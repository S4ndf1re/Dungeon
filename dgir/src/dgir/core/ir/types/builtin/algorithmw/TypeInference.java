package dgir.core.ir.types.builtin.algorithmw;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeInferenceSolver;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.builtin.algorithmw.expr.Ann;
import dgir.core.ir.types.builtin.algorithmw.expr.App;
import dgir.core.ir.types.builtin.algorithmw.expr.Abs;
import dgir.core.ir.types.builtin.algorithmw.expr.LetRec;
import dgir.core.ir.types.builtin.algorithmw.expr.Lit;
import dgir.core.ir.types.builtin.algorithmw.expr.Seq;
import dgir.core.ir.types.builtin.algorithmw.expr.Var;
import dgir.core.ir.types.builtin.algorithmw.expr.Block;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;

public final class TypeInference
    extends TypeInferenceSolver<TypeInference, Expr, AlgorithmWType> {

  private int currentLevel;

  public TypeInference() {
    this(new TypeDialectConverterRegistry());
    this.currentLevel = 0;
  }

  public TypeInference(TypeDialectConverterRegistry registry) {
    super(registry);
  }

  @Override
  public Pair<AlgorithmWType, Optional<ConversionContext<Expr, AlgorithmWType>>> generalNominalTypeToInferenceType(
      GeneralParameterizedNominalType type,
      Optional<ConversionContext<Expr, AlgorithmWType>> data) {
    List<AlgorithmWType> paramTypes = type.getTypedParameters().stream().map(param -> switch (param) {
      case GeneralTypeParameter.Concrete con -> this.generalNominalTypeToInferenceType(con.ty(), data).getLeft();
      case GeneralTypeParameter.Unknown unk -> new AlgorithmWType.Var(new TypeVar<>());
      case GeneralTypeParameter.Numeric num -> new AlgorithmWType.NumericType(num.number());
    }).toList();

    return Pair.of(new AlgorithmWType.LitType(type.getIdent(), paramTypes), null);
  }

  @Override
  public Expr Let(List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings, Expr body) {
    return new LetRec(bindings, body);
  }

  @Override
  public Expr Var(Symbol<Expr, AlgorithmWType> symbol) {
    return new Var(symbol);
  }

  @Override
  public Expr Seq(List<Expr> exprs) {
    return new Seq(exprs);
  }

  @Override
  public Expr Lit(Literal lit) {
    return new Lit(lit);
  }

  @Override
  public Expr App(Expr func, List<Expr> args) {
    return new App(func, args);
  }

  @Override
  public Expr Abs(List<Symbol<Expr, AlgorithmWType>> params, Expr body) {
    return new Abs(params, body);
  }

  @Override
  public Expr Ann(Expr body, AlgorithmWType type) {
    return new Ann(body, type);
  }

  @Override
  public Expr Block(Expr body) {
    return new Block(body);
  }

  @Override
  public SolveResult<Expr, AlgorithmWType> solve(ExprOrOperator<Expr, AlgorithmWType> exprOrOp) {
    Env env = new Env();
    Expr expr = this.asExpression(exprOrOp);
    InferResult res = this.infer(expr, env);
    var finalType = res.type();

    var instantiated = expr.instantiate(this, new InstEnv<>(expr), Subst.newEmpty());

    instantiated = this.postSolve(instantiated);

    return new SolveResult<>(finalType, expr, instantiated);
  }

  /**
   * First infer, the `exprOrOp`. If `exprOrOp` is of type Operator, first convert
   * to an {@link Expr} using the conversion untilities and buffer the converted
   * result.
   *
   * <p>
   * After inferring either the {@link Expr} or the {@Link Operation}, store the
   * inferred result in combination with the already inferred {@link Expr}
   */
  public InferResult infer(Expr expr, Env env) {
    this.currentLevel += 1;
    InferResult res = expr.infer(this, env);
    this.currentLevel -= 1;
    expr.setInferredType(Optional.ofNullable(res.type()));
    return res;
  }

  /**
   * Unifies two types: makes them equal by assigning and merging type
   * variables. A variable on either side is bound to the other side (the right
   * one first); otherwise both sides must have the same shape, and their parts
   * are unified pairwise.
   *
   * @throws TypingException.UnificationFailed if the shapes differ
   */
  public void unify(AlgorithmWType left, AlgorithmWType right) {
    if (right instanceof AlgorithmWType.Var rightVar) {
      this.unifyVar(rightVar, left);
      return;
    }

    switch (left) {
      case AlgorithmWType.Var leftVar -> this.unifyVar(leftVar, right);
      case AlgorithmWType.Arrow a -> {
        if (!(right instanceof AlgorithmWType.Arrow b)) {
          throw new TypingException.UnificationFailed(left, right);
        }
        this.unify(a.from, b.from);
        this.unify(a.to, b.to);
      }
      case AlgorithmWType.LitType a -> {
        if (!(right instanceof AlgorithmWType.LitType b && b.tyName.equals(a.tyName))) {
          throw new TypingException.UnificationFailed(left, right);
        }
        if (a.parameters.size() != b.parameters.size()) {
          throw new RuntimeException(
              "Parameter count mismatch: " + a.parameters.size() + " vs " + b.parameters.size());
        }
        for (int i = 0; i < a.parameters.size(); i++) {
          this.unify(a.parameters.get(i), b.parameters.get(i));
        }
      }
      case AlgorithmWType.NumericType a -> {
        if (!(right instanceof AlgorithmWType.NumericType b && a.size == b.size)) {
          throw new TypingException.UnificationFailed(left, right);
        }
      }
      case AlgorithmWType.Tuple a -> {
        if (!(right instanceof AlgorithmWType.Tuple b)) {
          throw new TypingException.UnificationFailed(left, right);
        }
        if (a.elements.size() != b.elements.size()) {
          throw new TypingException.TupleSizeMismatch(a, b, a.elements.size(), b.elements.size());
        }
        for (int i = 0; i < a.elements.size(); i++) {
          this.unify(a.elements.get(i), b.elements.get(i));
        }
      }
    }
  }

  /**
   * Binds a type variable to another type, or merges it with another variable.
   * A variable that already has a type unifies that type instead.
   */
  private void unifyVar(AlgorithmWType.Var var, AlgorithmWType other) {
    if (other instanceof AlgorithmWType.Var otherVar) {
      // Two variables: merge their classes. If both already had types, those
      // types must agree as well.
      var assigned = var.tyVar.unify(otherVar.tyVar);
      if (assigned.isPresent()) {
        this.unify(assigned.get().getLeft(), assigned.get().getRight());
      }
      return;
    }

    if (other.occursCheck(var.tyVar)) {
      throw new TypingException.OccursCheckFailed(other, var.tyVar);
    }

    if (var.tyVar.getAssigendType().isPresent()) {
      this.unify(var.tyVar.getAssigendType().get(), other);
      return;
    }

    other.occursCheckAjustLevel(var.tyVar);
    var.tyVar.assignType(other);
  }

  @Override
  public AlgorithmWType Arrow(AlgorithmWType from, AlgorithmWType to) {
    return new AlgorithmWType.Arrow(from, to);
  }

  public int getCurrentLevel() {
    return this.currentLevel;
  }
}

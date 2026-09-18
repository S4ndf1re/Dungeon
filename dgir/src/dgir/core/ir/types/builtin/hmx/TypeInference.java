package dgir.core.ir.types.builtin.hmx;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Value;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.InferenceTree;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeInferenceSolver;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.builtin.hmx.traits.OneShotConstraint;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;
import dgir.core.ir.types.traits.IAbstraction;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.traits.ISymbol;

public final class TypeInference
    extends TypeInferenceSolver<TypeInference, HMXExpr, HMXType> {

  private int currentLevel;

  public TypeInference() {
    this(new TypeDialectConverterRegistry());
    this.currentLevel = 0;
  }

  public TypeInference(TypeDialectConverterRegistry registry) {
    super(registry);
  }

  @Override
  public Pair<HMXType, Optional<ConversionContext<HMXExpr, HMXType>>> generalNominalTypeToInferenceType(
      GeneralParameterizedNominalType type,
      Optional<ConversionContext<HMXExpr, HMXType>> data) {
    List<HMXType> paramTypes = type.getTypedParameters().stream().map(param -> switch (param) {
      case GeneralTypeParameter.Concrete con -> this.generalNominalTypeToInferenceType(con.ty(), data).getLeft();
      case GeneralTypeParameter.Unknown unk -> new HMXType.Var(new TypeVar<>());
      case GeneralTypeParameter.Numeric num -> new HMXType.NumericType(num.number());
    }).toList();

    return Pair.of(new HMXType.LitType(type.getIdent(), paramTypes), null);
  }

  @Override
  public HMXExpr generalBlockToInferenceExpr(GeneralBlock block) {
    ArrayList<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings = new ArrayList<>();
    Optional<Symbol<HMXExpr, HMXType>> lastValue = Optional.empty();

    for (var op : block.getOperations()) {
      var opOutput = op.getOutput();
      if (opOutput.isPresent()) {
        var sym = Symbol.<HMXExpr, HMXType>of(opOutput.get().getValue());
        var expr = this.asExpression(ExprOrOperator.of(op));
        if (expr.containsSymbol(sym)) {
          throw new TypingException.CyclicSymbolAssignment(sym, expr);
        }
        bindings.add(Pair.of(sym, expr));
        lastValue = Optional.of(sym);
      } else {
        /*
         * NOTE: handle everything as a returnable value, even though something like a
         * function is not actually a expression! This is done to correctly typecheck
         * each function and their parameters!
         */
        Symbol<HMXExpr, HMXType> sym = null;
        if (op.asOp() instanceof ISymbol isym) {
          sym = Symbol.<HMXExpr, HMXType>of(isym.getSymbol());
        } else {
          var val = new Value();
          sym = Symbol.<HMXExpr, HMXType>of(val);
        }
        var expr = this.asExpression(ExprOrOperator.of(op));
        if (expr.containsSymbol(sym)) {
          throw new TypingException.CyclicSymbolAssignment(sym, expr);
        }
        bindings.add(Pair.of(sym, expr));
        lastValue = Optional.of(sym);
      }
    }

    if (lastValue.isPresent()) {
      return new HMXExpr.ExprLetRec(bindings,
          new HMXExpr.ExprSeq(bindings.stream().filter(bnd -> !(bnd.getRight() instanceof IAbstraction))
              .map(bnd -> (HMXExpr) new HMXExpr.ExprVar(bnd.getLeft())).toList()));
    } else {
      return new HMXExpr.ExprLetRec(bindings, new HMXExpr.ExprLit(new Literal.Unit()));
    }
  }

  @Override
  public SolveResult<HMXExpr, HMXType> solve(ExprOrOperator<HMXExpr, HMXType> exprOrOp) {
    Env env = new Env();
    HMXExpr expr = this.asExpression(exprOrOp);
    var resultType = new HMXType.Var(new TypeVar<HMXType>());
    GenerateResult res = this.generate(expr, env, resultType);

    // Since the result type var is actually not set for the engines level, this has
    // to be performed by a exists constraint on the first and outermost level!
    this.solveConstraint(new Constraint.Exists(res.constr(), resultType.tyVar), env);

    var instantiated = expr.instantiate(this, new InstEnv<>(expr), Subst.newEmpty());

    instantiated = this.postSolve(instantiated);

    return new SolveResult<>(resultType.deref(), expr, instantiated);
  }

  /**
   * First infer, the `exprOrOp`. If `exprOrOp` is of type Operator, first convert
   * to an {@link HMXExpr} using the conversion untilities and buffer the
   * converted
   * result.
   *
   * <p>
   * After inferring either the {@link HMXExpr} or the {@Link Operation}, store
   * the
   * inferred result in combination with the already inferred {@link HMXExpr}
   */
  public GenerateResult generate(HMXExpr expr, Env env, HMXType type) {
    this.currentLevel += 1;
    GenerateResult res = expr.generate(this, env, type);
    this.currentLevel -= 1;
    expr.setInferredType(Optional.ofNullable(type.deref()));
    return res;
  }

  public InferenceTree unify(HMXType left, HMXType right) {
    if (right instanceof HMXType.Var) {
      return right.unify(this, left);
    }
    return left.unify(this, right);
  }

  public int getCurrentLevel() {
    return this.currentLevel;
  }

  public void solveConstraint(Constraint constr, Env env) {
    if (constr instanceof OneShotConstraint oneShot && oneShot.isSolved()) {
      return;
    }

    this.currentLevel += 1;
    constr.solve(this, env);
    this.currentLevel -= 1;

    if (constr instanceof OneShotConstraint oneShotSolved) {
      oneShotSolved.markSolved();
    }
  }

  public Scheme solveConstraintAndGeneralize(Constraint constr, Env env, HMXType type) {
    this.solveConstraint(constr, env);

    var finalType = type.deref();
    return finalType.generalize(this.currentLevel, constr);
  }
}

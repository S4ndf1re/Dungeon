package dgir.core.ir.types.builtin.algorithmw;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;
import dgir.core.ir.types.InferenceTree;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeInferenceSolver;
import dgir.core.ir.types.TypeVar;
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
  public Expr newLetExpr(List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings, Expr body) {
    return new Expr.ExprLetRec(bindings, body);
  }

  @Override
  public Expr newVarExpr(Symbol<Expr, AlgorithmWType> symbol) {
    return new Expr.ExprVar(symbol);
  }

  @Override
  public Expr newSeqExpr(List<Expr> exprs) {
    return new Expr.ExprSeq(exprs);
  }

  @Override
  public Expr newLit(Literal lit) {
    return new Expr.ExprLit(lit);
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

  public InferenceTree unify(AlgorithmWType left, AlgorithmWType right) {
    if (right instanceof AlgorithmWType.Var) {
      return right.unify(this, left);
    }
    return left.unify(this, right);
  }

  public int getCurrentLevel() {
    return this.currentLevel;
  }
}

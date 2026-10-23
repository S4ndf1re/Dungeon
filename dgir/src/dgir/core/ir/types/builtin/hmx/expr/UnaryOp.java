package dgir.core.ir.types.builtin.hmx.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.Env;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Subst;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.builtin.hmx.Constraint.DetermineByCallbackAndUnify;
import dgir.core.ir.types.builtin.hmx.Constraint.DetermineByCallbackAndUnify.DetermineCallback;
import dgir.core.ir.types.InstEnv;
import dgir.dialect.arith.ArithAttrs.UnaryModeAttr.UnaryMode;

public final class UnaryOp extends HMXExpr {
  public final HMXExpr lhs;
  public final UnaryMode mode;

  public UnaryOp(HMXExpr lhs, UnaryMode mode) {
    this.lhs = lhs;
    this.mode = mode;
  }

  public UnaryOp(Location loc, HMXExpr lhs, UnaryMode mode) {
    super(loc);
    this.lhs = lhs;
    this.mode = mode;
  }

  public UnaryOp(UnaryOp other) {
    super(other);
    this.lhs = other.lhs;
    this.mode = other.mode;
  }

  public UnaryOp(UnaryOp other, HMXExpr lhs) {
    super(other);
    this.lhs = lhs;
    this.mode = other.mode;
  }

  @Override
  public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env,
      Subst solutionContext) {
    return new UnaryOp(this,
        this.lhs.instantiate(engine, env, solutionContext));
  }

  @Override
  public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
    var lhsTypeVar = new TypeVar<HMXType>();
    var lhsType = new HMXType.Var(this.getLocation(), lhsTypeVar);

    var resLhs = engine.generate(this.lhs, env, lhsType);

    DetermineCallback callback = types -> {
      var lhsTy = types[0];

      if (!lhsType.isFullySpecified()) {
        throw new DetermineByCallbackAndUnify.CallbackNotReady();
      }

      var lhsIrType = lhsTy.toIrType();

      var resultType = this.mode.getExpectedResultTypeForParams(lhsIrType);
      return engine
          .generalNominalTypeToInferenceType(resultType.asParameterizedNominalTypeParameter().getConcrete(), null)
          .getLeft();
    };

    return new GenerateResult(new Constraint.Exists(List.of(lhsTypeVar ), new Constraint.And(
        resLhs.constr(),
        new Constraint.Sub(lhsType, new HMXType.LitType(this.getLocation(), TypeIdent.TYPE_IDENT_NUMBER)),
        new Constraint.DetermineByCallbackAndUnify(callback, type, lhsType))));
  }

  @Override
  public List<HMXExpr> getChildren() {
    return List.of(this.lhs);
  }

  @Override
  public void replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
    this.lhs.replaceSymbol(original, replacement);
  }


  @Override
  public boolean equals(Object obj) {
    return obj instanceof UnaryOp other && this.lhs.equals(other.lhs)
        && this.mode.equals(other.mode)
        && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.lhs, this.mode, super.hashCode());
  }

  @Override
  public HMXExpr copy() {
    return new UnaryOp(this);
  }

  @Override
  public String toString() {
    return this.mode + " " + this.lhs;
  }
}

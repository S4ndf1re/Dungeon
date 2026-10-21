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
import dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;

public final class BinOp extends HMXExpr {
  public final HMXExpr lhs;
  public final HMXExpr rhs;
  public final BinMode mode;

  public BinOp(HMXExpr lhs, HMXExpr rhs, BinMode mode) {
    this.lhs = lhs;
    this.rhs = rhs;
    this.mode = mode;
  }

  public BinOp(Location loc, HMXExpr lhs, HMXExpr rhs, BinMode mode) {
    super(loc);
    this.lhs = lhs;
    this.rhs = rhs;
    this.mode = mode;
  }

  public BinOp(BinOp other) {
    super(other);
    this.lhs = other.lhs;
    this.rhs = other.rhs;
    this.mode = other.mode;
  }

  public BinOp(BinOp other, HMXExpr lhs, HMXExpr rhs) {
    super(other);
    this.lhs = lhs;
    this.rhs = rhs;
    this.mode = other.mode;
  }

  @Override
  public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env,
      Subst solutionContext) {
    return new BinOp(this,
        this.lhs.instantiate(engine, env, solutionContext),
        this.rhs.instantiate(engine, env, solutionContext));
  }

  @Override
  public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
    var lhsTypeVar = new TypeVar<HMXType>();
    var rhsTypeVar = new TypeVar<HMXType>();
    var lhsType = new HMXType.Var(lhsTypeVar);
    var rhsType = new HMXType.Var(rhsTypeVar);

    var resLhs = engine.generate(this.lhs, env, lhsType);
    var resRhs = engine.generate(this.rhs, env, rhsType);

    DetermineCallback callback = types -> {
      var lhsTy = types[0];
      var rhsTy = types[1];

      if (!lhsTy.isFullySpecified()) {
        throw new DetermineByCallbackAndUnify.CallbackNotReady();
      }

      if (!rhsTy.isFullySpecified()) {
        throw new DetermineByCallbackAndUnify.CallbackNotReady();
      }

      var lhsIrType = lhsTy.toIrType();
      var rhsIrType = rhsTy.toIrType();

      var resultType = this.mode.getExpectedResultTypeForParams(lhsIrType, rhsIrType);
      return engine
          .generalNominalTypeToInferenceType(resultType.asParameterizedNominalTypeParameter().getConcrete(), null)
          .getLeft();
    };

    return new GenerateResult(new Constraint.Exists(List.of(lhsTypeVar, rhsTypeVar), new Constraint.And(
        resLhs.constr(), resRhs.constr(),
        new Constraint.Sub(lhsType, new HMXType.LitType(TypeIdent.TYPE_IDENT_NUMBER)),
        new Constraint.Sub(rhsType, new HMXType.LitType(TypeIdent.TYPE_IDENT_NUMBER)),
        new Constraint.DetermineByCallbackAndUnify(callback, type, lhsType, rhsType))));
  }

  @Override
  public List<HMXExpr> getChildren() {
    return List.of(this.lhs, this.rhs);
  }

  @Override
  public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
    return new BinOp(this, this.lhs.replaceSymbol(original, replacement),
        this.rhs.replaceSymbol(original, replacement));
  }

  @Override
  public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
    return this.lhs.containsSymbol(symbol) || this.rhs.containsSymbol(symbol);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof BinOp other && this.lhs.equals(other.lhs) && this.rhs.equals(other.rhs)
        && this.mode.equals(other.mode)
        && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.lhs, this.rhs, this.mode, super.hashCode());
  }

  @Override
  public HMXExpr copy() {
    return new BinOp(this);
  }
}

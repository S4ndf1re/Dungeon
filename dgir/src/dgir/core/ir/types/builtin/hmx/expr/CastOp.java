package dgir.core.ir.types.builtin.hmx.expr;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dgir.core.debug.Location;
import dgir.core.ir.Type;
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
import dgir.core.ir.types.InstEnv;

public final class CastOp extends HMXExpr {
  public final HMXExpr operand;
  public final Type targetType;

  public CastOp(HMXExpr operand, Type targetType) {
    this.operand = operand;
    this.targetType = targetType;
  }

  public CastOp(Location loc, HMXExpr operand, Type targetType) {
    super(loc);
    this.operand = operand;
    this.targetType = targetType;
  }

  public CastOp(CastOp other) {
    super(other);
    this.operand = other.operand;
    this.targetType = other.targetType;
  }

  public CastOp(CastOp other, HMXExpr operand) {
    super(other);
    this.operand = operand;
    this.targetType = other.targetType;
  }

  @Override
  public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env,
      Subst solutionContext) {
    return new CastOp(this,
        this.operand.instantiate(engine, env, solutionContext));
  }

  @Override
  public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
    var operandTypeVar = new TypeVar<HMXType>();
    var operandType = new HMXType.Var(operandTypeVar);
    var operandRes = engine.generate(this.operand, env, operandType);

    var resultIrType = this.targetType;

    var resultType = engine
        .generalNominalTypeToInferenceType(resultIrType.asParameterizedNominalType(), Optional.empty())
        .getLeft();

    return new GenerateResult(new Constraint.Exists(List.of(operandTypeVar),
        new Constraint.And(operandRes.constr(),
            new Constraint.Sub(operandType, new HMXType.LitType(TypeIdent.TYPE_IDENT_NUMBER)),
            new Constraint.Sub(resultType, new HMXType.LitType(TypeIdent.TYPE_IDENT_NUMBER)),
            new Constraint.Equal(type, resultType))));
  }

  @Override
  public List<HMXExpr> getChildren() {
    return List.of(this.operand);
  }

  @Override
  public void replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
    this.operand.replaceSymbol(original, replacement);
  }

  @Override
  public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
    return this.operand.containsSymbol(symbol);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof CastOp other && this.operand.equals(other.operand)
        && this.targetType.equals(other.targetType)
        && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.operand, this.targetType, super.hashCode());
  }

  @Override
  public HMXExpr copy() {
    return new CastOp(this);
  }
}

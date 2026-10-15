package dgir.core.ir.types.builtin.systemf.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dgir.core.debug.Location;
import dgir.core.ir.Type;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.builtin.systemf.TypeResult;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.dialect.builtin.BuiltinTypes;

import static dgir.dialect.builtin.BuiltinTypes.isNumeric;

public final class CastOp extends Expr {
  public final Expr operand;
  public final Type targetType;

  public CastOp(Expr operand, Type targetType) {
    this.operand = operand;
    this.targetType = targetType;
  }

  public CastOp(Location loc, Expr operand, Type targetType) {
    super(loc);
    this.operand = operand;
    this.targetType = targetType;
  }

  public CastOp(CastOp other) {
    super(other);
    this.operand = other.operand;
    this.targetType = other.targetType;
  }

  public CastOp(CastOp other, Expr operand) {
    super(other);
    this.operand = operand;
    this.targetType = other.targetType;
  }

  @Override
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
      Context solutionContext) {
    return new CastOp(this,
        this.operand.instantiate(engine, env, solutionContext));
  }

  @Override
  public TypeResult infer(TypeInference engine, Context ctx) {
    TypeResult resValue = engine.infer(ctx, this.operand);
    var valueType = resValue.type().deref();

    var integerDescriptors = BuiltinTypes.BuiltinTypeDescriptor.IntegerDescriptor.getDescriptors();
    var floatDescriptors = new ArrayList<>(BuiltinTypes.BuiltinTypeDescriptor.FloatDescriptor.getDescriptors());
    floatDescriptors.addAll(integerDescriptors);

    Optional<TypingException> firstError = Optional.empty();
    for (var desc : floatDescriptors) {
      try {
        engine.subtype(ctx, valueType,
            new SystemFType.Lit(this.getLocation(), TypeIdent.from(desc.getIdent())));
        firstError = Optional.empty();
        break;
      } catch (TypingException e) {
        firstError = firstError.or(() -> Optional.of(e));
      }
    }
    if (firstError.isPresent()) {
      throw firstError.get();
    }

    valueType = valueType.deref();
    valueType = ctx.apply(valueType);

    var valueIrType = valueType.toIrType();
    var resultIrType = this.targetType;

    assert isNumeric(valueIrType);
    assert isNumeric(resultIrType);

    var resultType = engine
        .generalNominalTypeToInferenceType(resultIrType.asParameterizedNominalType(), Optional.empty())
        .getLeft();

    return new TypeResult(resultType, ctx);
  }

  @Override
  public List<Expr> getChildren() {
    return List.of(this.operand);
  }

  @Override
  public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
    return new CastOp(this, this.operand.replaceSymbol(original, replacement));
  }

  @Override
  public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
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
  public Expr copy() {
    return new CastOp(this);
  }
}

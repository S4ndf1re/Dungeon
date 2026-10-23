package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dgir.core.debug.Location;
import dgir.core.ir.Type;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Env;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.builtin.algorithmw.InferResult;
import dgir.core.ir.types.builtin.algorithmw.Subst;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;
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
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env,
      Subst solutionContext) {
    return new CastOp(this,
        this.operand.instantiate(engine, env, solutionContext));
  }

  @Override
  public InferResult infer(TypeInference engine, Env env) {
    InferResult resValue = engine.infer(this.operand, env);
    var valueType = resValue.type().deref();

    var integerDescriptors = BuiltinTypes.BuiltinTypeDescriptor.IntegerDescriptor.getDescriptors();
    var floatDescriptors = new ArrayList<>(BuiltinTypes.BuiltinTypeDescriptor.FloatDescriptor.getDescriptors());
    floatDescriptors.addAll(integerDescriptors);

    Optional<TypingException> firstError = Optional.empty();
    for (var desc : floatDescriptors) {
      try {
        engine.unify(valueType,
            new AlgorithmWType.LitType(this.getLocation(), TypeIdent.from(desc.getIdent())));
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

    var valueIrType = valueType.toIrType();
    var resultIrType = this.targetType;

    assert isNumeric(valueIrType);
    assert isNumeric(resultIrType);

    var resultType = engine
        .generalNominalTypeToInferenceType(resultIrType.asParameterizedNominalType(), Optional.empty())
        .getLeft();

    return new InferResult(resultType);
  }

  @Override
  public List<Expr> getChildren() {
    return List.of(this.operand);
  }

  @Override
  public void replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
    this.operand.replaceSymbol(original, replacement);
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

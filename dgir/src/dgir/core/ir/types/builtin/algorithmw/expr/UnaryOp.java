package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dgir.core.debug.Location;
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
import dgir.dialect.arith.ArithAttrs.UnaryModeAttr.UnaryMode;
import dgir.dialect.builtin.BuiltinTypes;

public final class UnaryOp extends Expr {
  public final Expr operand;
  public final UnaryMode mode;

  public UnaryOp(Expr operand, UnaryMode mode) {
    this.operand = operand;
    this.mode = mode;
  }

  public UnaryOp(Location loc, Expr operand, UnaryMode mode) {
    super(loc);
    this.operand = operand;
    this.mode = mode;
  }

  public UnaryOp(UnaryOp other) {
    super(other);
    this.operand = other.operand;
    this.mode = other.mode;
  }

  public UnaryOp(UnaryOp other, Expr operand) {
    super(other);
    this.operand = operand;
    this.mode = other.mode;
  }

  @Override
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env,
      Subst solutionContext) {
    return new UnaryOp(this,
        this.operand.instantiate(engine, env, solutionContext));
  }

  @Override
  public InferResult infer(TypeInference engine, Env env) {
    InferResult resOperand = engine.infer(this.operand, env);
    var operandType = resOperand.type().deref();

    var integerDescriptors = BuiltinTypes.BuiltinTypeDescriptor.IntegerDescriptor.getDescriptors();
    var floatDescriptors = new ArrayList<>(BuiltinTypes.BuiltinTypeDescriptor.FloatDescriptor.getDescriptors());
    floatDescriptors.addAll(integerDescriptors);

    Optional<TypingException> firstError = Optional.empty();
    for (var desc : floatDescriptors) {
      try {
        engine.unify(operandType,
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

    operandType = operandType.deref();

    var operandIrType = operandType.toIrType();

    var resultIrType = this.mode.getExpectedResultTypeForParams(operandIrType);

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
  public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
    return new UnaryOp(this, this.operand.replaceSymbol(original, replacement));
  }

  @Override
  public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
    return this.operand.containsSymbol(symbol);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof UnaryOp other && this.operand.equals(other.operand) && this.mode.equals(other.mode)
        && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.operand, this.mode, super.hashCode());
  }

  @Override
  public Expr copy() {
    return new UnaryOp(this);
  }
}

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
import dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;
import dgir.dialect.builtin.BuiltinTypes;

public final class BinOp extends Expr {
  public final Expr lhs;
  public final Expr rhs;
  public final BinMode mode;

  public BinOp(Expr lhs, Expr rhs, BinMode mode) {
    this.lhs = lhs;
    this.rhs = rhs;
    this.mode = mode;
  }

  public BinOp(Location loc, Expr lhs, Expr rhs, BinMode mode) {
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

  public BinOp(BinOp other, Expr lhs, Expr rhs) {
    super(other);
    this.lhs = lhs;
    this.rhs = rhs;
    this.mode = other.mode;
  }

  @Override
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env,
      Subst solutionContext) {
    return new BinOp(this,
        this.lhs.instantiate(engine, env, solutionContext),
        this.rhs.instantiate(engine, env, solutionContext));
  }

  @Override
  public InferResult infer(TypeInference engine, Env env) {
    InferResult resLhs = engine.infer(this.lhs, env);
    InferResult resRhs = engine.infer(this.rhs, env);
    var lhsType = resLhs.type().deref();
    var rhsType = resRhs.type().deref();

    var integerDescriptors = BuiltinTypes.BuiltinTypeDescriptor.IntegerDescriptor.getDescriptors();
    var floatDescriptors = new ArrayList<>(BuiltinTypes.BuiltinTypeDescriptor.FloatDescriptor.getDescriptors());
    floatDescriptors.addAll(integerDescriptors);

    Optional<TypingException> firstError = Optional.empty();
    for (var desc : floatDescriptors) {
      try {
        engine.unify(lhsType,
            new AlgorithmWType.LitType(this.getLocation(), TypeIdent.from(desc.getIdent())));
        firstError = Optional.empty();
        break;
      } catch (TypingException.UnificationFailed e) {
        firstError = firstError.or(() -> Optional.of(e));
      }
    }
    if (firstError.isPresent()) {
      throw firstError.get();
    }

    lhsType = lhsType.deref();

    for (var desc : floatDescriptors) {
      try {
        engine.unify(rhsType,
            new AlgorithmWType.LitType(this.getLocation(), TypeIdent.from(desc.getIdent())));
        firstError = Optional.empty();
        break;
      } catch (TypingException.UnificationFailed e) {
        firstError = firstError.or(() -> Optional.of(e));
      }
    }
    if (firstError.isPresent()) {
      throw firstError.get();
    }

    rhsType = rhsType.deref();

    var lhsIrType = lhsType.toIrType();
    var rhsIrType = rhsType.toIrType();

    var resultIrType = this.mode.getExpectedResultTypeForParams(lhsIrType, rhsIrType);

    var resultType = engine
        .generalNominalTypeToInferenceType(resultIrType.getAsKnownOrThrow().asParameterizedNominalType(),
            Optional.empty())
        .getLeft();

    return new InferResult(resultType);
  }

  @Override
  public List<Expr> getChildren() {
    return List.of(this.lhs, this.rhs);
  }

  @Override
  public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
    return new BinOp(this, this.lhs.replaceSymbol(original, replacement),
        this.rhs.replaceSymbol(original, replacement));
  }

  @Override
  public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
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
  public Expr copy() {
    return new BinOp(this);
  }
}

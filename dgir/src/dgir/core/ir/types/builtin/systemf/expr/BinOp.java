package dgir.core.ir.types.builtin.systemf.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dgir.core.debug.Location;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.TypeResult;
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
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
      Context solutionContext) {
    return new BinOp(this,
        this.lhs.instantiate(engine, env, solutionContext),
        this.rhs.instantiate(engine, env, solutionContext));
  }

  @Override
  public TypeResult infer(TypeInference engine, Context ctx) {
    TypeResult resLhs = engine.infer(ctx, this.lhs);
    TypeResult resRhs = engine.infer(ctx, this.rhs);
    var lhsType = resLhs.type().deref();
    var rhsType = resRhs.type().deref();

    var integerDescriptors = BuiltinTypes.BuiltinTypeDescriptor.IntegerDescriptor.getDescriptors();
    var floatDescriptors = new ArrayList<>(BuiltinTypes.BuiltinTypeDescriptor.FloatDescriptor.getDescriptors());
    floatDescriptors.addAll(integerDescriptors);

    // TODO: this can be implemented properly, once the subtype method is adjusted!
    // For the current fallback, the aglgorithm W Logic is used instead
    Optional<TypingException> firstError = Optional.empty();
    for (var desc : floatDescriptors) {
      try {
        engine.subtype(ctx, lhsType,
            new SystemFType.Lit(this.getLocation(), TypeIdent.from(desc.getIdent())));
        firstError = Optional.empty();
        break;
      } catch (TypingException.SubtypingFailed e) {
        firstError = firstError.or(() -> Optional.of(e));
      }
    }
    if (firstError.isPresent()) {
      throw firstError.get();
    }

    lhsType = lhsType.deref();
    lhsType = ctx.apply(lhsType);

    for (var desc : floatDescriptors) {
      try {
        engine.subtype(ctx, rhsType,
            new SystemFType.Lit(this.getLocation(), TypeIdent.from(desc.getIdent())));
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
    rhsType = ctx.apply(rhsType);

    var lhsIrType = lhsType.toIrType();
    var rhsIrType = rhsType.toIrType();

    var resultIrType = this.mode.getExpectedResultTypeForParams(lhsIrType, rhsIrType);

    var resultType = engine
        .generalNominalTypeToInferenceType(resultIrType.getAsKnownOrThrow().asParameterizedNominalType(),
            Optional.empty())
        .getLeft();

    return new TypeResult(resultType, ctx);
  }

  @Override
  public List<Expr> getChildren() {
    return List.of(this.lhs, this.rhs);
  }

  @Override
  public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
    return new BinOp(this, this.lhs.replaceSymbol(original, replacement),
        this.rhs.replaceSymbol(original, replacement));
  }

  @Override
  public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
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

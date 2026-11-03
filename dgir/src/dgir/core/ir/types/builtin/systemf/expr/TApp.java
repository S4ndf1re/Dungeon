package dgir.core.ir.types.builtin.systemf.expr;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Entry;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.TypeResult;

/**
 * A single type application, instantiating the outermost {@link SystemFType.ForAll}
 * of {@link #func}.
 *
 * <p>
 * The type argument is either annotated, or a hole that is inferred: a hole
 * instantiates the bound variable with a fresh existential, which is solved by
 * the value arguments the instantiated function is applied to afterwards.
 * Chaining type applications mixes both freely, e.g. {@code f [int32] [_]}.
 */
public final class TApp extends Expr {

    private final Expr func;
    private final Optional<SystemFType> type;
    // The existential standing in for the type argument of a hole
    private final TypeVar<SystemFType> hole;

    public TApp(Expr func, SystemFType type) {
      this(Location.UNKNOWN, func, Optional.of(type));
    }

    public TApp(Location loc, Expr func, SystemFType type) {
      this(loc, func, Optional.of(type));
    }

    /**
     * Create a type application with a hole, inferring the type argument.
     */
    public TApp(Location loc, Expr func) {
      this(loc, func, Optional.empty());
    }

    public TApp(Location loc, Expr func, Optional<SystemFType> type) {
      super(loc);
      this.func = func;
      this.type = type;
      this.hole = new TypeVar<>();
    }

    public TApp(TApp other) {
      super(other);
      this.func = other.func;
      this.type = other.type;
      this.hole = other.hole;
    }

    public TApp(TApp other, Expr func, Optional<SystemFType> type) {
      super(other);
      this.func = func;
      this.type = type;
      this.hole = other.hole;
    }

    @Override
    public final String toString() {
      return func + " [" + type.map(Object::toString).orElse("_") + "]";
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var funcInferred = engine.infer(ctx, this.func);
      if (funcInferred.ctx().apply(funcInferred.type()) instanceof SystemFType.ForAll forall) {
        var newCtx = funcInferred.ctx().copy();

        SystemFType typeArg;
        if (this.type.isPresent()) {
          typeArg = this.type.get();
        } else {
          newCtx.push(new Entry.ETVarBnd(this.hole));
          typeArg = new SystemFType.EtVar(this.getLocation(), this.hole);
        }

        var resultType = engine.substType(
            forall.boundVar,
            typeArg,
            forall.body);
        return new TypeResult(resultType, newCtx);
      } else {
        throw new TypingException.ExpectedForAllType(this.getLocation());
      }
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(this.func);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof TApp other && this.func.equals(other.func) && this.type.equals(other.type)
          && this.hole.equals(other.hole) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.func, this.type, this.hole, super.hashCode());
    }

    @Override
    public Expr copy() {
      return new TApp(this);
    }

    @Override
    public void replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      this.func.replaceSymbol(original, replacement);
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      assert this.func.getInferredType().isPresent();
      assert this.func.getInferredType().get() instanceof SystemFType.ForAll;

      var forAll = (SystemFType.ForAll) this.func.getInferredType().get();

      // A hole takes the type its existential was solved to by the value
      // arguments
      var typeArg = this.type.orElseGet(
          () -> solution.apply(new SystemFType.EtVar(this.getLocation(), this.hole)));
      assert typeArg.isFullySpecified() : "the type argument of " + this + " could not be inferred";

      var newCtx = solution.copy();
      newCtx.push(new Entry.SVarBnd(forAll.boundVar, typeArg));

      return this.func.instantiate(engine, env, newCtx);
    }
  }

package dgir.core.ir.types.builtin.systemf.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Entry;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.TypeResult;

public final class TApp extends Expr {

    private final Expr func;
    private final SystemFType type;

    public TApp(Expr func, SystemFType type) {
      this.func = func;
      this.type = type;
    }

    public TApp(Location loc, Expr func, SystemFType type) {
      super(loc);
      this.func = func;
      this.type = type;
    }

    public TApp(TApp other) {
      super(other);
      this.func = other.func;
      this.type = other.type;
    }

    public TApp(TApp other, Expr func, SystemFType type) {
      super(other);
      this.func = func;
      this.type = type;
    }

    @Override
    public final String toString() {
      return func + " " + type;
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var funcInferred = engine.infer(ctx, this.func);
      if (funcInferred.type() instanceof SystemFType.ForAll forall) {
        var resultType = engine.substType(
            forall.boundVar,
            this.type,
            forall.body);
        return new TypeResult(resultType, funcInferred.ctx());
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
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.func, this.type, super.hashCode());
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

      var newCtx = solution.copy();
      newCtx.push(new Entry.SVarBnd(forAll.boundVar, this.type));

      return this.func.instantiate(engine, env, newCtx);
    }
  }

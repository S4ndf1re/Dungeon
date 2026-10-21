package dgir.core.ir.types.builtin.systemf.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.TypeResult;

public final class Return extends Expr {

    private Expr value;

    public Return(Expr value) {
      this.value = value;
    }

    public Return(Location loc, Expr value) {
      super(loc);
      this.value = value;
    }

    public Return(Return other) {
      super(other);
      this.value = other.value;
    }

    public Return(Return other, Expr value) {
      super(other);
      this.value = value;
    }

    public Expr value() {
      return this.value.unwrapOrThis();
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      TypeResult res = engine.infer(ctx, this.value);
      SystemFType resultType = res.ctx().apply(res.type());

      return new TypeResult(resultType, res.ctx());
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(this.value);
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return this.value.containsSymbol(symbol);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Return other && this.value.equals(other.value) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.value, super.hashCode());
    }

    @Override
    public void replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      this.value.replaceSymbol(original, replacement);
    }

    @Override
    public Expr copy() {
      return new Return(this);
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      return new Return(this, this.value.instantiate(engine, env, solution));
    }
  }

package dgir.core.ir.types.builtin.systemf.expr;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Entry;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.TypeResult;
import dgir.core.ir.types.builtin.systemf.TypeInference;

public final class Untyped extends Expr {
  private Optional<SystemFType> type;

  public Untyped(Location loc) {
    super(loc);
    this.type = Optional.empty();
  }

  private Untyped(Untyped other) {
    super(other);
    this.type = other.type;
  }

  @Override
  public final String toString() {
    return "" + type.orElse(null);
  }

  @Override
  public List<Expr> getChildren() {
    return List.of();
  }

  @Override
  public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
    return false;
  }

  @Override
  public TypeResult infer(TypeInference engine, Context ctx) {
    if (this.type.isEmpty()) {
      var etvar = new TypeVar<SystemFType>();
      ctx = ctx.copy();
      ctx.push(new Entry.ETVarBnd(etvar));
      this.type = Optional.of(new SystemFType.EtVar(etvar));
    }
    return new TypeResult(this.type.get(), ctx);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Untyped other && this.type.equals(other.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.type, super.hashCode());
  }

  @Override
  public void replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
  }

  @Override
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env, Context solution) {
    return this;
  }

  @Override
  public Expr copy() {
    return new Untyped(this);
  }
}

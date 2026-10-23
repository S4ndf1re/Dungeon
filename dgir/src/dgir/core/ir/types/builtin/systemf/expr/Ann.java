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

public final class Ann extends Expr {

  private final Expr expr;
  private final SystemFType type;

  public Ann(Expr expr, SystemFType type) {
    this.expr = expr;
    this.type = type;
  }

  public Ann(Location loc, Expr expr, SystemFType type) {
    super(loc);
    this.expr = expr;
    this.type = type;
  }

  public Ann(Ann other) {
    super(other);
    this.expr = other.expr;
    this.type = other.type;
  }

  public Ann(Ann other, Expr expr, SystemFType type) {
    super(other);
    this.expr = expr;
    this.type = type;
  }

  @Override
  public boolean isA(Class<?> clazz) {
    return super.isA(clazz) || this.expr.isA(clazz);
  }

  @Override
  public final String toString() {
    return expr + " : " + type;
  }

  @Override
  public TypeResult infer(TypeInference engine, Context ctx) {
    var checked = engine.check(ctx, this.expr, this.type);

    return new TypeResult(this.type, checked.ctx());
  }

  @Override
  public List<Expr> getChildren() {
    return List.of(this.expr);
  }


  @Override
  public boolean equals(Object obj) {
    return obj instanceof Ann other && this.expr.equals(other.expr) && this.type.equals(other.type)
        && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.expr, this.type, super.hashCode());
  }

  @Override
  public void replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
    this.expr.replaceSymbol(original, replacement);
  }

  @Override
  public Expr copy() {
    return new Ann(this);
  }

  @Override
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
      Context solution) {
    // Simply return the inner as fully instantiated!
    return this.expr.instantiate(engine, env, solution);
  }
}

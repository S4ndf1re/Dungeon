package dgir.core.ir.types.builtin.systemf.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.TypeResult;

public final class Tuple extends Expr {

    private final List<Expr> elements;

    public Tuple(List<Expr> elements) {
      this.elements = List.copyOf(elements);
    }

    public Tuple(Location loc, List<Expr> elements) {
      super(loc);
      this.elements = List.copyOf(elements);
    }

    public Tuple(Expr... elements) {
      this.elements = List.of(elements);
    }

    public Tuple(Location loc, Expr... elements) {
      super(loc);
      this.elements = List.of(elements);
    }

    public Tuple(Tuple other) {
      super(other);
      this.elements = List.copyOf(other.elements);
    }

    public Tuple(Tuple other, List<Expr> elements) {
      super(other);
      this.elements = List.copyOf(elements);
    }

    public List<Expr> elements() {
      return this.elements.stream().map(Expr::unwrapOrThis).toList();
    }

    @Override
    public final String toString() {
      return "(" +
          this.elements.stream().map(Object::toString).collect(Collectors.joining(", ")) +
          ")";
    }

    @Override
    public List<Expr> getChildren() {
      return this.elements;
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return this.elements.stream().anyMatch(elem -> elem.containsSymbol(symbol));
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var currentCtx = ctx.copy();
      var types = new ArrayList<SystemFType>();

      for (var elem : this.elements) {
        var res = engine.infer(currentCtx, elem);
        currentCtx = res.ctx();
        types.add(currentCtx.apply(res.type()));
      }

      var resultType = new SystemFType.Tuple(this.getLocation(), List.copyOf(types));
      return new TypeResult(resultType, currentCtx);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Tuple other && this.elements.equals(other.elements) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.elements, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new Tuple(this,
          this.elements.stream().map(elem -> elem.replaceSymbol(original, replacement)).toList());
    }

    @Override
    public Expr copy() {
      return new Tuple(this);
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      return new Tuple(this, this.elements.stream().map(elem -> elem.instantiate(engine, env, solution)).toList());
    }
  }

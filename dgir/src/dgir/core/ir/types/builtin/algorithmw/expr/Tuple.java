package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Env;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.InferResult;
import dgir.core.ir.types.builtin.algorithmw.Subst;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;

public final class Tuple extends Expr {

    private final List<Expr> elements;

    public List<Expr> elements() {
      return this.elements.stream().map(Expr::unwrapOrThis).toList();
    }

    @Override
    public Expr copy() {
      return new Tuple(this);
    }

    public Tuple(List<Expr> elements) {
      this.elements = elements;
    }

    public Tuple(Location loc, List<Expr> elements) {
      super(loc);
      this.elements = elements;
    }

    public Tuple(Expr... elements) {
      ArrayList<Expr> elems = new ArrayList<>();
      for (var elem : elements) {
        elems.add(elem);
      }

      this.elements = List.copyOf(elems);
    }

    public Tuple(Location loc, Expr... elements) {
      super(loc);
      ArrayList<Expr> elems = new ArrayList<>();
      for (var elem : elements) {
        elems.add(elem);
      }

      this.elements = List.copyOf(elems);
    }

    public Tuple(Tuple other) {
      super(other);
      this.elements = List.copyOf(other.elements);
    }

    public Tuple(Tuple other, List<Expr> elements) {
      super(other);
      this.elements = List.copyOf(elements);
    }

    @Override
    public final String toString() {
      return ("(" +
          elements
              .stream()
              .map(Object::toString)
              .collect(Collectors.joining(", "))
          +
          ")");
    }

    @Override
    public List<Expr> getChildren() {
      return this.elements.stream().map(ExprOrOperator::getExpr).toList();
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.elements.stream().anyMatch(elem -> elem.containsSymbol(symbol));
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      ArrayList<AlgorithmWType> types = new ArrayList<>();
      Env currentEnv = env.copy();

      for (var expr : elements) {
        var res = engine.infer(expr, currentEnv);
        types.add(res.type());
      }

      var resultType = new AlgorithmWType.Tuple(this.getLocation(), List.copyOf(types));

      return new InferResult(resultType);
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
    public void replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      this.elements.forEach(elem -> elem.replaceSymbol(original, replacement));
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      return new Tuple(this, this.elements.stream().map(elem -> elem.instantiate(engine, env, solution)).toList());
    }
  }

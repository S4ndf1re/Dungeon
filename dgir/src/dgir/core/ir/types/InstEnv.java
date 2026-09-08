package dgir.core.ir.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.types.traits.IExpressionCell;

public final class InstEnv<E extends Expression<E, T>, T extends Type, S> extends HashConsing<E, T> {
  private Optional<InstEnv<E, T, S>> parentEnv;
  private E bindingExpression;
  private HashMap<Symbol<E, T>, Pair<E, Integer>> env;
  private HashMap<Pair<E, S>, Optional<E>> visited;
  private IdentityHashMap<E, List<IExpressionCell<E, T>>> memoryCells;

  public InstEnv(E expr) {
    this.parentEnv = Optional.empty();
    this.env = new HashMap<>();
    this.visited = new HashMap<>();
    this.bindingExpression = expr;
    this.memoryCells = new IdentityHashMap<>();
  }

  public InstEnv(InstEnv<E, T, S> parent, E expr) {
    super(parent);
    this.parentEnv = Optional.ofNullable(parent);
    this.env = new HashMap<>();
    this.bindingExpression = expr;

    // This is not a copy, but a completely shared reference!
    this.visited = parent.visited;
    this.memoryCells = parent.memoryCells;
  }

  public E getBindingExpression() {
    return this.bindingExpression;
  }

  public void put(Symbol<E, T> sym, E expr, int position) {
    this.env.put(sym, Pair.of(expr, position));
  }

  public Optional<E> get(Symbol<E, T> sym) {
    return this.getExprAndPosition(sym).map(value -> value.getLeft());
  }

  public Optional<Pair<E, Integer>> getExprAndPosition(Symbol<E, T> sym) {
    var lookedUp = this.env.get(sym);
    if (lookedUp == null && this.parentEnv.isPresent()) {
      return this.parentEnv.get().getExprAndPosition(sym);
    }
    return Optional.ofNullable(lookedUp);
  }

  public Optional<E> getScopeExpression(Symbol<E, T> sym) {
    var lookedUp = this.env.get(sym);
    if (lookedUp != null) {
      return Optional.ofNullable(this.bindingExpression);
    }

    if (lookedUp == null && this.parentEnv.isPresent()) {
      return this.parentEnv.get().getScopeExpression(sym);
    }

    return Optional.empty();
  }

  public void visit(E expr, S solution) {
    this.visited.put(Pair.of(expr, solution), Optional.empty());
  }

  public boolean isVisisted(E expr, S solution) {
    return this.visited.containsKey(Pair.of(expr, solution));
  }

  public void setSolution(E expr, S solution, E result) {
    var key = Pair.of(expr, solution);
    this.visited.compute(key, (k, v) -> Optional.of(result));
  }

  public boolean hasSolution(E expr, S solution) {
    var key = Pair.of(expr, solution);
    return this.visited.containsKey(key) && this.visited.get(key).isPresent();
  }

  public E getSolutionOrThrow(E expr, S solution) {
    if (!this.hasSolution(expr, solution)) {
      throw new RuntimeException("" + expr + " must have  a solution");
    }
    var key = Pair.of(expr, solution);
    return this.visited.get(key).get();
  }

  public Optional<E> getSolution(E expr, S solution) {
    var key = Pair.of(expr, solution);
    if (this.visited.containsKey(key)) {
      return this.visited.get(key);
    } else {
      return Optional.empty();
    }
  }

  public void addCellForExpr(E expr, IExpressionCell<E, T> cell) {
    this.memoryCells.computeIfAbsent(expr, e -> new ArrayList<>());

    this.memoryCells.get(expr).add(cell);
  }

  public List<IExpressionCell<E, T>> getCellsForExpressions(E expr) {
    if (this.memoryCells.containsKey(expr)) {
      return List.copyOf(this.memoryCells.get(expr));
    } else {
      return List.of();
    }
  }
}

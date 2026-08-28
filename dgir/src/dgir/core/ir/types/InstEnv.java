package dgir.core.ir.types;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

public final class InstEnv<E extends Expression<E, T>, T extends Type, S> extends HashConsing<E, T> {
  private Optional<InstEnv<E, T, S>> parentEnv;
  private E bindingExpression;
  private HashMap<Symbol<E, T>, Pair<E, Integer>> env;
  private HashSet<Pair<E, S>> visited;

  public InstEnv(E expr) {
    this.parentEnv = Optional.empty();
    this.env = new HashMap<>();
    this.visited = new HashSet<>();
    this.bindingExpression = expr;
  }

  public InstEnv(InstEnv<E, T, S> parent, E expr) {
    super(parent);
    this.parentEnv = Optional.ofNullable(parent);
    this.env = new HashMap<>();
    // This is not a copy, but a completely shared reference!
    this.visited = parent.visited;
    this.bindingExpression = expr;
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

  public void visit(Pair<E, S> expr) {
    this.visited.add(expr);
  }

  public boolean isVisisted(Pair<E, S> expr) {
    return this.visited.contains(expr);
  }
}

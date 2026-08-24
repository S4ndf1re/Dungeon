package dgir.core.ir.types;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

public final class InstEnv<E extends Expression<E, T>, T extends Type, S> extends HashConsing<E, T> {
  private HashMap<Symbol<E, T>, E> env;
  private HashSet<Pair<E, S>> visited;

  public InstEnv() {
    this.env = new HashMap<>();
    this.visited = new HashSet<>();
  }

  public InstEnv(InstEnv<E, T, S> env) {
    this.env = new HashMap<>(env.env);
    // This is not a copy, but a completely shared reference!
    this.visited = env.visited;
  }

  public void put(Symbol<E, T> sym, E expr) {
    this.env.put(sym, expr);
  }

  public Optional<E> get(Symbol<E, T> sym) {
    return Optional.ofNullable(this.env.get(sym));
  }

  public void visit(Pair<E, S> expr) {
    this.visited.add(expr);
  }

  public boolean isVisisted(Pair<E, S> expr) {
    return this.visited.contains(expr);
  }
}

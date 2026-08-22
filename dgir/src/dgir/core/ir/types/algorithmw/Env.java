package dgir.core.ir.types.algorithmw;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeDialect.TypeInferenceSolver.ConversionContext;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.compatibility.Scope.ScopeLike;

public final class Env extends ScopeLike<AlgorithmWType> implements ConversionContext<Expr, AlgorithmWType> {
  private HashMap<Symbol<Expr, AlgorithmWType>, Scheme> env;

  public Env() {
    super();
    this.env = new HashMap<>();
  }

  public Env(Env other) {
    super(other);
    this.env = new HashMap<>(other.env);
  }

  public Scheme get(Symbol<Expr, AlgorithmWType> sym) {
    return this.env.get(sym);
  }

  public Scheme put(Symbol<Expr, AlgorithmWType> sym, Scheme scheme) {
    return this.env.put(sym, scheme);
  }

  @Override
  public final String toString() {
    return ("{" +
        env
            .entrySet()
            .stream()
            .map(entry -> entry.getKey() + " -> " + entry.getValue())
            .collect(Collectors.joining(", "))
        +
        "}");
  }

  /**
   * Apply the subst to this env.
   *
   * @param subst the subst to apply with
   * @return the applied env where subst is applied to this
   */
  public Env apply(Subst subst) {
    var newEnv = this.copy();
    for (var entry : this.env.entrySet()) {
      newEnv.env.put(entry.getKey(), entry.getValue().apply(subst));
    }
    return new Env(newEnv);
  }

  /**
   * Get the free type Variables that are unbound for the whole environment
   *
   * @return
   */
  public Set<TypeVar> freeTypeVars() {
    var set = new HashSet<TypeVar>();

    for (var entry : this.env.entrySet()) {
      set.addAll(entry.getValue().freeTypeVars());
    }

    return Set.copyOf(set);
  }

  public Env copy() {
    return new Env(this);
  }

}

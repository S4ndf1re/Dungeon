package dgir.core.ir.types;

import java.util.HashMap;

import org.apache.commons.lang3.tuple.Pair;

/**
 * A utility class for hash consing (hash-based canonicalization) of
 * expressions.
 * Hash consing ensures that structurally identical expressions are represented
 * by the same
 * object in memory, reducing redundancy and improving efficiency for equality
 * checks and
 * memory usage.
 *
 * @param <E> The type of the expression, which must extend {@link Expression}.
 * @param <T> The type of the type associated with the expression, which must
 *            extend {@link Type}.
 */
public class HashConsing<E extends Expression<E, T>, T extends Type<T>, S> {

  /**
   * A map that stores canonical instances of expressions, keyed by the
   * expression together with the solution it was instantiated under. Two
   * instantiations of the same expression under different solutions (e.g. two
   * scheme instances of a polymorphic function) must NOT share a node, as the
   * per-instance unifications would otherwise corrupt each other.
   */
  public HashMap<Pair<E, S>, E> hashConsedExprs;

  /**
   * Initializes a new instance with an empty map for storing canonical
   * expressions.
   */
  public HashConsing() {
    this.hashConsedExprs = new HashMap<>();
  }

  public HashConsing(HashConsing<E, T, S> other) {
    this.hashConsedExprs = other.hashConsedExprs;
  }

  /**
   * Retrieves the canonical instance of the given expression instantiated
   * under the given solution. If no instance is stored yet, the expression is
   * added to the map and returned.
   *
   * @param expr     the expression to canonicalize.
   * @param solution the solution the expression is instantiated under.
   * @return The canonical instance for this expression/solution pair.
   */
  public E getConsed(E expr, S solution) {
    var key = Pair.of(expr, solution);
    var existing = this.hashConsedExprs.putIfAbsent(key, expr);
    return existing != null ? existing : expr;
  }

}

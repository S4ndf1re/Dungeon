package dgir.core.ir.types;

import java.util.HashMap;

/**
 * A utility class for hash consing (hash-based canonicalization) of expressions.
 * Hash consing ensures that structurally identical expressions are represented by the same
 * object in memory, reducing redundancy and improving efficiency for equality checks and
 * memory usage.
 *
 * @param <E> The type of the expression, which must extend {@link Expression}.
 * @param <T> The type of the type associated with the expression, which must extend {@link Type}.
 */
public class HashConsing<E extends Expression<E, T>, T extends Type> {

  /**
   * A map that stores canonical instances of expressions.
   * Each unique expression (based on its {@code equals} and {@code hashCode} methods)
   * is stored once, and subsequent requests for the same expression return the stored instance.
   */
  public HashMap<E, E> hashConsedExprs;

  /**
   * Initializes a new instance of {@code HashConsing} with an empty map for storing canonical expressions.
   */
  public HashConsing() {
    this.hashConsedExprs = new HashMap<>();
  }

  /**
   * Adds an expression to the canonical map if it is not already present.
   *
   * @param expr The expression to add to the canonical map.
   */
  private void addExpr(E expr) {
    if (!this.hashConsedExprs.containsKey(expr)) {
      this.hashConsedExprs.put(expr, expr);
    }
  }

  /**
   * Retrieves the canonical instance of the given expression.
   * If the expression is already in the map, the stored instance is returned.
   * Otherwise, the expression is added to the map and the canonical instance is returned.
   *
   * @param expr The expression to canonicalize.
   * @return The canonical instance of the expression.
   */
  public E getConsed(E expr) {
    if (this.hashConsedExprs.containsKey(expr)) {
      return this.hashConsedExprs.get(expr);
    }

    this.addExpr(expr);
    return this.getConsed(expr);
  }

}

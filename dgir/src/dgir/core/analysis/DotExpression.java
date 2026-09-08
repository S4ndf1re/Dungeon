package dgir.core.analysis;

import java.util.IdentityHashMap;
import java.util.List;

import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Type;

/**
 * Builds a Graphviz DOT representation of an {@link Expression} tree.
 *
 * <p>
 * Mirrors {@link DotCFG}: instead of the operation/region/block hierarchy, this
 * walks the expression tree and emits one DOT node per (identity-distinct)
 * expression, labeled with the expression's concrete kind and its inferred type
 * (when present). Edges connect a parent expression to each of its children.
 *
 * <p>
 * The DOT node id is the expression's {@link System#identityHashCode(Object)
 * identity hash}, so one node corresponds to exactly one object instance.
 * Shared (hash-consed) subexpressions are emitted once and simply receive
 * multiple incoming edges.
 *
 * <p>
 * In {@link VisitGetChildrenOption#ONLY_INSTANTIATED} mode the walk follows
 * {@link Expression#getInstantiableChildren()} instead of
 * {@link Expression#getChildren()}, so stale (not re-instantiated) subtrees
 * — e.g. a let binding's original body — are not drawn. This renders the tree
 * as it looks after instantiation, matching the reconstructed operations.
 *
 * <p>
 * The resulting DOT string can be printed, saved as {@code .dot}, or rendered
 * to an image with {@code Graphviz.fromString(...)}, as done in
 * {@code DgirTestUtils}.
 */
public class DotExpression {

  public static enum VisitGetChildrenOption {
    ALL_CHILDREN,
    ONLY_INSTANTIATED;
  }

  private DotExpression() {}

  /**
   * Build the DOT graph string for the expression tree rooted at {@code root},
   * following {@link Expression#getChildren()}.
   *
   * @param root the expression to start from.
   * @param <E>  the concrete expression type.
   * @param <T>  the concrete type attached to the expressions.
   * @return a DOT digraph string.
   */
  public static <E extends Expression<E, T>, T extends Type> String toDot(E root) {
    return toDot(root, VisitGetChildrenOption.ALL_CHILDREN);
  }

  /**
   * Build the DOT graph string for the expression tree rooted at {@code root}.
   *
   * @param root              the expression to start from.
   * @param getChildrenOption whether to follow all children or only the
   *                          instantiable ones (see
   *                          {@link Expression#getInstantiableChildren()}).
   * @param <E>               the concrete expression type.
   * @param <T>               the concrete type attached to the expressions.
   * @return a DOT digraph string.
   */
  public static <E extends Expression<E, T>, T extends Type> String toDot(
      E root,
      VisitGetChildrenOption getChildrenOption) {
    StringBuilder dot = new StringBuilder();
    dot.append("digraph expr {\n");
    dot.append("\tnode [shape=box];\n");

    if (root != null) {
      IdentityHashMap<Expression<E, T>, String> emitted = new IdentityHashMap<>();
      appendExpression(dot, root, emitted, getChildrenOption);
    }

    dot.append("}\n");
    return dot.toString();
  }

  private static <E extends Expression<E, T>, T extends Type> String appendExpression(
      StringBuilder dot,
      E expr,
      IdentityHashMap<Expression<E, T>, String> emitted,
      VisitGetChildrenOption getChildrenOption) {
    String id = emitted.get(expr);
    if (id != null) {
      // Already emitted (shared subexpression); only the caller adds the edge.
      return id;
    }

    id = "n" + System.identityHashCode(expr);
    emitted.put(expr, id);

    StringBuilder label = new StringBuilder(expr.getClass().getSimpleName());

    expr.getInferredType().ifPresent(ty -> label.append("\\n: ").append(escape(ty.toString())));

    expr.getUnderlyingOperation()
        .ifPresent(op -> label.append("\\n[").append(escape(op.getDetails().ident())).append("]"));

    dot.append("\t").append(id).append(" [label=\"").append(label).append("\"];\n");

    List<E> children = getChildrenOption == VisitGetChildrenOption.ALL_CHILDREN
        ? expr.getChildren()
        : expr.getInstantiableChildren();
    for (E child : children) {
      String childId = appendExpression(dot, child, emitted, getChildrenOption);
      dot.append("\t").append(id).append(" -> ").append(childId).append(";\n");
    }

    return id;
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}

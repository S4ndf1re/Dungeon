package dgir.core.analysis;

import java.util.IdentityHashMap;
import java.util.List;

import dgir.core.ir.types.Expression;

import dgir.core.ir.types.traits.IAbstraction;
import dgir.core.ir.types.Type;

/**
 * Builds a Graphviz DOT representation of an {@link Expression} tree.
 *
 * <p>
 * Mirrors {@link DotCFG}: instead of the operation/region/block hierarchy, this
 * walks the expression tree and emits one DOT node per (identity-distinct)
 * expression, labeled with the expression's concrete kind and its inferred type
 * (when present).
 * <p>
 * Edges are drawn in data-flow direction: child (value) -> parent (usage),
 * drawn dotted. Scope-forming expressions (abstractions and let bindings) are
 * the exception: their edges are drawn solid parent -> child, since they
 * create sub bodies rather than consume values.
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
  public static <E extends Expression<E, T>, T extends Type<T>> String toDot(E root) {
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
  public static <E extends Expression<E, T>, T extends Type<T>> String toDot(
      E root,
      VisitGetChildrenOption getChildrenOption) {
    StringBuilder dot = new StringBuilder();
    dot.append("digraph expr {\n");
    dot.append("\tnode [shape=box];\n");

    if (root != null) {
      IdentityHashMap<Expression<E, T>, String> emitted = new IdentityHashMap<>();
      appendExpression(dot, root, emitted, getChildrenOption, false, null, null);
    }

    dot.append("}\n");
    return dot.toString();
  }

  /**
   * Build the DOT graph string for the expression tree rooted at {@code root},
   * drawing the bounding scope of each expression as a dashed Graphviz cluster
   * box. A node belongs to the cluster of its
   * {@link Expression#getParentScopeExpr() bounding scope expression}; its
   * {@link Expression#getParentScopePosition() position} within that scope is
   * added to the node label.
   *
   * @param root the expression to start from.
   * @param <E>  the concrete expression type.
   * @param <T>  the concrete type attached to the expressions.
   * @return a DOT digraph string with one cluster per bounding scope.
   */
  public static <E extends Expression<E, T>, T extends Type<T>> String toDotWithScopes(E root) {
    return toDotWithScopes(root, VisitGetChildrenOption.ALL_CHILDREN);
  }

  /**
   * See {@link #toDotWithScopes(Expression)}.
   *
   * @param root              the expression to start from.
   * @param getChildrenOption whether to follow all children or only the
   *                          instantiable ones.
   */
  public static <E extends Expression<E, T>, T extends Type<T>> String toDotWithScopes(
      E root,
      VisitGetChildrenOption getChildrenOption) {
    StringBuilder dot = new StringBuilder();
    dot.append("digraph expr {\n");
    dot.append("\tnode [shape=box];\n");

    if (root != null) {
      IdentityHashMap<Expression<E, T>, String> emitted = new IdentityHashMap<>();
      IdentityHashMap<Expression<E, T>, StringBuilder> clusters = new IdentityHashMap<>();
      StringBuilder freeNodes = new StringBuilder();
      appendExpression(dot, root, emitted, getChildrenOption, true, clusters, freeNodes);

      clusters.forEach((scope, cluster) -> {
        dot.append("\tsubgraph cluster_n").append(System.identityHashCode(scope)).append(" {\n");
        dot.append("\t\tlabel=\"scope: ").append(scope.getClass().getSimpleName()).append("\";\n");
        dot.append("\t\tstyle=dashed;\n");
        dot.append(cluster);
        dot.append("\t}\n");
      });
      dot.append(freeNodes);
    }

    dot.append("}\n");
    return dot.toString();
  }

  private static <E extends Expression<E, T>, T extends Type<T>> String appendExpression(
      StringBuilder dot,
      E expr,
      IdentityHashMap<Expression<E, T>, String> emitted,
      VisitGetChildrenOption getChildrenOption,
      boolean withScopes,
      IdentityHashMap<Expression<E, T>, StringBuilder> clusters,
      StringBuilder freeNodes) {
    String id = emitted.get(expr);
    if (id != null) {
      // Already emitted (shared subexpression); only the caller adds the edge.
      return id;
    }

    id = "n" + System.identityHashCode(expr);
    emitted.put(expr, id);

    StringBuilder label = new StringBuilder(expr.getClass().getSimpleName());

    expr.getInferredType().ifPresent(ty -> label.append("\\n: ").append(escape(ty.toString())));

    if (withScopes) {
      expr.getParentScopePosition().ifPresent(pos -> label.append("\\n#").append(pos));
    }

    expr.getUnderlyingOperation()
        .ifPresent(op -> label.append("\\n[").append(escape(op.getDetails().ident())).append("]"));

    String nodeLine = "\t" + id + " [label=\"" + label + "\"];\n";
    if (withScopes) {
      var scope = expr.getParentScopeExpr().orElse(null);
      if (scope != null) {
        clusters.computeIfAbsent(scope, s -> new StringBuilder()).append(nodeLine);
      } else {
        freeNodes.append(nodeLine);
      }
    } else {
      dot.append(nodeLine);
    }

    boolean parentToChild = expr instanceof IAbstraction<?, ?>
        || expr.getClass().getSimpleName().contains("Let");
    List<E> children = getChildrenOption == VisitGetChildrenOption.ALL_CHILDREN
        ? expr.getChildren()
        : expr.getInstantiableChildren();
    for (E child : children) {
      String childId = appendExpression(dot, child, emitted, getChildrenOption, withScopes,
          clusters, freeNodes);
      if (parentToChild) {
        // Scope-forming edge (sub body): solid.
        dot.append("\t").append(id).append(" -> ").append(childId).append(";\n");
      } else {
        // Usage edge (value flows into consumer): dotted.
        dot.append("\t").append(childId).append(" -> ").append(id)
            .append(" [style=dotted];\n");
      }
    }

    return id;
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}

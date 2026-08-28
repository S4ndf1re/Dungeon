package dgir.core.ir.types;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dgir.core.ir.Operation;
import dgir.core.ir.types.compatibility.ExprOrOperator;

public interface Expression<E extends Expression<E, T>, T extends Type> {

  @FunctionalInterface
  public interface InstantiateOperation<E extends Expression<E, T>, T extends Type> {
    public Operation instantiate(E expr);
  }

  public void setInferredType(T inferredType);

  public Optional<T> getInferredType();

  /**
   * Collect a list of all child expressions. I.e. all expressions that are found
   * to be children in the expression tree of the called on {@link Expr}.
   *
   * <p>
   * This method is used to automatically recurse down the expression tree to
   * build all polymorphic instances.
   *
   * @return the list of all children {@link ExprOrOperator}
   */
  public List<E> getChildren();

  /**
   * Some operations, like Let expressions don't really instantiate all bindings.
   * Instead, only the let bindings body is instantiated!
   * This method aims to only return the instantiable expressions, i.e. those that
   * would actually be instantiated in the Expr instantiation!
   *
   * <p>
   * By defaut, this returns all children, except when overwritten!
   *
   * @return the list of all children that would be instantiated on Expression
   *         instantiation
   */
  public default List<E> getInstantiableChildren() {
    return this.getChildren();
  }

  /**
   * When an {@link Expression} is a variable that is just a reference to another
   * {@link Symbol} within the algorithm specific environment,
   * this function is expected to return the {@link Symbol} to that reference.
   *
   * <p>
   * For an {@link Expression} like {@link ExprVar}, this is a trivial {@link Env}
   * lookup.
   * However, custom
   * {@link Expression}s may also provide this functionality in some way, and
   * hence must
   * expose the potentially referenced {@link Symbol}.
   *
   * @return `Some(var)` if `var` is a variable bound by this expression
   */
  public Optional<Symbol<E, T>> getReferencedVariable();

  public default void reinstantiateSymbols() {
  }

  public E replaceSymbol(Symbol<E, T> original, Symbol<E, T> replacement);

  public void setUnderlyingOperation(Operation op);

  public Optional<Operation> getUnderlyingOperation();

  public Optional<E> getParentScopeExpr();

  public Optional<Integer> getParentScopePosition();

  public void setInstantiateOperationCallback(InstantiateOperation<E, T> callback);

  public Optional<InstantiateOperation<E, T>> getInstantiateOperationCallback();

  public class ExpressionVisitor<E extends Expression<E, T>, T extends Type> {
    private Set<E> visited;
    private VisitOrder order;
    private VisitGetChildrenOption getChildrenOption;

    public ExpressionVisitor(VisitOrder order) {
      this.visited = Collections.newSetFromMap(new IdentityHashMap<>());
      this.order = order;
      this.getChildrenOption = VisitGetChildrenOption.ALL_CHILDREN;
    }

    public ExpressionVisitor(VisitOrder order, VisitGetChildrenOption getChildrenOption) {
      this.visited = Collections.newSetFromMap(new IdentityHashMap<>());
      this.order = order;
      this.getChildrenOption = getChildrenOption;
    }

    public static enum VisitOrder {
      IN_ORDER,
      POST_ORDER;
    }

    public static enum VisitGetChildrenOption {
      ALL_CHILDREN,
      ONLY_INSTANTIATED;
    }

    @FunctionalInterface
    public static interface Visitor<E extends Expression<E, T>, T extends Type> {
      public void visit(E expr);
    }

    public static interface VisitState<E extends Expression<E, T>, T extends Type> {
      public default void enter(E expr) {
      }

      public default void exit(E expr) {
      }
    }

    private boolean precheckVisit(E root, Visitor<E, T> visitor) {
      if (visitor == null) {
        return false;
      }
      if (this.visited.contains(root)) {
        return false;
      }
      this.visited.add(root);

      return true;
    }

    public void visit(E root, Visitor<E, T> visitor) {
      if (!this.precheckVisit(root, visitor)) {
        return;
      }

      if (root != null) {
        if (this.order == VisitOrder.IN_ORDER) {
          visitor.visit(root);
        }

        List<E> children = null;
        if(this.getChildrenOption == VisitGetChildrenOption.ALL_CHILDREN) {
          children = root.getChildren();
        } else {
          children = root.getInstantiableChildren();
        }
        for (var child : children) {
          this.visit(child, visitor);
        }

        if (this.order == VisitOrder.POST_ORDER) {
          visitor.visit(root);
        }
      }
    }

    public <S extends VisitState<E, T>> void visitWithState(E root, Visitor<E, T> visitor, S state) {
      if (!this.precheckVisit(root, visitor)) {
        return;
      }

      if (root != null) {
        if (this.order == VisitOrder.IN_ORDER) {
          visitor.visit(root);
        }

        // Enter and exit only around the children, as the root is visited from the
        // parent scope already
        state.enter(root);
        List<E> children = null;
        if(this.getChildrenOption == VisitGetChildrenOption.ALL_CHILDREN) {
          children = root.getChildren();
        } else {
          children = root.getInstantiableChildren();
        }
        for (var child : children) {
          this.visit(child, visitor);
        }
        state.exit(root);

        if (this.order == VisitOrder.POST_ORDER) {
          visitor.visit(root);
        }
      }
    }
  }
}

package dgir.core.ir.types;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import dgir.core.ir.Operation;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.traits.IExpressionCell;

public abstract class Expression<E extends Expression<E, T>, T extends Type> extends ExprOrOperator<E, T> {
  public Optional<T> inferredType;
  public Optional<Operation> underlyingOperation;
  public Optional<E> parentScopeExpression;
  public Optional<Integer> parentScopePosition;
  public Optional<InstantiateOperation<E, T>> instantiationCallback;

  public Expression() {
    this.inferredType = Optional.empty();
    this.underlyingOperation = Optional.empty();
    this.parentScopeExpression = Optional.empty();
    this.parentScopePosition = Optional.empty();
    this.instantiationCallback = Optional.empty();
  }

  public Expression(Expression<E, T> other) {
    this.inferredType = other.inferredType;
    this.underlyingOperation = other.underlyingOperation;
    this.parentScopeExpression = other.parentScopeExpression;
    this.parentScopePosition = other.parentScopePosition;
    this.instantiationCallback = other.instantiationCallback;
  }

  @FunctionalInterface
  public interface InstantiateOperation<E extends Expression<E, T>, T extends Type> {
    public Operation instantiate(E expr);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.inferredType,
        this.parentScopeExpression.isPresent() ? System.identityHashCode(this.parentScopeExpression.get()) : 0,
        this.parentScopePosition);
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object obj) {
    return obj instanceof Expression expr && this.inferredType.equals(expr.inferredType)
        && this.parentScopeExpression.orElse(null) == expr.parentScopeExpression.orElse(null)
        && this.parentScopePosition.equals(expr.parentScopePosition);
  }

  @Override
  public boolean isExpr() {
    return true;
  }

  @Override
  public boolean isOperator() {
    return false;
  }

  @SuppressWarnings("unchecked")
  @Override
  public E getExpr() {
    return (E) this;
  }

  public void setInferredType(Optional<T> inferredType) {
    this.inferredType = Optional.ofNullable(inferredType.orElse(null));
  }

  public Optional<T> getInferredType() {
    return this.inferredType;
  }

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
  public abstract List<E> getChildren();

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
  public List<E> getInstantiableChildren() {
    return this.getChildren();
  }

  public void reinstantiateSymbols() {
  }

  public abstract E replaceSymbol(Symbol<E, T> original, Symbol<E, T> replacement);

  public abstract boolean containsSymbol(Symbol<E, T> symbol);

  public void setUnderlyingOperation(Operation op) {
    this.underlyingOperation = Optional.ofNullable(op);
  }

  public Optional<Operation> getUnderlyingOperation() {
    return this.underlyingOperation;
  }

  public Optional<E> getParentScopeExpr() {
    return this.parentScopeExpression;
  }

  public Optional<Integer> getParentScopePosition() {
    return this.parentScopePosition;
  }

  public void setParentScopeExpression(Optional<E> expr, Optional<Integer> position) {
    this.parentScopeExpression = expr;
    this.parentScopePosition = position;
  }

  public void setInstantiateOperationCallback(InstantiateOperation<E, T> callback) {
    this.instantiationCallback = Optional.ofNullable(callback);
  }

  public Optional<InstantiateOperation<E, T>> getInstantiateOperationCallback() {
    return this.instantiationCallback;
  }

  @SuppressWarnings("unchecked")
  public E unwrapOrThis() {
    if (this instanceof IExpressionCell) {
      return ((IExpressionCell<E, T>) this).unwrap();
    }
    return (E) this;
  }

  /**
   * Creates a shallow copy of this expression: a new instance of the same
   * concrete type that references the SAME child expressions (not copies) and
   * carries the same state (inferred type, parent scope, underlying operation,
   * callbacks) as this expression. Only the node itself is duplicated.
   *
   * @return a new shallow copy of this expression node
   */
  public abstract E copy();

  public static class ExpressionVisitor<E extends Expression<E, T>, T extends Type> {
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
        if (this.getChildrenOption == VisitGetChildrenOption.ALL_CHILDREN) {
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
        if (this.getChildrenOption == VisitGetChildrenOption.ALL_CHILDREN) {
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

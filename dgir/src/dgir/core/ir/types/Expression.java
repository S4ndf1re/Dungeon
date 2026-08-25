package dgir.core.ir.types;

import java.util.List;
import java.util.Optional;

import dgir.core.ir.Operation;
import dgir.core.ir.types.compatibility.ExprOrOperator;

public interface Expression<E extends Expression<E, T>, T extends Type> {

  public interface SolutionContext<T extends Type> {
    T apply(T type);
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

  public E replaceSymbol(Symbol<E, T> original, Symbol<E, T> replacement);

  public void setUnderlyingOperation(Operation op);

  public Optional<Operation> getUnderlyingOperation();

}

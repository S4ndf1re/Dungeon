package dgir.core.ir.types;

import java.util.List;
import java.util.Optional;

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

  public E replaceSymbol(Symbol<E, T> original, Symbol<E, T> replacement);

}

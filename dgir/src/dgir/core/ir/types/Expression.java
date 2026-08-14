package dgir.core.ir.types;

import java.util.Optional;

import dgir.core.ir.Operation;

public interface Expression<T extends Type> {

  public interface SolutionContext<T extends Type> {
    T apply(T type);
  }

  public void setInferredType(T inferredType);

  public Optional<T> getInferredType();

  public interface SolutionRelayFunction<T extends Type> {
    void applySolution(T solvedType);
  }

  /**
   * After solving the type system, make sure to relay the solution back to the
   * origin of the expression (either {@link Operation} or {@link Expression}).
   */
  public void resolveUsingContext(SolutionContext<T> ctx);

  public void setSolver(SolutionRelayFunction<T> solutionRelay);

  public Optional<Operation> getOriginalOperation();
}

package dgir.core.ir.types;

import java.util.Optional;

public interface Expression<T extends Type> {

  public interface SolutionContext<T extends Type> {
    T apply(T type);
  }

  public void setInferredType(T inferredType);

  public Optional<T> getInferredType();

}

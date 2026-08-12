package dgir.core.ir.types.compatibility;

import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Type;

public sealed interface InferOrTransformResult<EO extends InferResultMarker<T>, E extends Expression<T>, T extends Type> {
  public default boolean isInfer() {
    return this instanceof Infer;
  }

  public default boolean isTransform() {
    return this instanceof Transform;
  }

  public default T getInferResult() {
    if (this.isInfer()) {
      return ((Infer<EO, E, T>) this).type;
    } else {
      throw new IllegalStateException("Not an infer type");
    }
  }

  public default E getTransformExpr() {
    if (this.isTransform()) {
      return ((Transform<EO, E, T>) this).expr;
    } else {
      throw new IllegalStateException("Not a transform type");
    }
  }

  public static final record Infer<EO extends InferResultMarker<T>, E extends Expression<T>, T extends Type>(
      T type) implements InferOrTransformResult<EO, E, T> {
  }

  public static final record Transform<EO extends InferResultMarker<T>, E extends Expression<T>, T extends Type>(
      E expr) implements InferOrTransformResult<EO, E, T> {
  }
}

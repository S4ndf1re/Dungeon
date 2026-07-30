package dgir.core.ir.types.compatibility;

import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Type;

public sealed interface InferOrTransformResult<T extends InferResultMarker<? extends Type>, E extends Expression> {
  public default boolean isInfer() {
    return this instanceof Infer;
  }

  public default boolean isTransform() {
    return this instanceof Transform;
  }

  public default T getInferResult() {
    if (this.isInfer()) {
      return ((Infer<T, E>) this).type;
    } else {
      throw new IllegalStateException("Not an infer type");
    }
  }

  public default E getTransformExpr() {
    if (this.isTransform()) {
      return ((Transform<T, E>) this).expr;
    } else {
      throw new IllegalStateException("Not a transform type");
    }
  }

  public static final record Infer<T extends InferResultMarker<? extends Type>, E extends Expression>(
      T type) implements InferOrTransformResult<T, E> {
  }

  public static final record Transform<T extends InferResultMarker<? extends Type>, E extends Expression>(
      E expr) implements InferOrTransformResult<T, E> {
  }
}

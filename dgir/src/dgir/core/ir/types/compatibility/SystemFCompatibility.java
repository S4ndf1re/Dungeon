package dgir.core.ir.types.compatibility;

import dgir.core.ir.types.SystemFInference;

/**
 * Implement type system compatibility with the algorihtm w implementation
 */
public interface SystemFCompatibility extends CompatibilityMarker {
  /**
   * Either infer the type of the implementing operation directly, or transform
   * the operation into an Expression for the correct type system.
   */
  public InferOrTransformResult<SystemFInference.TypeInference.TypeResult, SystemFInference.Expr> inferOrTransformSystemF(
      SystemFInference.TypeInference engine,
      SystemFInference.Context ctx);
}

package dgir.core.ir.types.algorithmw;

import dgir.core.ir.types.compatibility.CompatibilityMarker;
import dgir.core.ir.types.compatibility.InferOrTransformResult;

/**
 * Implement type system compatibility with the algorihtm w implementation
 */
public interface AlgorithmWCompatibility extends CompatibilityMarker {

  /**
   * Either infer the type of the implementing operation directly, or transform
   * the operation into an Expression for the correct type system.
   */
  public InferOrTransformResult<AlgorithmWInference.Expr.InferResult, AlgorithmWInference.Expr> inferOrTransformAlgorithmW(
      AlgorithmWInference.TypeInference engine,
      AlgorithmWInference.Env env);
}

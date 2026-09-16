package dgir.core.ir.types;


import dgir.core.ir.Region;

public class OperationExprConversionUtils {

  public static <E extends Expression<E, T>, T extends Type<T>, EngineT extends TypeInferenceSolver<EngineT, E, T>> E regionToExpr(
      EngineT engine, Region region) {
    GeneralBlock generalBlock = GeneralBlock.fromBlock(region.getBlocks().getFirst());

    return engine.generalBlockToInferenceExpr(generalBlock);
  }
}

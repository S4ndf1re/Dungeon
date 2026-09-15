package dgir.core.ir.types;


import dgir.core.ir.Region;

import dgir.core.ir.types.TypeDialect.TypeInferenceSolver;

public class OperationExprConversionUtils {

  public static <E extends Expression<E, T>, T extends Type, EngineT extends TypeInferenceSolver<E, T>> E regionToExpr(
      EngineT engine, Region region) {
    GeneralBlock generalBlock = GeneralBlock.fromBlock(region.getBlocks().getFirst());

    return engine.generalBlockToInferenceExpr(generalBlock);
  }
}

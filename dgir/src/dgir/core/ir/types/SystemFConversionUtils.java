package dgir.core.ir.types;

import java.util.Optional;

import dgir.core.ir.types.systemf.Context;
import dgir.core.ir.types.systemf.SystemFType;
import dgir.core.ir.types.systemf.TypeInference;

public final class SystemFConversionUtils {
  public static SystemFType irTypeToSystemFType(TypeInference engine, dgir.core.ir.Type irType) {
    var result = engine.generalNominalTypeToInferenceType(irType.asParameterizedNominalType(),
        Optional.of(engine.getStartContext()));
    engine.setStartContext((Context) result.getRight().get());

    return result.getLeft();
  }
}

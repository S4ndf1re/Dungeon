package dgir.core.ir.types;

import java.util.Optional;

import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;

public final class SystemFConversionUtils {
  public static SystemFType irTypeToSystemFType(TypeInference engine, dgir.core.ir.Type irType) {
    var result = engine.generalNominalTypeToInferenceType(irType.asParameterizedNominalType(),
        Optional.of(engine.getStartContext()));
    engine.setStartContext((Context) result.getRight().get());

    return result.getLeft();
  }
}

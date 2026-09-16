package dgir.core.ir.types.builtin.hmx;

import dgir.core.ir.types.TypeDialect;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;

public final class HMXInference extends TypeDialect<TypeInference, HMXExpr, HMXType> {

  @Override
  protected TypeInference instantiateSolver() {
    return new TypeInference();
  }

  @Override
  protected TypeInference instantiateSolver(TypeDialectConverterRegistry registry) {
    return new TypeInference(registry);
  }

}

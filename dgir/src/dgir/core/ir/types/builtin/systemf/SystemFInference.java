package dgir.core.ir.types.builtin.systemf;

import dgir.core.ir.types.TypeDialect;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;

public final class SystemFInference
    extends
    TypeDialect<TypeInference, Expr, SystemFType> {

  @Override
  protected TypeInference instantiateSolver() {
    return new TypeInference();
  }

  @Override
  protected TypeInference instantiateSolver(TypeDialectConverterRegistry registry) {
    return new TypeInference(registry);
  }


}

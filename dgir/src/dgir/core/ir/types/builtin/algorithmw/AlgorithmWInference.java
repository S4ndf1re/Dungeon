package dgir.core.ir.types.builtin.algorithmw;

import dgir.core.ir.types.TypeDialect;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;

public final class AlgorithmWInference
    extends
    TypeDialect<TypeInference, Expr, AlgorithmWType> {

  @Override
  protected TypeInference instantiateSolver() {
    return new TypeInference();
  }

  @Override
  protected TypeInference instantiateSolver(TypeDialectConverterRegistry registry) {
    return new TypeInference(registry);
  }

}

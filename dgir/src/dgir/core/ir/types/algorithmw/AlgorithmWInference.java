package dgir.core.ir.types.algorithmw;

import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeDialect;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.compatibility.ConverterRegistry;

import java.util.List;
import java.util.Optional;

public final class AlgorithmWInference
    extends
    TypeDialect<ExprOrOperator<Expr, AlgorithmWType>, Expr, AlgorithmWType> {

  private static Optional<TypeInference> instance = Optional.empty();

  @Override
  public TypeInferenceSolver<ExprOrOperator<Expr, AlgorithmWType>, Expr, AlgorithmWType> getSolverInstance() {
    var converterRegistry = ConverterRegistry.getConverterForDialect(AlgorithmWInference.class);

    // The solver binds to the converter registry at creation time. If converters
    // were registered after a default solver was cached, rebuild it so the
    // registered converters become visible.
    if (instance.isPresent()
        && converterRegistry.isPresent()
        && instance.get().getRegistry() != converterRegistry.get()) {
      instance = Optional.empty();
    }

    if (instance.isPresent()) {
      return instance.get();
    }

    TypeInference solver = converterRegistry.isPresent()
        ? new TypeInference(converterRegistry.get())
        : new TypeInference();
    AlgorithmWInference.instance = Optional.of(solver);
    return solver;
  }

  @Override
  public List<Class<? extends Type>> getAllowedTypes() {
    return TypeDialect.extractTypesFromAbstract(AlgorithmWType.class);
  }

  @Override
  public List<Class<? extends Expression<Expr, AlgorithmWType>>> getAllowedExpressions() {
    return TypeDialect.extractExpressionsFromAbstract(Expr.class);
  }

}

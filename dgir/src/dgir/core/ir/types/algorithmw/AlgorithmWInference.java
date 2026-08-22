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
    if (AlgorithmWInference.instance.isPresent()) {
      return AlgorithmWInference.instance.get();
    } else {
      TypeInference solver = null;
      var converterRegistry = ConverterRegistry.getConverterForDialect(AlgorithmWInference.class);
      if (converterRegistry.isPresent()) {
        solver = new TypeInference(converterRegistry.get());
      } else {
        solver = new TypeInference();
      }
      AlgorithmWInference.instance = Optional.of(solver);
      return solver;
    }
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

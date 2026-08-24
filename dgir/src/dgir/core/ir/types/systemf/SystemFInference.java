package dgir.core.ir.types.systemf;

import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeDialect;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import java.util.List;
import java.util.Optional;

public final class SystemFInference
    extends
    TypeDialect<ExprOrOperator<Expr, SystemFType>, Expr, SystemFType> {

  private static Optional<TypeInference> solver = Optional.empty();

  @Override
  public List<Class<? extends Type>> getAllowedTypes() {
    return TypeDialect.extractTypesFromAbstract(SystemFType.class);
  }

  @Override
  public List<Class<? extends Expression<Expr, SystemFType>>> getAllowedExpressions() {
    return TypeDialect.extractExpressionsFromAbstract(Expr.class);
  }

  @Override
  public TypeInferenceSolver<ExprOrOperator<Expr, SystemFType>, Expr, SystemFType> getSolverInstance() {
    if (SystemFInference.solver.isPresent()) {
      return SystemFInference.solver.get();
    } else {
      TypeInference solverInstance = null;
      var converterRegistry = ConverterRegistry.getConverterForDialect(SystemFInference.class);
      if (converterRegistry.isPresent()) {
        solverInstance = new TypeInference(converterRegistry.get());
      } else {
        solverInstance = new TypeInference();
      }
      SystemFInference.solver = Optional.of(solverInstance);
      return solverInstance;
    }
  }





}

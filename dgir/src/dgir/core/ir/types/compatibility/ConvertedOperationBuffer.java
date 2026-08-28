package dgir.core.ir.types.compatibility;

import java.util.HashMap;
import java.util.Optional;

import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeDialect.TypeInferenceSolver;
import dgir.core.ir.types.compatibility.ConverterRegistry.ConverterFunction;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;

public class ConvertedOperationBuffer<EO extends ExprOrOperator<E, T>, E extends Expression<E, T>, T extends Type, SolverT extends TypeInferenceSolver<EO, E, T>> {
  private HashMap<Operation, E> converted;

  public ConvertedOperationBuffer() {
    converted = new HashMap<>();
  }

  public void add(Operation op, E expr) {
    this.converted.put(op, expr);
  }

  public Optional<E> get(Operation op) {
    return Optional.ofNullable(this.converted.get(op));
  }

  public E operationToExpr(SolverT engine, Operation op,
      TypeDialectConverterRegistry registry, Class<E> clazz) {
    var buffered = this.get(op);

    var exprConverted = buffered.orElseGet(() -> {
      @SuppressWarnings("unchecked")
      ConverterFunction<EO, E, T, SolverT> converter = (ConverterFunction<EO, E, T, SolverT>) registry
          .getConverter(op.asOp().getClass());
      Expression<E, T> convertedExpr = converter.convertToExpression(op, engine);

      if (!(clazz.isInstance(convertedExpr))) {
        throw new RuntimeException("Expression is not of type SystemFInference.Expr");
      }

      this.add(op, clazz.cast(convertedExpr));
      return clazz.cast(convertedExpr);
    });

    return exprConverted;
  }

}

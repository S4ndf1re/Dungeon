package dgir.core.ir.types.compatibility;

import java.util.HashMap;
import java.util.Optional;

import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeDialect.TypeInferenceSolver;
import dgir.core.ir.types.compatibility.ConverterRegistry.ConverterFunction;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;

public class ConvertedOperationBuffer<E extends Expression<T>, T extends Type> {
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

  public E operationToExpr(TypeInferenceSolver<ExprOrOperator<E>, E, T> engine, Operation op,
      TypeDialectConverterRegistry registry, Class<E> clazz) {
    var buffered = this.get(op);

    var exprConverted = buffered.orElseGet(() -> {
      @SuppressWarnings("unchecked")
      ConverterFunction<ExprOrOperator<E>, E, T> converter = (ConverterFunction<ExprOrOperator<E>, E, T>) registry
          .getConverter(op.asOp().getClass());
      Expression<T> convertedExpr = converter.convertToExpression(op, engine);

      if (!(clazz.isInstance(convertedExpr))) {
        throw new RuntimeException("Expression is not of type SystemFInference.Expr");
      }

      this.add(op, clazz.cast(convertedExpr));
      return clazz.cast(convertedExpr);
    });

    return exprConverted;
  }

}

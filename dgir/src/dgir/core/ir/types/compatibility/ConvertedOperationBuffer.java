package dgir.core.ir.types.compatibility;

import java.util.HashMap;
import java.util.Optional;

import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.TypeDialect.TypeInferenceSolver;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;

public class ConvertedOperationBuffer<E extends Expression> {
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

  public E operationToExpr(TypeInferenceSolver<ExprOrOperator<E>, E> engine, Operation op,
      TypeDialectConverterRegistry registry, Class<E> clazz) {
    var buffered = this.get(op);

    var exprConverted = buffered.orElseGet(() -> {
      var converter = registry.getConverter(op.asOp().getClass());
      Expression convertedExpr = converter.convertToExpression(op, engine);

      if (!(clazz.isInstance(convertedExpr))) {
        throw new RuntimeException("Expression is not of type SystemFInference.Expr");
      }

      this.add(op, clazz.cast(convertedExpr));
      return clazz.cast(convertedExpr);
    });

    return exprConverted;
  }

}

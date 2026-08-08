package dgir.core.ir.types.compatibility;

import java.util.HashMap;
import java.util.Optional;

import dgir.core.ir.Op;
import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeDialect;

public class ConverterRegistry {
  public static final record Pair(Op op, ConverterFunction converter) {
  }

  @FunctionalInterface
  public static interface ConverterFunction {
    <E extends Expression> E convertToExpression(
        Operation op);
  }

  public static final class TypeDialectConverterRegistry {
    private HashMap<Op, ConverterFunction> converters = new HashMap<>();

    public void addOpConverter(Op op, ConverterFunction fn) {
      this.converters.put(op, fn);
    }

    public void removeOpConverter(Op op) {
      this.converters.remove(op);
    }

    public ConverterFunction getConverter(Op op) {
      var converter = this.converters.get(op);
      if (converter == null) {
        throw new RuntimeException("Op " + op + " is not registered");
      }
      return converter;
    }

  }

  // Holy hell what a type this is ............ All in the name of type safety.
  // Right?
  private HashMap<Class<? extends TypeDialect<? extends InferOrTransformResult<? extends InferResultMarker<? extends Type>, ? extends Expression>, ? extends CompatibilityMarker, ? extends Expression, ? extends Type>>, TypeDialectConverterRegistry> converters;

  public void registerDialect(
      Class<? extends TypeDialect<? extends InferOrTransformResult<? extends InferResultMarker<? extends Type>, ? extends Expression>, ? extends CompatibilityMarker, ? extends Expression, ? extends Type>> dialect) {

    if (this.converters.containsKey(dialect)) {
      return;
    }

    this.converters.put(dialect, new TypeDialectConverterRegistry());
  }

  public void deregisterDialect(
      Class<? extends TypeDialect<? extends InferOrTransformResult<? extends InferResultMarker<? extends Type>, ? extends Expression>, ? extends CompatibilityMarker, ? extends Expression, ? extends Type>> dialect) {
    this.converters.remove(dialect);
  }

  public void addOperatorsToDialect(
      Class<? extends TypeDialect<? extends InferOrTransformResult<? extends InferResultMarker<? extends Type>, ? extends Expression>, ? extends CompatibilityMarker, ? extends Expression, ? extends Type>> dialect,
      Pair... pairs) {

    var convertersForDialect = this.converters.get(dialect);
    if (convertersForDialect == null) {
      throw new RuntimeException("Dialect " + dialect.getName() + " is not registered");
    }

    for (var pair : pairs) {
      convertersForDialect.addOpConverter(pair.op(), pair.converter());
    }
  }

  public Optional<ConverterFunction> getConverterForDialectAndOp(
      Class<? extends TypeDialect<? extends InferOrTransformResult<? extends InferResultMarker<? extends Type>, ? extends Expression>, ? extends CompatibilityMarker, ? extends Expression, ? extends Type>> dialect,
      Op op) {
    var dialectConverters = this.converters.get(dialect);
    if (dialectConverters == null) {
      return Optional.empty();
    }

    var converter = dialectConverters.getConverter(op);
    return Optional.of(converter);
  }

}

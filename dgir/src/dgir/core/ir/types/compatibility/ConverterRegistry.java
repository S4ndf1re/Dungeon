package dgir.core.ir.types.compatibility;

import java.util.HashMap;
import java.util.Optional;

import dgir.core.ir.Op;
import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression;
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
  private static HashMap<Class<? extends TypeDialect<?, ?, ?, ?>>, TypeDialectConverterRegistry> converters = new HashMap<>();

  public static void registerDialect(
      Class<? extends TypeDialect<?, ?, ?, ?>> dialect) {

    if (converters.containsKey(dialect)) {
      return;
    }

    converters.put(dialect, new TypeDialectConverterRegistry());
  }

  public static void deregisterDialect(
      Class<? extends TypeDialect<?, ?, ?, ?>> dialect) {
    converters.remove(dialect);
  }

  public static void addOperatorsToDialect(
      Class<? extends TypeDialect<?, ?, ?, ?>> dialect,
      Pair... pairs) {

    var convertersForDialect = converters.get(dialect);
    if (convertersForDialect == null) {
      throw new RuntimeException("Dialect " + dialect.getName() + " is not registered");
    }

    for (var pair : pairs) {
      convertersForDialect.addOpConverter(pair.op(), pair.converter());
    }
  }

  public static <D extends TypeDialect<?, ?, ?, ?>> Optional<TypeDialectConverterRegistry> getConverterForDialect(
      Class<D> dialect) {
    var dialectConverters = converters.get(dialect);
    if (dialectConverters == null) {
      return Optional.empty();
    }

    return Optional.ofNullable(dialectConverters);
  }

}

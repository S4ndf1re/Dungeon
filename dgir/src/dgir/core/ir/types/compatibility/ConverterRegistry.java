package dgir.core.ir.types.compatibility;

import java.util.HashMap;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Op;
import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeDialect;
import dgir.core.ir.types.TypeDialect.TypeInferenceSolver;

public class ConverterRegistry {

  @FunctionalInterface
  public static interface ConverterFunction {
    <EO extends ExprOrOperator<E>, E extends Expression<T>, T extends Type> E convertToExpression(
        Operation op,
        TypeInferenceSolver<EO, E, T> engine);
  }

  public static final class TypeDialectConverterRegistry {
    private HashMap<Class<? extends Op>, ConverterFunction> converters = new HashMap<>();

    public void addOpConverter(Class<? extends Op> op, ConverterFunction fn) {
      this.converters.put(op, fn);
    }

    public void removeOpConverter(Class<? extends Op> op) {
      this.converters.remove(op);
    }

    public ConverterFunction getConverter(Class<? extends Op> op) {
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

  @SafeVarargs
  public static void addOperatorsToDialect(
      Class<? extends TypeDialect<?, ?, ?, ?>> dialect,
      Pair<Class<? extends Op>, ConverterFunction>... pairs) {

    var convertersForDialect = converters.get(dialect);
    if (convertersForDialect == null) {
      throw new RuntimeException("Dialect " + dialect.getName() + " is not registered");
    }

    for (var pair : pairs) {
      convertersForDialect.addOpConverter(pair.getLeft(), pair.getRight());
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

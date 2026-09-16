package dgir.core.ir.types;

import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;

public abstract class TypeDialect<TypeInferenceT extends TypeInferenceSolver<TypeInferenceT, E, T>, E extends Expression<E, T>, T extends Type> {

  /**
   * Return the static instance of a specific type inference solver for a type
   * system.
   *
   * @return the static instance of the algorithm specific
   *         {@link TypeInferenceSolver}
   */
  public TypeInferenceT getNewSolverInstance() {
    var converterRegistry = ConverterRegistry.getConverterForDialect(this.getClass());

    TypeInferenceT solver = converterRegistry.isPresent()
        ? this.instantiateSolver(converterRegistry.get())
        : this.instantiateSolver();

    return solver;
  }

  protected abstract TypeInferenceT instantiateSolver();

  protected abstract TypeInferenceT instantiateSolver(TypeDialectConverterRegistry registry);

  /**
   * @return a list of allowed types to be used with the specific algorithm
   */
  @SuppressWarnings("unchecked")
  public List<Class<? extends Type>> getAllowedTypes() {
    Class<T> typeClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[2];
    return TypeDialect.extractTypesFromAbstract(typeClass);
  }

  /**
   * @return a list of allowed expressions to be used with the specific algorithm
   */
  @SuppressWarnings("unchecked")
  public List<Class<? extends Expression<E, T>>> getAllowedExpressions() {
    Class<E> exprClass = (Class<E>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1];
    return TypeDialect.extractExpressionsFromAbstract(exprClass);
  }

  @SuppressWarnings("unchecked")
  public static <E extends Expression<E, T>, T extends Type> List<Class<? extends Expression<E, T>>> extractExpressionsFromAbstract(
      Class<? extends Expression<E, T>> abstractInterface) {
    if (!Arrays.asList(abstractInterface.getInterfaces()).contains(
        Expression.class) && !abstractInterface.getSuperclass().equals(Expression.class)) {
      throw new IllegalStateException(
          "Expr must extend dgir.core.ir.types.Expression");
    }

    var possibleTypes = Arrays.asList(abstractInterface.getDeclaredClasses());

    Predicate<Class<?>> classInheritsExpression = obj -> {
      return (Arrays.asList(obj.getInterfaces()).contains(abstractInterface))
          || (obj.getSuperclass().equals(abstractInterface));
    };

    // SAFETY: This cast is inherently safe when Expr actually extends
    // Expression! Hence a check is concluded beforehand
    return possibleTypes
        .stream()
        .filter(classInheritsExpression)
        .map(clazz -> (Class<? extends Expression<E, T>>) clazz)
        .collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  public static List<Class<? extends Type>> extractTypesFromAbstract(
      Class<? extends Type> abstractClass) {
    System.out.println(abstractClass.getName());
    if (!abstractClass.getSuperclass().equals(Type.class)) {
      throw new IllegalStateException(
          "Type must extend dgir.core.ir.types.Type");
    }

    var possibleTypes = Arrays.asList(abstractClass.getDeclaredClasses());

    Predicate<Class<?>> classInheritsExpression = obj -> {
      return (obj.getSuperclass() != null && obj.getSuperclass().equals(abstractClass));
    };

    // SAFETY: This cast is inherently safe when Expr actually extends
    // Expression! Hence a check is concluded beforehand.
    return possibleTypes
        .stream()
        .filter(classInheritsExpression)
        .map(clazz -> (Class<? extends Type>) clazz)
        .collect(Collectors.toList());
  }
}

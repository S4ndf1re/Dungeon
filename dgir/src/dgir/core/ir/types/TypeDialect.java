package dgir.core.ir.types;

import dgir.core.ir.Operation;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.compatibility.InferOrTransformResult;
import dgir.core.ir.types.compatibility.InferResultMarker;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

public abstract class TypeDialect<IR extends InferOrTransformResult<? extends InferResultMarker<T>, E, T>, C extends ExprOrOperator<E, T>, E extends Expression<T>, T extends Type> {

  public abstract static class TypeInferenceSolver<EO extends ExprOrOperator<E, T>, E extends Expression<T>, T extends Type> {
    protected TypeDialectConverterRegistry registry;

    /**
     * A simpler marker interface, marking all allowed contexts for conversion from
     * GPNT to inference types.
     */
    public static interface ConversionContext<E, T> {
    }

    public TypeInferenceSolver(TypeDialectConverterRegistry registry) {
      this.registry = registry;
    }

    /**
     * Solve the full expression tree by applying algorithm specific logic, like
     * inference and unification.
     * Different algorithms provide different mechanisms and different general
     * logic.
     *
     * <p>
     * NOTE: to be able to solve non-{@link Expression}
     * trees containing some or all {@link Operation},
     * conversion functions must be registered globalls using
     * {@link ConverterRegistry}
     * Those registered converter functions are tasked with converting
     * {@link Operation} to inference specific {@link Expression}
     *
     * @param expr the {@link ExprOrOperator} to solve.
     *
     * @return a pair of the solved algorithm specific final type, and the fully
     *         type annotated expression tree in original structure
     */
    public abstract Pair<Type, ExprOrOperator<E, T>> solve(EO expr);

    /**
     * Convert a {@link GeneralBlock} to an algorithm specific {@link Expression}
     *
     * @param block the block to convert
     * @return the converted {@link Expression}
     */
    public abstract E generalBlockToInferenceExpr(GeneralBlock block);

    /**
     * Convert a {@link GeneralParameterizedNominalType} to an algorithm specific
     * type.
     *
     * @param type    the GPNT type to convert
     * @param context the algorithm specific context
     * @return a pair of the algorithm specific type, and a potentially modified
     *         context
     */
    public abstract Pair<T, Optional<ConversionContext<E, T>>> generalNominalTypeToInferenceType(
        GeneralParameterizedNominalType type,
        Optional<ConversionContext<E, T>> context);
  }

  /**
   * Return the static instance of a specific type inference solver for a type
   * system.
   *
   * @return the static instance of the algorithm specific
   *         {@link TypeInferenceSolver}
   */
  public abstract TypeInferenceSolver<C, E, T> getSolverInstance();

  /**
   * @return a list of allowed types to be used with the specific algorithm
   */
  public abstract List<Class<? extends Type>> getAllowedTypes();

  /**
   * @return a list of allowed expressions to be used with the specific algorithm
   */
  public abstract List<Class<? extends Expression<T>>> getAllowedExpressions();

  @SuppressWarnings("unchecked")
  public static <T extends Type> List<Class<? extends Expression<T>>> extractExpressionsFromAbstract(
      Class<? extends Expression<T>> abstractInterface) {
    if (!Arrays.asList(abstractInterface.getInterfaces()).contains(
        Expression.class)) {
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
        .map(clazz -> (Class<? extends Expression<T>>) clazz)
        .collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  public static List<Class<? extends Type>> extractTypesFromAbstract(
      Class<? extends Type> abstractClass) {
    if (!abstractClass.getSuperclass().equals(Type.class)) {
      throw new IllegalStateException(
          "Expr must extend dgir.core.ir.types.Expression");
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

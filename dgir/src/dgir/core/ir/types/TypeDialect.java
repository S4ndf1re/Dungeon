package dgir.core.ir.types;

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

public abstract class TypeDialect<IR extends InferOrTransformResult<? extends InferResultMarker<T>, E, T>, C extends ExprOrOperator<E>, E extends Expression<T>, T extends Type> {

  public abstract static class TypeInferenceSolver<EO extends ExprOrOperator<E>, E extends Expression<T>, T extends Type> {
    protected TypeDialectConverterRegistry registry;

    public static interface ConversionContext<E, T> {
    }

    public TypeInferenceSolver(TypeDialectConverterRegistry registry) {
      this.registry = registry;
    }

    public abstract Type solve(EO expr);

    public abstract E generalBlockToInferenceExpr(GeneralBlock block);

    public abstract Pair<Symbol, E> generalFunctionToInferenceExpr(GeneralFunctionType fn);

    public abstract Pair<T, Optional<ConversionContext<E, T>>> generalNominalTypeToInferenceType(
        GeneralParameterizedNominalType type,
        Optional<ConversionContext<E, T>> context);
  }

  public abstract TypeInferenceSolver<C, E, T> getSolverInstance();

  public abstract List<Class<? extends Type>> getAllowedTypes();

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

    // This cast is inherently safe, but only when Expr actually extends from
    // Expression! Hence a check is concluded before
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

    // This cast is inherently safe, but only when Expr actually extends from
    // Expression! Hence a check is concluded before
    return possibleTypes
        .stream()
        .filter(classInheritsExpression)
        .map(clazz -> (Class<? extends Type>) clazz)
        .collect(Collectors.toList());
  }
}

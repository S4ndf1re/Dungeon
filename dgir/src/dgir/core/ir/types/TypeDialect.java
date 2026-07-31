package dgir.core.ir.types;

import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.compatibility.InferOrTransformResult;
import dgir.core.ir.types.compatibility.InferResultMarker;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class TypeDialect<IR extends InferOrTransformResult<? extends InferResultMarker<? extends Type>, ? extends Expression>, C extends ExprOrOperator<? extends Expression>> {

  public abstract static class TypeInferenceSolver<T extends ExprOrOperator<? extends Expression>> {
     protected TypeDialectConverterRegistry registry;

    public TypeInferenceSolver(TypeDialectConverterRegistry registry) {
      this.registry = registry;
    }

    public abstract Type solve(T expr);
  }

  public abstract TypeInferenceSolver<C> getSolverInstance();

  public abstract List<Class<? extends Type>> getAllowedTypes();

  public void addAllowedExpression(Class<? extends Type> exprType) {
    throw new UnsupportedOperationException(
        "By default, a dialect must not allow additional expressions");
  }

  public abstract List<Class<? extends Expression>> getAllowedExpressions();

  @SuppressWarnings("unchecked")
  public static List<Class<? extends Expression>> extractExpressionsFromAbstract(
      Class<? extends Expression> abstractInterface) {
    if (!Arrays.asList(abstractInterface.getInterfaces()).contains(
        Expression.class)) {
      throw new IllegalStateException(
          "Expr must extend dgir.core.ir.types.Expression");
    }

    var possibleTypes = Arrays.asList(abstractInterface.getDeclaredClasses());

    Predicate<Class<?>> classInheritsExpression = obj -> {
      return (obj.getSuperclass() != null &&
          Arrays.asList(obj.getInterfaces()).contains(abstractInterface));
    };

    // This cast is inherently safe, but only when Expr actually extends from
    // Expression! Hence a check is concluded before
    return possibleTypes
        .stream()
        .filter(classInheritsExpression)
        .map(clazz -> (Class<? extends Expression>) clazz)
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

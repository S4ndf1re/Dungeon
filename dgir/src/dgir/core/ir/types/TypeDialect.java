package dgir.core.ir.types;

import dgir.core.analysis.OperationVerifier;
import dgir.core.analysis.OperationVerifier.VerifyOptions;
import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitGetChildrenOption;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.traits.IExpressionCell;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

public abstract class TypeDialect<E extends Expression<E, T>, T extends Type> {

  public abstract static class TypeInferenceSolver<E extends Expression<E, T>, T extends Type> {
    protected TypeDialectConverterRegistry registry;

    /**
     * Result of a full solve: the final type, the expression tree after
     * inference but before instantiation, and the instantiated tree.
     */
    public static record SolveResult<E>(Type type, E preInstantiation, E instantiated) {
    }

    /**
     * A simpler marker interface, marking all allowed contexts for conversion from
     * GPNT to inference types.
     */
    public static interface ConversionContext<E, T> {
    }

    public TypeInferenceSolver(TypeDialectConverterRegistry registry) {
      this.registry = registry;
    }

    /** Returns the converter registry this solver was bound to. */
    public TypeDialectConverterRegistry getRegistry() {
      return registry;
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
     * @return a {@link SolveResult} of the final type, the pre-instantiation
     *         expression tree, and the fully type annotated instantiated tree
     */
    public abstract SolveResult<E> solve(ExprOrOperator<E, T> expr);

    /**
     * Post-Solve stage to finish Expression Instantiation.
     *
     * <p>
     * This consists of four steps:
     * 1. Replace all old Symbols like Abstraction (function) parameter values
     * 2. Instantiate Operations from instantiated Expressions in bottom-up manner
     * 3. Clean up Temorary Blocks
     * 4. Validate resulting Operation tree
     */
    protected final E postSolve(E instantiated) {
      // 1. Replace values in Let and Abs expressions with new values
      // As Exprs are already hash-consed, this will visit every relevant expression
      // only once!
      // Additionally, function parameters are also unique Values, i.e. they cannot
      // get destroy hash-consing uniqueness!
      new Expression.ExpressionVisitor<E, T>(VisitOrder.IN_ORDER).visit(instantiated, e -> {
        e.reinstantiateSymbols();
      });
      // 2. Instantiate Operations bottom-up. As all values are newly assigned, this
      // operation will create a new operation tree
      // During this stage, make sure to fully type the values using the expressions
      // inferred types! The types are normally fully qualified, due to hash consing
      // and solution
      // applicaiton! In cases where the type is not fully qualified, throw a typing
      // error, as annotations may be needed to fully infer typing.
      // A few problems may arise in reconstructing the blocks and regions.
      // The new expression tree is actually a sea-of-nodes like Expression tree
      new Expression.ExpressionVisitor<E, T>(VisitOrder.POST_ORDER, VisitGetChildrenOption.ONLY_INSTANTIATED)
          .visit(instantiated, e -> {
            var instOp = e.getInstantiateOperationCallback();
            if (instOp.isPresent()) {
              @SuppressWarnings("unchecked")
              var exprUnwrapped = e instanceof IExpressionCell ? ((IExpressionCell<E, T>) e).unwrap() : e;
              var instantiatedOperation = instOp.get().instantiate(exprUnwrapped);
              e.setUnderlyingOperation(instantiatedOperation);
            }
          });

      // 3. Post-Process and move all temporary blocks to their operations parent
      // region!
      new Expression.ExpressionVisitor<E, T>(VisitOrder.POST_ORDER).visit(instantiated, e -> {
        var op = e.getUnderlyingOperation();
        if (op.isPresent() && !op.get().getTemporaryRegion().getBlocks().isEmpty()) {
          // Only try to move when the operation has its temporary region filled!
          // In case the parent region does not exist, it is invalid to move the child
          // blocks
          // to any position up the operation chain, hence, temporary region resolution is
          // invalid!
          var parentRegion = op.get().getParentRegionOrThrow();
          op.get().appendTemporaryBlocksToOtherRegion(parentRegion);
        }
      });

      if (instantiated.getUnderlyingOperation().isPresent()) {
        new OperationVerifier(VerifyOptions.FULL_VERIFICATION).verify(instantiated.getUnderlyingOperation().get());
      }

      return instantiated;
    }

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

    public abstract E asExpression(ExprOrOperator<E, T> exprOrOp);
  }

  /**
   * Return the static instance of a specific type inference solver for a type
   * system.
   *
   * @return the static instance of the algorithm specific
   *         {@link TypeInferenceSolver}
   */
  public abstract TypeInferenceSolver<E, T> getSolverInstance();

  /**
   * @return a list of allowed types to be used with the specific algorithm
   */
  public abstract List<Class<? extends Type>> getAllowedTypes();

  /**
   * @return a list of allowed expressions to be used with the specific algorithm
   */
  public abstract List<Class<? extends Expression<E, T>>> getAllowedExpressions();

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

package dgir.core.ir.types;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dgir.core.ir.Value;
import dgir.core.ir.types.algorithmw.Expr;

import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.traits.IAbstraction;
import dgir.core.ir.types.traits.IApplication;
import dgir.core.ir.types.traits.IVariable;

public class OperationExprConversionUtils {

  @SuppressWarnings("unchecked")
  public static <E extends Expression<E, T>, T extends Type> Optional<Symbol<E, T>> getOutputSymbol(E expr) {
    var assignedOp = expr.getUnderlyingOperation();

    if (assignedOp.isPresent() && assignedOp.get().getOutput().isPresent()) {
      return Optional.of(Symbol.of(assignedOp.get().getOutputValueOrThrow()));
    }

    if (expr instanceof IVariable) {
      return Optional.of(((IVariable<E, T>) expr).getReferencedVariable());
    }

    return Optional.empty();
  }

  public static <E extends Expression<E, T>, T extends Type> List<E> getAllChildrenForScopeExpression(E scopeExpr,
      E body) {
    record VisitState<E extends Expression<E, T>, T extends Type>(Set<E> withinThisBlock)
        implements dgir.core.ir.types.Expression.ExpressionVisitor.VisitState<E, T> {
    }
    var visitState = new VisitState<E, T>(Collections.newSetFromMap(new IdentityHashMap<>()));

    new Expression.ExpressionVisitor<E, T>(VisitOrder.IN_ORDER).visitWithState(body, e -> {
      var parentScopeExpr = e.getParentScopeExpr();
      if (parentScopeExpr.isPresent() && scopeExpr == parentScopeExpr.get()) {
        visitState.withinThisBlock.add(e);
      }
    }, visitState);

    ArrayList<E> exprsInBlock = new ArrayList<>(visitState.withinThisBlock);
    assert exprsInBlock.stream().allMatch(e -> e.getParentScopePosition().isPresent());

    exprsInBlock.sort((a, b) -> a.getParentScopePosition().get().compareTo(b.getParentScopePosition().get()));

    return List.copyOf(exprsInBlock);
  }

  @SuppressWarnings("unchecked")
  public static <AbsT extends IAbstraction<E, T>, E extends Expression<E, T>, T extends Type> List<Symbol<E, T>> getAllAbstractedParamters(
      AbsT expr) {

    ArrayList<Symbol<E, T>> params = new ArrayList<>();

    IAbstraction<E, T> current = expr;

    while (true) {
      var abstractedOver = current.getAbstractionsOverSymbols();

      if (abstractedOver.isEmpty()) {
        break;
      }

      params.addAll(abstractedOver);

      var body = current.getAbstractionBody();

      if (body instanceof IAbstraction) {
        current = (IAbstraction<E, T>) body;
      } else {
        break;
      }
    }

    return List.copyOf(params);
  }

  @SuppressWarnings("unchecked")
  public static <AppT extends IApplication<E, T>, E extends Expression<E, T>, T extends Type> List<E> getAllApplicationParameters(
      IApplication<E, T> expr) {

    ArrayDeque<E> params = new ArrayDeque<>();

    IApplication<E, T> current = expr;

    while (true) {

      var applicationParams = current.getApplications();

      // Prepend here, as nested function applications are in reverse order.
      // For example:
      // Func type: fn = a -> b -> c -> d
      // Func application ((((fn a) b) c) d)
      // Hence the outer most application actually contains parameter d, but a is
      // expected! This problem is mitigated a bit for multi paramter function
      // applications, but those cannot be guaranteed by all Type Systems.
      prependAll(params, applicationParams);

      var func = current.getFunction();
      if (func instanceof IApplication) {
        current = (IApplication<E, T>) func;
      } else {
        break;
      }
    }

    return List.copyOf(params);
  }

  private static <E> void prependAll(Deque<E> deque, List<E> chunk) {
    for (int i = chunk.size() - 1; i >= 0; i--) {
      deque.addFirst(chunk.get(i));
    }
  }

  /**
   * Returns the value of the expression's output symbol. Prefers the underlying
   * operation's result value and falls back to the referenced variable's symbol.
   *
   * @param expr the expression whose symbol value should be resolved.
   * @return the resolved value.
   */
  public static <E extends Expression<E, T>, T extends Type> Value getSymbolValue(E expr) {
    var symbol = getOutputSymbol(expr);
    assert symbol.isPresent();
    return symbol.get().getValue();
  }

  /**
   * Converts the fully specified inferred type of the given expression into its
   * concrete IR type representation.
   *
   * @param expr the expression whose inferred type should be converted.
   * @return the concrete IR type.
   */
  public static dgir.core.ir.Type inferredTypeToIrType(Expr expr) {
    var inferredType = expr.getInferredType();
    assert inferredType.isPresent();
    assert inferredType.get().isFullySpecified();
    return dgir.core.ir.Type.fromGeneralParameterizedNominalType(inferredType.get().asTypeParameter().getConcrete());
  }
}

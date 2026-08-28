package dgir.core.ir.types;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.traits.IIsAbstraction;
import dgir.core.ir.types.traits.IIsApplication;
import dgir.core.traits.IHasResult;

public class OperationExprConversionUtils {

  public static <E extends Expression<E, T>, T extends Type> Optional<Symbol<E, T>> getOutputSymbol(E expr) {
    var assignedOp = expr.getUnderlyingOperation();
    var referencedSymbol = expr.getReferencedVariable();

    if (assignedOp.isPresent() && assignedOp.get().asOp() instanceof IHasResult) {
      return Optional.of(Symbol.of(assignedOp.get().getOutputValueOrThrow()));
    }

    if (referencedSymbol.isPresent()) {
      return Optional.of(referencedSymbol.get());
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
  public static <AbsT extends IIsAbstraction<E, T>, E extends Expression<E, T>, T extends Type> List<Symbol<E, T>> getAllAbstractedParamters(
      AbsT expr) {

    ArrayList<Symbol<E, T>> params = new ArrayList<>();

    IIsAbstraction<E, T> current = expr;

    while (true) {
      var abstractedOver = current.getAbstractionsOverSymbols();

      if (abstractedOver.isEmpty()) {
        break;
      }

      params.addAll(abstractedOver);

      var body = current.getAbstractionBody();

      if (body instanceof IIsAbstraction) {
        current = (IIsAbstraction<E, T>) body;
      } else {
        break;
      }
    }

    return List.copyOf(params);
  }

  @SuppressWarnings("unchecked")
  public static <AppT extends IIsApplication<E, T>, E extends Expression<E, T>, T extends Type> List<E> getAllApplicationParameters(
      IIsApplication<E, T> expr) {

    ArrayDeque<E> params = new ArrayDeque<>();

    IIsApplication<E, T> current = expr;

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
      if (func instanceof IIsApplication) {
        current = (IIsApplication<E, T>) func;
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
}

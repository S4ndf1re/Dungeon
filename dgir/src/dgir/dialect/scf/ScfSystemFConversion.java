package dgir.dialect.scf;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFInference;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.expr.Abs;
import dgir.core.ir.types.builtin.systemf.expr.App;
import dgir.core.ir.types.builtin.systemf.expr.If;
import dgir.core.ir.types.builtin.systemf.expr.Let;
import dgir.core.ir.types.builtin.systemf.expr.Var;
import dgir.core.ir.types.builtin.systemf.expr.While;

public final class ScfSystemFConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinSystemFConversion() {
    ConverterRegistry.<Expr, SystemFType, TypeInference>addOperatorsToDialect(
        SystemFInference.class,
        Pair.of(ScfOps.IfOp.class, ScfSystemFConversion::convertIfOp),
        Pair.of(ScfOps.ScopeOp.class, ScfSystemFConversion::convertScopeOp),
        Pair.of(ScfOps.ForOp.class, ScfSystemFConversion::convertForOp),
        Pair.of(ScfOps.WhileOp.class, ScfSystemFConversion::convertWhileOp),
        Pair.of(ScfOps.SelectOp.class, ScfSystemFConversion::convertSelectOp),
        Pair.of(ScfOps.YieldOp.class, ScfSystemFConversion::convertYieldOp),
        Pair.of(ScfOps.ContinueOp.class, ScfSystemFConversion::convertContinueOp),
        Pair.of(ScfOps.EndOp.class, ScfSystemFConversion::convertEndOp));
  }

  /** Peels nested unary applications down to the head expression. */
  private static Expr peelApplication(Expr expr) {
    var current = expr;
    while (current instanceof App app && app.arg().isPresent()) {
      current = app.fun();
    }
    return current;
  }

  public static Expr convertIfOp(
      Operation op,
      TypeInference engine) {

    ScfOps.IfOp ifOp = (ScfOps.IfOp) op.asOp();

    Expr condExpr = new Var(ifOp.getLocation(), Symbol.<Expr, SystemFType>of(ifOp.getOperandValue(0).orElseThrow()));
    Expr thenCase = OperationExprConversionUtils.regionToExpr(engine, ifOp.getThenRegion());
    Optional<Expr> elseCase = ifOp.getElseRegion()
        .map(region -> OperationExprConversionUtils.regionToExpr(engine, region));

    var result = elseCase
        .map(elseExpr -> new If(ifOp.getLocation(), condExpr, thenCase, elseExpr))
        .orElseGet(() -> new If(ifOp.getLocation(), condExpr, thenCase));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof If;

      var ifExpr = (If) instantiatedExpr;

      var condValue = ifExpr.cond.getOutputValue();
      boolean withElse = ifExpr.elseExpr.isPresent();

      ScfOps.IfOp newIf;
      if (op.getOutput().isPresent()) {
        newIf = new ScfOps.IfOp(op.getLocation(), condValue, withElse,
            ifExpr.inferredTypeToIrType());
      } else {
        newIf = new ScfOps.IfOp(op.getLocation(), condValue, withElse);
      }
      var newOp = newIf.getOperation();

      ifExpr.thenExpr.fillOpScoped(newOp, 0);
      if (withElse) {
        ifExpr.elseExpr.get().fillOpScoped(newOp, 1);
      }

      return newOp;
    });

    return result;
  }

  public static Expr convertScopeOp(
      Operation op,
      TypeInference engine) {

    ScfOps.ScopeOp scopeOp = (ScfOps.ScopeOp) op.asOp();

    var body = OperationExprConversionUtils.regionToExpr(engine, scopeOp.getRegion());

    body.setInstantiateOperationCallback(instantiatedExpr -> {
      var newScope = new ScfOps.ScopeOp(op.getLocation());
      var newOp = newScope.getOperation();

      instantiatedExpr.fillOpScoped(newOp, 0);

      return newOp;
    });

    return body;
  }

  public static Expr convertForOp(
      Operation op,
      TypeInference engine) {
    ScfOps.ForOp forOp = (ScfOps.ForOp) op.asOp();

    // NOTE: the induction parameter must be LAST: the application only passes
    // four arguments (init, lower, upper, step), leaving the induction
    // parameter as the unapplied tail of a partially applied function.
    List<Symbol<Expr, SystemFType>> params = List.of(
        Symbol.<Expr, SystemFType>of(forOp.getInitialValue()),
        Symbol.<Expr, SystemFType>of(forOp.getLowerBound()),
        Symbol.<Expr, SystemFType>of(forOp.getUpperBound()),
        Symbol.<Expr, SystemFType>of(forOp.getStep()),
        Symbol.<Expr, SystemFType>of(forOp.getInductionValue()));

    var loc = forOp.getLocation();
    var result = engine.App(
        loc,
        engine.Abs(loc, params, OperationExprConversionUtils.regionToExpr(engine, forOp.getRegion())),
        List.of(
            engine.Var(loc, Symbol.<Expr, SystemFType>of(forOp.getInitialValue())),
            engine.Var(loc, Symbol.<Expr, SystemFType>of(forOp.getLowerBound())),
            engine.Var(loc, Symbol.<Expr, SystemFType>of(forOp.getUpperBound())),
            engine.Var(loc, Symbol.<Expr, SystemFType>of(forOp.getStep()))));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof App;

      var argResults = ((App) instantiatedExpr).getAllApplicationParameters().stream()
          .map(Expr::getOutputValue).toList();
      assert argResults.size() == 4;

      var newForOp = new ScfOps.ForOp(op.getLocation(), argResults.get(0), argResults.get(1), argResults.get(2),
          argResults.get(3));
      var newOp = newForOp.getOperation();

      var head = peelApplication(instantiatedExpr);
      assert head instanceof Abs;
      // engine.Abs chains one unary Abs per parameter; unwrap to the actual
      // region body.
      var bodyExpr = ((Abs) head).body();
      while (bodyExpr instanceof Abs nested) {
        bodyExpr = nested.body();
      }
      if (bodyExpr instanceof Let let) {
        // Region expressions are Let-scoped blocks in System F; fill the
        // region's entry block from the Let bindings.
        let.fillBlockScoped(newForOp.getRegion().getEntryBlock());
      } else {
        bodyExpr.fillOpScoped(newOp, 0);
      }

      var newRegion = newForOp.getRegion();
      if (forOp.getInductionValue() != newForOp.getInductionValue()) {
        forOp.getInductionValue().replaceAllUsesIn(newForOp.getInductionValue(), newRegion);
      }

      for (int i = 0; i < 4; i++) {
        var oldValue = List.of(forOp.getInitialValue(), forOp.getLowerBound(), forOp.getUpperBound(),
            forOp.getStep()).get(i);
        if (oldValue != argResults.get(i)) {
          oldValue.replaceAllUsesIn(argResults.get(i), newRegion);
        }
      }

      return newOp;
    });

    return result;
  }

  public static Expr convertWhileOp(
      Operation op,
      TypeInference engine) {

    ScfOps.WhileOp whileOp = (ScfOps.WhileOp) op.asOp();

    var result = new While(whileOp.getLocation(),
        OperationExprConversionUtils.regionToExpr(engine, whileOp.getConditionRegion()),
        OperationExprConversionUtils.regionToExpr(engine, whileOp.getBodyRegion()));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof While;

      var whileExpr = (While) instantiatedExpr;

      var newWhile = new ScfOps.WhileOp(op.getLocation());
      var newOp = newWhile.getOperation();

      whileExpr.cond.fillOpScoped(newOp, 0);
      whileExpr.body.fillOpScoped(newOp, 1);

      return newOp;
    });

    return result;
  }

  public static Expr convertSelectOp(
      Operation op,
      TypeInference engine) {

    ScfOps.SelectOp selectOp = (ScfOps.SelectOp) op.asOp();

    Expr cond = new Var(selectOp.getLocation(), Symbol.<Expr, SystemFType>of(selectOp.getCondition()));
    Expr thenExpr = new Var(selectOp.getLocation(), Symbol.<Expr, SystemFType>of(selectOp.getTrue()));
    Expr elseExpr = new Var(selectOp.getLocation(), Symbol.<Expr, SystemFType>of(selectOp.getFalse()));

    var result = new If(selectOp.getLocation(), cond, thenExpr, elseExpr);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof If;

      var ifExpr = (If) instantiatedExpr;

      return new ScfOps.SelectOp(op.getLocation(),
          ifExpr.cond.getOutputValue(),
          ifExpr.thenExpr.getOutputValue(),
          ifExpr.elseExpr.get().getOutputValue()).getOperation();
    });

    return result;
  }

  public static Expr convertYieldOp(
      Operation op,
      TypeInference engine) {

    ScfOps.YieldOp yieldOp = (ScfOps.YieldOp) op.asOp();

    Expr yieldExpr = new Var(yieldOp.getLocation(), Symbol.<Expr, SystemFType>of(yieldOp.getOperand()));

    var result = OperationExprConversionUtils.buildIdentityApplication(engine, yieldExpr, yieldOp.getLocation());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof App;
      var argResults = ((App) instantiatedExpr).getAllApplicationParameters();

      return new ScfOps.YieldOp(op.getLocation(),
          argResults.getFirst().getOutputValue()).getOperation();
    });

    return result;
  }

  public static Expr convertContinueOp(
      Operation op,
      TypeInference engine) {

    var result = engine.Lit(op.getLocation(), new Literal.Unit());

    result.setInstantiateOperationCallback(instantiatedExpr -> new ScfOps.ContinueOp(op.getLocation()).getOperation());

    return result;
  }

  public static Expr convertEndOp(
      Operation op,
      TypeInference engine) {

    var result = engine.Lit(op.getLocation(), new Literal.Unit());

    result.setInstantiateOperationCallback(instantiatedExpr -> new ScfOps.EndOp(op.getLocation()).getOperation());

    return result;
  }
}

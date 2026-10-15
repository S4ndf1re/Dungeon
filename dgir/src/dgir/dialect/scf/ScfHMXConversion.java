package dgir.dialect.scf;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.expr.Abs;
import dgir.core.ir.types.builtin.hmx.expr.App;
import dgir.core.ir.types.builtin.hmx.expr.If;
import dgir.core.ir.types.builtin.hmx.expr.Lit;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.builtin.hmx.expr.Var;
import dgir.core.ir.types.builtin.hmx.expr.While;
import dgir.core.ir.types.compatibility.ConverterRegistry;

public final class ScfHMXConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinHMXConversion() {
    ConverterRegistry.<HMXExpr, HMXType, TypeInference>addOperatorsToDialect(HMXInference.class,
        Pair.of(ScfOps.IfOp.class, ScfHMXConversion::convertIfOp),
        Pair.of(ScfOps.ScopeOp.class, ScfHMXConversion::convertScopeOp),
        Pair.of(ScfOps.ForOp.class, ScfHMXConversion::convertForOp),
        Pair.of(ScfOps.WhileOp.class, ScfHMXConversion::convertWhileOp),
        Pair.of(ScfOps.SelectOp.class, ScfHMXConversion::convertSelectOp),
        Pair.of(ScfOps.YieldOp.class, ScfHMXConversion::convertYieldOp),
        Pair.of(ScfOps.ContinueOp.class, ScfHMXConversion::convertContinueOp),
        Pair.of(ScfOps.EndOp.class, ScfHMXConversion::convertEndOp));

  }

  public static HMXExpr convertIfOp(
      Operation op,
      TypeInference engine) {

    ScfOps.IfOp ifOp = (ScfOps.IfOp) op.asOp();

    HMXExpr condExpr = new Var(ifOp.getLocation(), Symbol.<HMXExpr, HMXType>of(ifOp.getOperandValue(0).orElseThrow()));
    HMXExpr thenCase = OperationExprConversionUtils.regionToExpr(engine, ifOp.getThenRegion());
    Optional<HMXExpr> elseCase = ifOp.getElseRegion()
        .map(region -> OperationExprConversionUtils.regionToExpr(engine, region));

    var result = new If(ifOp.getLocation(), condExpr, thenCase, elseCase);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof If;

      var custExpr = (If) instantiatedExpr;

      var condValue = custExpr.cond.getOutputValue();
      boolean withElse = custExpr.elseExpr.isPresent();

      ScfOps.IfOp newIf;
      if (op.getOutput().isPresent()) {
        newIf = new ScfOps.IfOp(op.getLocation(), condValue, withElse,
            custExpr.inferredTypeToIrType());
      } else {
        newIf = new ScfOps.IfOp(op.getLocation(), condValue, withElse);
      }
      var newOp = newIf.getOperation();

      custExpr.thenExpr.fillOpScoped(newOp, 0);
      if (withElse) {
        custExpr.elseExpr.get().fillOpScoped(newOp, 1);
      }

      return newOp;
    });

    return result;
  }

  public static HMXExpr convertScopeOp(
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

  public static HMXExpr convertForOp(
      Operation op,
      TypeInference engine) {
    ScfOps.ForOp forOp = (ScfOps.ForOp) op.asOp();

    // NOTE: the induction parameter must be LAST: the application only passes
    // four arguments (init, lower, upper, step) and ExprApp.infer unifies the
    // arrow types positionally. Leading with the induction value would pair it
    // with the initial accumulator value; trailing it leaves the induction
    // parameter as the unapplied tail of a partially applied function.
    List<Symbol<HMXExpr, HMXType>> params = List.of(
        Symbol.<HMXExpr, HMXType>of(forOp.getInitialValue()),
        Symbol.<HMXExpr, HMXType>of(forOp.getLowerBound()),
        Symbol.<HMXExpr, HMXType>of(forOp.getUpperBound()),
        Symbol.<HMXExpr, HMXType>of(forOp.getStep()),
        Symbol.<HMXExpr, HMXType>of(forOp.getInductionValue()));

    var loc = forOp.getLocation();
    var result = new App(
        loc,
        new Abs(loc, params, OperationExprConversionUtils.regionToExpr(engine, forOp.getRegion())),
        List.of(
            new Var(loc, Symbol.<HMXExpr, HMXType>of(forOp.getInitialValue())),
            new Var(loc, Symbol.<HMXExpr, HMXType>of(forOp.getLowerBound())),
            new Var(loc, Symbol.<HMXExpr, HMXType>of(forOp.getUpperBound())),
            new Var(loc, Symbol.<HMXExpr, HMXType>of(forOp.getStep()))));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof App;

      var app = (App) instantiatedExpr;
      assert app.args().size() == 4;

      List<Value> argResults = app.args().stream()
          .map(e -> e.getOutputValue()).toList();

      var newForOp = new ScfOps.ForOp(op.getLocation(), argResults.get(0), argResults.get(1), argResults.get(2),
          argResults.get(3));
      var newOp = newForOp.getOperation();

      assert app.func() instanceof Abs;
      var instantiatedAbs = (Abs) app.func();
      instantiatedAbs.body().fillOpScoped(newOp, 0);

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

  public static HMXExpr convertWhileOp(
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

  public static HMXExpr convertSelectOp(
      Operation op,
      TypeInference engine) {

    ScfOps.SelectOp selectOp = (ScfOps.SelectOp) op.asOp();

    HMXExpr cond = new Var(selectOp.getLocation(), Symbol.<HMXExpr, HMXType>of(selectOp.getCondition()));
    HMXExpr thenExpr = new Var(selectOp.getLocation(), Symbol.<HMXExpr, HMXType>of(selectOp.getTrue()));
    HMXExpr elseExpr = new Var(selectOp.getLocation(), Symbol.<HMXExpr, HMXType>of(selectOp.getFalse()));

    var result = new If(selectOp.getLocation(), cond, thenExpr, elseExpr);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof If;

      var custExpr = (If) instantiatedExpr;

      return new ScfOps.SelectOp(op.getLocation(),
          custExpr.cond.getOutputValue(),
          custExpr.thenExpr.getOutputValue(),
          custExpr.elseExpr.get().getOutputValue()).getOperation();
    });

    return result;
  }

  public static HMXExpr convertYieldOp(
      Operation op,
      TypeInference engine) {

    ScfOps.YieldOp yieldOp = (ScfOps.YieldOp) op.asOp();

    HMXExpr yieldExpr = new Var(yieldOp.getLocation(), Symbol.of(yieldOp.getOperand()));

    var result = OperationExprConversionUtils.buildIdentityApplication(engine, yieldExpr, yieldOp.getLocation());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      var app = (App) instantiatedExpr;

      return new ScfOps.YieldOp(op.getLocation(),
          app.args().getFirst().getOutputValue()).getOperation();
    });

    return result;
  }

  public static HMXExpr convertContinueOp(
      Operation op,
      TypeInference engine) {

    var result = new Lit(op.getLocation(), new Literal.Unit());

    result.setInstantiateOperationCallback(instantiatedExpr -> new ScfOps.ContinueOp(op.getLocation()).getOperation());

    return result;
  }

  public static HMXExpr convertEndOp(
      Operation op,
      TypeInference engine) {

    var result = new Lit(op.getLocation(), new Literal.Unit());

    result.setInstantiateOperationCallback(instantiatedExpr -> new ScfOps.EndOp(op.getLocation()).getOperation());

    return result;
  }
}

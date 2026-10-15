package dgir.dialect.scf;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.algorithmw.expr.Abs;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.expr.App;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.expr.If;
import dgir.core.ir.types.builtin.algorithmw.expr.Lit;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;
import dgir.core.ir.types.builtin.algorithmw.expr.Var;
import dgir.core.ir.types.builtin.algorithmw.expr.While;
import dgir.core.ir.types.compatibility.ConverterRegistry;

public final class ScfAlgoWConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<Expr, AlgorithmWType, TypeInference>addOperatorsToDialect(
        AlgorithmWInference.class,
        Pair.of(ScfOps.IfOp.class, ScfAlgoWConversion::convertIfOp),
        Pair.of(ScfOps.ScopeOp.class, ScfAlgoWConversion::convertScopeOp),
        Pair.of(ScfOps.ForOp.class, ScfAlgoWConversion::convertForOp),
        Pair.of(ScfOps.WhileOp.class, ScfAlgoWConversion::convertWhileOp),
        Pair.of(ScfOps.SelectOp.class, ScfAlgoWConversion::convertSelectOp),
        Pair.of(ScfOps.YieldOp.class, ScfAlgoWConversion::convertYieldOp),
        Pair.of(ScfOps.ContinueOp.class, ScfAlgoWConversion::convertContinueOp),
        Pair.of(ScfOps.EndOp.class, ScfAlgoWConversion::convertEndOp));

  }

  public static Expr convertIfOp(
      Operation op,
      TypeInference engine) {

    ScfOps.IfOp ifOp = (ScfOps.IfOp) op.asOp();

    Expr condExpr = new Var(op.getLocation(), Symbol.of(ifOp.getOperandValue(0).orElseThrow()));
    Expr thenCase = OperationExprConversionUtils.regionToExpr(engine, ifOp.getThenRegion());
    Optional<Expr> elseCase = ifOp.getElseRegion()
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
    // four arguments (init, lower, upper, step) and ExprApp.infer unifies the
    // arrow types positionally. Leading with the induction value would pair it
    // with the initial accumulator value; trailing it leaves the induction
    // parameter as the unapplied tail of a partially applied function.
    List<Symbol<Expr, AlgorithmWType>> params = List.of(
        Symbol.of(forOp.getInitialValue()),
        Symbol.of(forOp.getLowerBound()),
        Symbol.of(forOp.getUpperBound()),
        Symbol.of(forOp.getStep()),
        Symbol.of(forOp.getInductionValue()));

    var loc = op.getLocation();
    var result = new App(
        loc,
        new Abs(loc, params, OperationExprConversionUtils.regionToExpr(engine, forOp.getRegion())),
        List.of(
            new Var(loc, Symbol.of(forOp.getInitialValue())),
            new Var(loc, Symbol.of(forOp.getLowerBound())),
            new Var(loc, Symbol.of(forOp.getUpperBound())),
            new Var(loc, Symbol.of(forOp.getStep()))));

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

  public static Expr convertWhileOp(
      Operation op,
      TypeInference engine) {

    ScfOps.WhileOp whileOp = (ScfOps.WhileOp) op.asOp();

    Expr cond = OperationExprConversionUtils.regionToExpr(engine, whileOp.getConditionRegion());
    Expr body = OperationExprConversionUtils.regionToExpr(engine, whileOp.getBodyRegion());

    var result = new While(whileOp.getLocation(), cond, body);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof While;

      var custExpr = (While) instantiatedExpr;

      var newWhile = new ScfOps.WhileOp(op.getLocation());
      var newOp = newWhile.getOperation();

      custExpr.cond.fillOpScoped(newOp, 0);
      custExpr.body.fillOpScoped(newOp, 1);

      return newOp;
    });

    return result;
  }

  public static Expr convertSelectOp(
      Operation op,
      TypeInference engine) {

    ScfOps.SelectOp selectOp = (ScfOps.SelectOp) op.asOp();

    Expr cond = new Var(selectOp.getLocation(), Symbol.of(selectOp.getCondition()));
    Expr thenExpr = new Var(selectOp.getLocation(), Symbol.of(selectOp.getTrue()));
    Expr elseExpr = new Var(selectOp.getLocation(), Symbol.of(selectOp.getFalse()));

    var result = new If(selectOp.getLocation(), cond, thenExpr, elseExpr);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof If;

      var custExpr = (If) instantiatedExpr;
      assert custExpr.elseExpr.isPresent();

      return new ScfOps.SelectOp(op.getLocation(),
          custExpr.cond.getOutputValue(),
          custExpr.thenExpr.getOutputValue(),
          custExpr.elseExpr.get().getOutputValue()).getOperation();
    });

    return result;
  }

  public static Expr convertYieldOp(
      Operation op,
      TypeInference engine) {

    ScfOps.YieldOp yieldOp = (ScfOps.YieldOp) op.asOp();

    Expr yieldExpr = new Var(yieldOp.getLocation(), Symbol.of(yieldOp.getOperand()));

    var result = OperationExprConversionUtils.buildIdentityApplication(engine, yieldExpr, yieldOp.getLocation());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      var app = (App) instantiatedExpr;

      return new ScfOps.YieldOp(op.getLocation(),
          app.args().getFirst().getOutputValue()).getOperation();
    });

    return result;
  }

  public static Expr convertContinueOp(
      Operation op,
      TypeInference engine) {

    var result = new Lit(op.getLocation(), new Literal.Unit());

    result.setInstantiateOperationCallback(instantiatedExpr -> new ScfOps.ContinueOp(op.getLocation()).getOperation());

    return result;
  }

  public static Expr convertEndOp(
      Operation op,
      TypeInference engine) {

    var result = new Lit(op.getLocation(), new Literal.Unit());

    result.setInstantiateOperationCallback(instantiatedExpr -> new ScfOps.EndOp(op.getLocation()).getOperation());

    return result;
  }
}

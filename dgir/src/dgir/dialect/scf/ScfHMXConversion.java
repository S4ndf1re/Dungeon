package dgir.dialect.scf;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXExpr.ExprCustom.GenerateFunction;
import dgir.core.ir.types.builtin.hmx.HMXExpr.ExprCustom.GetChildrenFunction;
import dgir.core.ir.types.builtin.hmx.HMXExpr.ExprCustom.InstantiateFunction;
import dgir.core.ir.types.builtin.hmx.HMXExpr.ExprCustom.ReplaceSymbolFunction;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;

public final class ScfHMXConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
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

    record IfData(HMXExpr cond, HMXExpr thenCase, Optional<HMXExpr> elseCase) {
    }

    ScfOps.IfOp ifOp = (ScfOps.IfOp) op.asOp();

    var condExpr = new HMXExpr.ExprVar(Symbol.<HMXExpr, HMXType>of(ifOp.getOperandValue(0).orElseThrow()));
    var thenCase = OperationExprConversionUtils.regionToExpr(engine, ifOp.getThenRegion());
    Optional<HMXExpr> elseCase = ifOp.getElseRegion()
        .map(region -> OperationExprConversionUtils.regionToExpr(engine, region));
    var ifData = new IfData(condExpr, thenCase, elseCase);

    GenerateFunction<IfData> infFunc = (eng, env, type, data) -> {

      var condTyVar = new TypeVar<HMXType>();
      var thenTyVar = new TypeVar<HMXType>();
      var condType = new HMXType.Var(condTyVar);
      var thenType = new HMXType.Var(thenTyVar);
      TypeVar<HMXType> elseTyVar = null;
      GenerateResult resCond = eng.generate(data.cond(), env, condType);
      GenerateResult resThen = eng.generate(data.thenCase(), env, thenType);

      var constraints = new ArrayList<>(List.of(resCond.constr(), resThen.constr(),
          new Constraint.Equal(condType, new HMXType.LitType(TypeIdent.TYPE_IDENT_BOOL))));

      if (data.elseCase().isPresent()) {
        elseTyVar = new TypeVar<HMXType>();
        var elseType = new HMXType.Var(elseTyVar);
        GenerateResult resElse = eng.generate(data.elseCase().get(), env, elseType);
        constraints.add(resElse.constr());
        constraints.add(new Constraint.Equal(thenType, elseType));
      }

      if (op.getOutput().isPresent()) {
        constraints.add(new Constraint.Equal(thenType, type));
      } else {
        // An if without results is only well-typed when both branches are unit.
        constraints.add(new Constraint.Equal(thenType, new HMXType.LitType(TypeIdent.TYPE_IDENT_UNIT)));
        constraints.add(new Constraint.Equal(type, new HMXType.LitType(TypeIdent.TYPE_IDENT_UNIT)));
      }

      var quantified = new ArrayList<TypeVar<HMXType>>(List.of(condTyVar, thenTyVar));
      if (elseTyVar != null) {
        quantified.add(elseTyVar);
      }
      return new Constraint.Exists(quantified, new Constraint.And(constraints));
    };

    GetChildrenFunction<IfData> getChildrenFn = (data) -> {
      if (data.elseCase().isPresent()) {
        return List.of(data.cond(), data.thenCase(), data.elseCase().get());
      } else {
        return List.of(data.cond(), data.thenCase());
      }
    };

    InstantiateFunction<IfData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new HMXExpr.ExprCustom<IfData>(toInstantiate, new IfData(data.cond().instantiate(eng, env, solution),
          data.thenCase().instantiate(eng, env, solution),
          data.elseCase().map(elseExpr -> elseExpr.instantiate(eng, env, solution))));
    };

    ReplaceSymbolFunction<IfData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new HMXExpr.ExprCustom<IfData>(oldExpr, new IfData(data.cond().replaceSymbol(original, replacement),
          data.thenCase().replaceSymbol(original, replacement),
          data.elseCase().map(elseExpr -> elseExpr.replaceSymbol(original, replacement))));
    };

    var result = new HMXExpr.ExprCustom<IfData>(ifData, infFunc, instFn, getChildrenFn, replaceSymbolFn);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (HMXExpr.ExprCustom<IfData>) instantiatedExpr;
      var data = custExpr.getData();

      var condValue = data.cond().getOutputValue();
      boolean withElse = data.elseCase().isPresent();

      ScfOps.IfOp newIf;
      if (op.getOutput().isPresent()) {
        newIf = new ScfOps.IfOp(op.getLocation(), condValue, withElse,
            custExpr.inferredTypeToIrType());
      } else {
        newIf = new ScfOps.IfOp(op.getLocation(), condValue, withElse);
      }
      var newOp = newIf.getOperation();

      data.thenCase.fillOpScoped(newOp, 0);
      if (withElse) {
        data.elseCase.get().fillOpScoped(newOp, 1);
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

    var result = new HMXExpr.ExprApp(
        new HMXExpr.ExprAbs(params, OperationExprConversionUtils.regionToExpr(engine, forOp.getRegion())),
        List.of(
            new HMXExpr.ExprVar(Symbol.<HMXExpr, HMXType>of(forOp.getInitialValue())),
            new HMXExpr.ExprVar(Symbol.<HMXExpr, HMXType>of(forOp.getLowerBound())),
            new HMXExpr.ExprVar(Symbol.<HMXExpr, HMXType>of(forOp.getUpperBound())),
            new HMXExpr.ExprVar(Symbol.<HMXExpr, HMXType>of(forOp.getStep()))));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprApp;

      var app = (HMXExpr.ExprApp) instantiatedExpr;
      assert app.args().size() == 4;

      List<Value> argResults = app.args().stream()
          .map(e -> e.getOutputValue()).toList();

      var newForOp = new ScfOps.ForOp(op.getLocation(), argResults.get(0), argResults.get(1), argResults.get(2),
          argResults.get(3));
      var newOp = newForOp.getOperation();

      assert app.func() instanceof HMXExpr.ExprAbs;
      var instantiatedAbs = (HMXExpr.ExprAbs) app.func();
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

    record WhileData(HMXExpr cond, HMXExpr body) {
    }

    ScfOps.WhileOp whileOp = (ScfOps.WhileOp) op.asOp();

    var whileData = new WhileData(
        OperationExprConversionUtils.regionToExpr(engine, whileOp.getConditionRegion()),
        OperationExprConversionUtils.regionToExpr(engine, whileOp.getBodyRegion()));

    GenerateFunction<WhileData> infFunc = (eng, env, type, data) -> {

      var condTyVar = new TypeVar<HMXType>();
      var bodyTyVar = new TypeVar<HMXType>();
      var condType = new HMXType.Var(condTyVar);
      var bodyType = new HMXType.Var(bodyTyVar);

      GenerateResult resCond = eng.generate(data.cond(), env, condType);
      GenerateResult resBody = eng.generate(data.body(), env, bodyType);

      return new Constraint.Exists(List.of(condTyVar, bodyTyVar),
          new Constraint.And(resCond.constr(), resBody.constr(),
              new Constraint.Equal(condType, new HMXType.LitType(TypeIdent.TYPE_IDENT_BOOL)),
              new Constraint.Equal(bodyType, new HMXType.LitType(TypeIdent.TYPE_IDENT_UNIT)),
              new Constraint.Equal(type, new HMXType.LitType(TypeIdent.TYPE_IDENT_UNIT))));
    };

    GetChildrenFunction<WhileData> getChildrenFn = (data) -> {
      return List.of(data.cond(), data.body());
    };

    InstantiateFunction<WhileData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new HMXExpr.ExprCustom<WhileData>(toInstantiate, new WhileData(
          data.cond().instantiate(eng, env, solution), data.body().instantiate(eng, env, solution)));
    };

    ReplaceSymbolFunction<WhileData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new HMXExpr.ExprCustom<WhileData>(oldExpr, new WhileData(
          data.cond().replaceSymbol(original, replacement), data.body().replaceSymbol(original, replacement)));
    };

    var result = new HMXExpr.ExprCustom<WhileData>(whileData, infFunc, instFn, getChildrenFn, replaceSymbolFn);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (HMXExpr.ExprCustom<WhileData>) instantiatedExpr;
      var data = custExpr.getData();

      var newWhile = new ScfOps.WhileOp(op.getLocation());
      var newOp = newWhile.getOperation();

      data.cond().fillOpScoped(newOp, 0);
      data.body().fillOpScoped(newOp, 1);

      return newOp;
    });

    return result;
  }

  public static HMXExpr convertSelectOp(
      Operation op,
      TypeInference engine) {

    record SelectData(HMXExpr cond, HMXExpr trueVal, HMXExpr falseVal) {
    }

    ScfOps.SelectOp selectOp = (ScfOps.SelectOp) op.asOp();

    var selectData = new SelectData(
        new HMXExpr.ExprVar(Symbol.<HMXExpr, HMXType>of(selectOp.getCondition())),
        new HMXExpr.ExprVar(Symbol.<HMXExpr, HMXType>of(selectOp.getTrue())),
        new HMXExpr.ExprVar(Symbol.<HMXExpr, HMXType>of(selectOp.getFalse())));

    GenerateFunction<SelectData> infFunc = (eng, env, type, data) -> {

      var condTyVar = new TypeVar<HMXType>();
      var trueTyVar = new TypeVar<HMXType>();
      var falseTyVar = new TypeVar<HMXType>();
      var condType = new HMXType.Var(condTyVar);
      var trueType = new HMXType.Var(trueTyVar);
      var falseType = new HMXType.Var(falseTyVar);

      GenerateResult resCond = eng.generate(data.cond(), env, condType);
      GenerateResult resTrue = eng.generate(data.trueVal(), env, trueType);
      GenerateResult resFalse = eng.generate(data.falseVal(), env, falseType);

      var resultType = trueType;

      return new Constraint.Exists(List.of(condTyVar, trueTyVar, falseTyVar),
          new Constraint.And(resCond.constr(), resTrue.constr(), resFalse.constr(),
              new Constraint.Equal(condType, new HMXType.LitType(TypeIdent.TYPE_IDENT_BOOL)),
              new Constraint.Equal(trueType, falseType),
              new Constraint.Equal(trueType, type)));
    };

    GetChildrenFunction<SelectData> getChildrenFn = (data) -> {
      return List.of(data.cond(), data.trueVal(), data.falseVal());
    };

    InstantiateFunction<SelectData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new HMXExpr.ExprCustom<SelectData>(toInstantiate, new SelectData(
          data.cond().instantiate(eng, env, solution),
          data.trueVal().instantiate(eng, env, solution),
          data.falseVal().instantiate(eng, env, solution)));
    };

    ReplaceSymbolFunction<SelectData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new HMXExpr.ExprCustom<SelectData>(oldExpr, new SelectData(
          data.cond().replaceSymbol(original, replacement),
          data.trueVal().replaceSymbol(original, replacement),
          data.falseVal().replaceSymbol(original, replacement)));
    };

    var result = new HMXExpr.ExprCustom<SelectData>(selectData, infFunc, instFn, getChildrenFn, replaceSymbolFn);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (HMXExpr.ExprCustom<SelectData>) instantiatedExpr;
      var data = custExpr.getData();

      return new ScfOps.SelectOp(op.getLocation(),
          data.cond().getOutputValue(),
          data.trueVal().getOutputValue(),
          data.falseVal().getOutputValue()).getOperation();
    });

    return result;
  }

  public static HMXExpr convertYieldOp(
      Operation op,
      TypeInference engine) {

    record YieldData(HMXExpr value) {
    }

    ScfOps.YieldOp yieldOp = (ScfOps.YieldOp) op.asOp();

    var yieldData = new YieldData(
        new HMXExpr.ExprVar(Symbol.<HMXExpr, HMXType>of(yieldOp.getOperand())));

    GenerateFunction<YieldData> infFunc = (eng, env, type, data) -> {
      var valueTyVar = new TypeVar<HMXType>();
      var valueType = new HMXType.Var(valueTyVar);
      GenerateResult resValue = eng.generate(data.value(), env, valueType);

      return new Constraint.Exists(List.of(valueTyVar),
          new Constraint.And(resValue.constr(), new Constraint.Equal(valueType, type)));
    };

    GetChildrenFunction<YieldData> getChildrenFn = (data) -> {
      return List.of(data.value);
    };

    InstantiateFunction<YieldData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new HMXExpr.ExprCustom<YieldData>(toInstantiate,
          new YieldData(data.value.instantiate(eng, env, solution)));
    };

    ReplaceSymbolFunction<YieldData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new HMXExpr.ExprCustom<YieldData>(oldExpr, new YieldData(
          data.value.replaceSymbol(original, replacement)));
    };

    var result = new HMXExpr.ExprCustom<YieldData>(yieldData, infFunc, instFn, getChildrenFn, replaceSymbolFn);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (HMXExpr.ExprCustom<YieldData>) instantiatedExpr;
      var data = custExpr.getData();

      return new ScfOps.YieldOp(op.getLocation(),
          data.value().getOutputValue()).getOperation();
    });

    return result;
  }

  public static HMXExpr convertContinueOp(
      Operation op,
      TypeInference engine) {

    record JumpData() {
    }

    GenerateFunction<JumpData> infFunc = (eng, env, type, data) -> new Constraint.Trivial();

    var result = new HMXExpr.ExprCustom<JumpData>(new JumpData(), infFunc, null, null, null);

    result.setInstantiateOperationCallback(instantiatedExpr -> new ScfOps.ContinueOp(op.getLocation()).getOperation());

    return result;
  }

  public static HMXExpr convertEndOp(
      Operation op,
      TypeInference engine) {

    record EndData() {
    }

    GenerateFunction<EndData> infFunc = (eng, env, type, data) -> new Constraint.Trivial();

    var result = new HMXExpr.ExprCustom<EndData>(new EndData(), infFunc, null, null, null);

    result.setInstantiateOperationCallback(instantiatedExpr -> new ScfOps.EndOp(op.getLocation()).getOperation());

    return result;
  }
}

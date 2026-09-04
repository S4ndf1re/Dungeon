package dgir.dialect.scf;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Block;
import dgir.core.ir.Operation;
import dgir.core.ir.Region;
import dgir.core.ir.Value;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.algorithmw.AlgorithmWType;
import dgir.core.ir.types.algorithmw.Expr;
import dgir.core.ir.types.algorithmw.Expr.ExprCustom.GetChildrenFunction;
import dgir.core.ir.types.algorithmw.Expr.ExprCustom.InferFunction;
import dgir.core.ir.types.algorithmw.Expr.ExprCustom.InferFunctionResult;
import dgir.core.ir.types.algorithmw.Expr.ExprCustom.InstantiateFunction;
import dgir.core.ir.types.algorithmw.Expr.ExprCustom.ReplaceSymbolFunction;
import dgir.core.ir.types.algorithmw.InferResult;
import dgir.core.ir.types.algorithmw.Subst;
import dgir.core.ir.types.algorithmw.TypeInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;

public final class ScfAlgoWConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<ExprOrOperator<Expr, AlgorithmWType>, Expr, AlgorithmWType, TypeInference>addOperatorsToDialect(
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

    record IfData(Expr cond, Expr thenCase, Optional<Expr> elseCase) {
    }

    ScfOps.IfOp ifOp = (ScfOps.IfOp) op.asOp();

    var condExpr = new Expr.ExprVar(Symbol.of(ifOp.getOperandValue(0).orElseThrow()));
    var thenCase = regionToExpr(engine, ifOp.getThenRegion());
    Optional<Expr> elseCase = ifOp.getElseRegion().map(region -> regionToExpr(engine, region));
    var ifData = new IfData(condExpr, thenCase, elseCase);

    InferFunction<IfData> infFunc = (eng, env, data) -> {

      InferResult resCond = eng.infer(data.cond(), env);
      Subst finalSubst = resCond.subst();
      var condType = finalSubst.apply(resCond.type());

      var unifyCondRes = eng.unify(condType, new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_BOOL));
      finalSubst = unifyCondRes.subst().compose(finalSubst);

      InferResult resThen = eng.infer(data.thenCase(), env);
      finalSubst = resThen.subst().compose(finalSubst);
      var thenType = finalSubst.apply(resThen.type());

      if (data.elseCase().isPresent()) {
        InferResult resElse = eng.infer(data.elseCase().get(), env);
        finalSubst = resElse.subst().compose(finalSubst);
        var elseType = finalSubst.apply(resElse.type());

        var unifyBranchesRes = eng.unify(thenType, elseType);
        finalSubst = unifyBranchesRes.subst().compose(finalSubst);
      }

      AlgorithmWType resultType;
      if (op.getOutput().isPresent()) {
        resultType = finalSubst.apply(thenType);
      } else {
        // An if without results is only well-typed when both branches are unit.
        var unifyUnitRes = eng.unify(thenType, new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_UNIT));
        finalSubst = unifyUnitRes.subst().compose(finalSubst);
        resultType = new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_UNIT);
      }

      return new InferFunctionResult(finalSubst, resultType);
    };

    GetChildrenFunction<IfData> getChildrenFn = (data) -> {
      if (data.elseCase().isPresent()) {
        return List.of(data.cond(), data.thenCase(), data.elseCase().get());
      } else {
        return List.of(data.cond(), data.thenCase());
      }
    };

    InstantiateFunction<IfData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new Expr.ExprCustom<IfData>(toInstantiate, new IfData(data.cond().instantiate(eng, env, solution),
          data.thenCase().instantiate(eng, env, solution),
          data.elseCase().map(elseExpr -> elseExpr.instantiate(eng, env, solution))));
    };

    ReplaceSymbolFunction<IfData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new Expr.ExprCustom<IfData>(oldExpr, new IfData(data.cond().replaceSymbol(original, replacement),
          data.thenCase().replaceSymbol(original, replacement),
          data.elseCase().map(elseExpr -> elseExpr.replaceSymbol(original, replacement))));
    };

    var result = new Expr.ExprCustom<IfData>(ifData, infFunc, instFn, getChildrenFn, replaceSymbolFn,
        d -> new IfData(d.cond.copy(), d.thenCase.copy(), d.elseCase.map(e -> e.copy())));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (Expr.ExprCustom<IfData>) instantiatedExpr;
      var data = custExpr.getData();

      var condValue = OperationExprConversionUtils.getSymbolValue(data.cond());
      boolean withElse = data.elseCase().isPresent();

      ScfOps.IfOp newIf;
      if (op.getOutput().isPresent()) {
        newIf = new ScfOps.IfOp(op.getLocation(), condValue, withElse,
            OperationExprConversionUtils.inferredTypeToIrType(custExpr));
      } else {
        newIf = new ScfOps.IfOp(op.getLocation(), condValue, withElse);
      }
      var newOp = newIf.getOperation();

      fillRegionBlock(newOp, 0, data.thenCase());
      if (withElse) {
        fillRegionBlock(newOp, 1, data.elseCase().get());
      }

      return newOp;
    });

    return result;
  }

  public static Expr convertScopeOp(
      Operation op,
      TypeInference engine) {

    ScfOps.ScopeOp scopeOp = (ScfOps.ScopeOp) op.asOp();

    var body = regionToExpr(engine, scopeOp.getRegion());

    body.setInstantiateOperationCallback(instantiatedExpr -> {
      var newScope = new ScfOps.ScopeOp(op.getLocation());
      var newOp = newScope.getOperation();

      fillRegionBlock(newOp, 0, instantiatedExpr);

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

    var result = new Expr.ExprApp(
        new Expr.ExprAbs(params, regionToExpr(engine, forOp.getRegion())),
        List.of(
            new Expr.ExprVar(Symbol.of(forOp.getInitialValue())),
            new Expr.ExprVar(Symbol.of(forOp.getLowerBound())),
            new Expr.ExprVar(Symbol.of(forOp.getUpperBound())),
            new Expr.ExprVar(Symbol.of(forOp.getStep()))));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprApp;

      var app = (Expr.ExprApp) instantiatedExpr;
      assert app.args().size() == 4;

      List<Value> argResults = app.args().stream()
          .map(OperationExprConversionUtils::<Expr, AlgorithmWType>getSymbolValue).toList();

      var newForOp = new ScfOps.ForOp(op.getLocation(), argResults.get(0), argResults.get(1), argResults.get(2),
          argResults.get(3));
      var newOp = newForOp.getOperation();

      assert app.func() instanceof Expr.ExprAbs;
      var instantiatedAbs = (Expr.ExprAbs) app.func();
      fillRegionBlock(newOp, 0, instantiatedAbs.body());

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

    record WhileData(Expr cond, Expr body) {
    }

    ScfOps.WhileOp whileOp = (ScfOps.WhileOp) op.asOp();

    var whileData = new WhileData(
        regionToExpr(engine, whileOp.getConditionRegion()),
        regionToExpr(engine, whileOp.getBodyRegion()));

    InferFunction<WhileData> infFunc = (eng, env, data) -> {

      InferResult resCond = eng.infer(data.cond(), env);
      Subst finalSubst = resCond.subst();
      var condType = finalSubst.apply(resCond.type());

      var unifyCondRes = eng.unify(condType, new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_BOOL));
      finalSubst = unifyCondRes.subst().compose(finalSubst);

      InferResult resBody = eng.infer(data.body(), env);
      finalSubst = resBody.subst().compose(finalSubst);
      var bodyType = finalSubst.apply(resBody.type());

      var unifyBodyRes = eng.unify(bodyType, new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_UNIT));
      finalSubst = unifyBodyRes.subst().compose(finalSubst);

      return new InferFunctionResult(finalSubst, new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_UNIT));
    };

    GetChildrenFunction<WhileData> getChildrenFn = (data) -> {
      return List.of(data.cond(), data.body());
    };

    InstantiateFunction<WhileData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new Expr.ExprCustom<WhileData>(toInstantiate, new WhileData(
          data.cond().instantiate(eng, env, solution), data.body().instantiate(eng, env, solution)));
    };

    ReplaceSymbolFunction<WhileData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new Expr.ExprCustom<WhileData>(oldExpr, new WhileData(
          data.cond().replaceSymbol(original, replacement), data.body().replaceSymbol(original, replacement)));
    };

    var result = new Expr.ExprCustom<WhileData>(whileData, infFunc, instFn, getChildrenFn, replaceSymbolFn,
        d -> new WhileData(d.cond.copy(), d.body.copy()));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (Expr.ExprCustom<WhileData>) instantiatedExpr;
      var data = custExpr.getData();

      var newWhile = new ScfOps.WhileOp(op.getLocation());
      var newOp = newWhile.getOperation();

      fillRegionBlock(newOp, 0, data.cond());
      fillRegionBlock(newOp, 1, data.body());

      return newOp;
    });

    return result;
  }

  public static Expr convertSelectOp(
      Operation op,
      TypeInference engine) {

    record SelectData(Expr cond, Expr trueVal, Expr falseVal) {
    }

    ScfOps.SelectOp selectOp = (ScfOps.SelectOp) op.asOp();

    var selectData = new SelectData(
        new Expr.ExprVar(Symbol.of(selectOp.getCondition())),
        new Expr.ExprVar(Symbol.of(selectOp.getTrue())),
        new Expr.ExprVar(Symbol.of(selectOp.getFalse())));

    InferFunction<SelectData> infFunc = (eng, env, data) -> {

      InferResult resCond = eng.infer(data.cond(), env);
      Subst finalSubst = resCond.subst();
      var condType = finalSubst.apply(resCond.type());

      var unifyCondRes = eng.unify(condType, new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_BOOL));
      finalSubst = unifyCondRes.subst().compose(finalSubst);

      InferResult resTrue = eng.infer(data.trueVal(), env);
      finalSubst = resTrue.subst().compose(finalSubst);
      var trueType = finalSubst.apply(resTrue.type());

      InferResult resFalse = eng.infer(data.falseVal(), env);
      finalSubst = resFalse.subst().compose(finalSubst);
      var falseType = finalSubst.apply(resFalse.type());

      var unifyValsRes = eng.unify(trueType, falseType);
      finalSubst = unifyValsRes.subst().compose(finalSubst);
      var resultType = finalSubst.apply(trueType);

      return new InferFunctionResult(finalSubst, resultType);
    };

    GetChildrenFunction<SelectData> getChildrenFn = (data) -> {
      return List.of(data.cond(), data.trueVal(), data.falseVal());
    };

    InstantiateFunction<SelectData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new Expr.ExprCustom<SelectData>(toInstantiate, new SelectData(
          data.cond().instantiate(eng, env, solution),
          data.trueVal().instantiate(eng, env, solution),
          data.falseVal().instantiate(eng, env, solution)));
    };

    ReplaceSymbolFunction<SelectData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new Expr.ExprCustom<SelectData>(oldExpr, new SelectData(
          data.cond().replaceSymbol(original, replacement),
          data.trueVal().replaceSymbol(original, replacement),
          data.falseVal().replaceSymbol(original, replacement)));
    };

    var result = new Expr.ExprCustom<SelectData>(selectData, infFunc, instFn, getChildrenFn, replaceSymbolFn,
        d -> new SelectData(d.cond.copy(), d.trueVal.copy(), d.falseVal.copy()));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (Expr.ExprCustom<SelectData>) instantiatedExpr;
      var data = custExpr.getData();

      return new ScfOps.SelectOp(op.getLocation(),
          OperationExprConversionUtils.getSymbolValue(data.cond()),
          OperationExprConversionUtils.getSymbolValue(data.trueVal()),
          OperationExprConversionUtils.getSymbolValue(data.falseVal())).getOperation();
    });

    return result;
  }

  public static Expr convertYieldOp(
      Operation op,
      TypeInference engine) {

    record YieldData(Expr value) {
    }

    ScfOps.YieldOp yieldOp = (ScfOps.YieldOp) op.asOp();

    var selectData = new YieldData(
        new Expr.ExprVar(Symbol.of(yieldOp.getOperand())));

    InferFunction<YieldData> infFunc = (eng, env, data) -> {

      InferResult resValue = eng.infer(data.value(), env);
      Subst finalSubst = resValue.subst();
      var valueType = finalSubst.apply(resValue.type());

      return new InferFunctionResult(finalSubst, valueType);
    };

    GetChildrenFunction<YieldData> getChildrenFn = (data) -> {
      return List.of(data.value);
    };

    InstantiateFunction<YieldData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new Expr.ExprCustom<YieldData>(toInstantiate,
          new YieldData(data.value.instantiate(engine, env, solution)));
    };

    ReplaceSymbolFunction<YieldData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new Expr.ExprCustom<YieldData>(oldExpr, new YieldData(
          data.value.replaceSymbol(original, replacement)));
    };

    var result = new Expr.ExprCustom<YieldData>(selectData, infFunc, instFn, getChildrenFn, replaceSymbolFn,
        d -> new YieldData(d.value.copy()));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (Expr.ExprCustom<YieldData>) instantiatedExpr;
      var data = custExpr.getData();

      return new ScfOps.YieldOp(op.getLocation(),
          OperationExprConversionUtils.getSymbolValue(data.value())).getOperation();
    });

    return result;
  }

  public static Expr convertContinueOp(
      Operation op,
      TypeInference engine) {

    record JumpData() {
    }

    InferFunction<JumpData> infFunc = (eng, env, data) -> new InferFunctionResult(
        Subst.newEmpty(), new AlgorithmWType.Var(new TypeVar()));

    var result = new Expr.ExprCustom<JumpData>(new JumpData(), infFunc, null, null, null, d -> d);

    result.setInstantiateOperationCallback(instantiatedExpr -> new ScfOps.ContinueOp(op.getLocation()).getOperation());

    return result;
  }

  public static Expr convertEndOp(
      Operation op,
      TypeInference engine) {

    record EndData() {
    }

    InferFunction<EndData> infFunc = (eng, env, data) -> new InferFunctionResult(
        Subst.newEmpty(), new AlgorithmWType.Var(new TypeVar()));

    var result = new Expr.ExprCustom<EndData>(new EndData(), infFunc, null, null, null, d -> d);

    result.setInstantiateOperationCallback(instantiatedExpr -> new ScfOps.EndOp(op.getLocation()).getOperation());

    return result;
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  /**
   * Lowers a single region body into a let-rec expression. All operations —
   * including terminators — are converted to terms: the terminator is the
   * region's last binding, so a yield types the region with its value's type,
   * and jump terminators (continue/end) infer as fresh type variables that
   * never shadow the region's trailing value type.
   */
  private static Expr regionToExpr(TypeInference engine, Region region) {
    GeneralBlock generalBlock = new GeneralBlock();

    for (Operation o : region.getBlocks().getFirst().getOperations()) {
      generalBlock.addOperation(o);
    }

    return engine.generalBlockToInferenceExpr(generalBlock);
  }

  /**
   * Rebuilds the instantiated operations of a lowered region expression and adds
   * them to the given region index of {@code newOp}.
   */
  private static void fillRegionBlock(Operation newOp, int regionIndex, Expr regionExpr) {
    assert regionExpr instanceof Expr.ExprLetRec;
    var letRec = (Expr.ExprLetRec) regionExpr;

    var exprsForBlock = OperationExprConversionUtils.getAllChildrenForScopeExpression(letRec, letRec.body());
    assert exprsForBlock.stream().allMatch(e -> e.getUnderlyingOperation().isPresent());

    Block block = newOp.getRegion(regionIndex).orElseThrow().getEntryBlock();

    for (var e : exprsForBlock) {
      // SAFETY: already asserted, that the underlying operation is actually present.
      block.addOperation(e.getUnderlyingOperation().get());
    }

  }

  /**
   *
   * Returns the value the given let-rec scope expression evaluates to, i.e. the
   * value of its body expression's output symbol.
   *
   * @param regionExpr the let-rec expression of a lowered region body.
   * @return the body value.
   */
  public static Value getLetRecBodyValue(Expr regionExpr) {
    assert regionExpr instanceof Expr.ExprLetRec;
    return OperationExprConversionUtils.getSymbolValue(((Expr.ExprLetRec) regionExpr).body());
  }
}

package dgir.dialect.cf;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Block;
import dgir.core.ir.Operation;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
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

public final class CfAlgoWConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<ExprOrOperator<Expr, AlgorithmWType>, Expr, AlgorithmWType, TypeInference>addOperatorsToDialect(
        AlgorithmWInference.class,
        Pair.of(CfOps.BranchOp.class, CfAlgoWConversion::convertBranchOp),
        Pair.of(CfOps.BranchCondOp.class, CfAlgoWConversion::convertBranchCondOp),
        Pair.of(CfOps.AssertOp.class, CfAlgoWConversion::convertAssert));
  }

  public static Expr convertBranchOp(
      Operation op,
      TypeInference engine) {

    record BranchData(Expr body) {
    }

    CfOps.BranchOp branchOp = (CfOps.BranchOp) op.asOp();

    var expr = engine.generalBlockToInferenceExpr(GeneralBlock.fromBlock(branchOp.getTarget()));
    var branchOpData = new BranchData(expr);

    InferFunction<BranchData> infFunc = (eng, env, data) -> {

      InferResult resValue = eng.infer(data.body, env);
      Subst finalSubst = resValue.subst();
      var valueType = finalSubst.apply(resValue.type());

      return new InferFunctionResult(finalSubst, valueType);
    };

    GetChildrenFunction<BranchData> getChildrenFn = (data) -> {
      return List.of(data.body);
    };

    InstantiateFunction<BranchData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new Expr.ExprCustom<BranchData>(toInstantiate, new BranchData(data.body.instantiate(eng, env, solution)));
    };

    ReplaceSymbolFunction<BranchData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new Expr.ExprCustom<BranchData>(oldExpr, new BranchData(data.body.replaceSymbol(original, replacement)));
    };

    var result = new Expr.ExprCustom<BranchData>(branchOpData, infFunc, instFn, getChildrenFn, replaceSymbolFn,
        d -> new BranchData(d.body.copy()));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (Expr.ExprCustom<BranchData>) instantiatedExpr;
      var body = custExpr.getData().body;
      assert body instanceof Expr.ExprLetRec;
      var letRec = (Expr.ExprLetRec) body;

      var exprsForBlock = OperationExprConversionUtils.getAllChildrenForScopeExpression(letRec, letRec.body());
      assert exprsForBlock.stream().allMatch(e -> e.getUnderlyingOperation().isPresent());

      var block = new Block();

      for (var e : exprsForBlock) {
        // SAFETY: already asserted, that the underlying operation is actually present.
        block.addOperation(e.getUnderlyingOperation().get());
      }

      var newCfOp = new CfOps.BranchOp(op.getLocation(), block).getOperation();
      newCfOp.getTemporaryRegion().addBlock(block);

      return newCfOp;
    });
    return result;
  }

  public static Expr convertBranchCondOp(
      Operation op,
      TypeInference engine) {

    record BranchData(Expr cond, Expr thenCase, Expr elseCase) {
    }

    CfOps.BranchCondOp castOp = (CfOps.BranchCondOp) op.asOp();

    var condExpr = new Expr.ExprVar(Symbol.of(castOp.getCond()));
    var thenCase = engine.generalBlockToInferenceExpr(GeneralBlock.fromBlock(castOp.getCondTrueBlock()));
    var elseCase = engine.generalBlockToInferenceExpr(GeneralBlock.fromBlock(castOp.getCondFalseBlock()));
    var branchOpData = new BranchData(condExpr, thenCase, elseCase);

    InferFunction<BranchData> infFunc = (eng, env, data) -> {

      InferResult resCond = eng.infer(data.cond(), env);
      Subst finalSubst = resCond.subst();
      var condType = finalSubst.apply(resCond.type());

      var unifyCondRes = eng.unify(condType, new AlgorithmWType.LitType(TypeIdent.from("bool")));
      finalSubst = unifyCondRes.subst().compose(finalSubst);

      InferResult resThen = eng.infer(data.thenCase(), env);
      finalSubst = resThen.subst().compose(finalSubst);
      var thenType = finalSubst.apply(resThen.type());

      InferResult resElse = eng.infer(data.elseCase(), env);
      finalSubst = resElse.subst().compose(finalSubst);
      var elseType = finalSubst.apply(resElse.type());

      // TODO: figure out if it would actually be better if unit is returned,
      // always!
      var unifyBodyRes = eng.unify(thenType, elseType);
      finalSubst = unifyBodyRes.subst().compose(finalSubst);
      var finalType = finalSubst.apply(thenType);

      return new InferFunctionResult(finalSubst, finalType);
    };

    GetChildrenFunction<BranchData> getChildrenFn = (data) -> {
      return List.of(data.cond, data.thenCase, data.elseCase);
    };

    InstantiateFunction<BranchData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new Expr.ExprCustom<BranchData>(toInstantiate, new BranchData(data.cond.instantiate(eng, env, solution),
          data.thenCase.instantiate(eng, env, solution), data.elseCase.instantiate(eng, env, solution)));
    };

    ReplaceSymbolFunction<BranchData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new Expr.ExprCustom<BranchData>(oldExpr, new BranchData(data.cond.replaceSymbol(original, replacement),
          data.thenCase.replaceSymbol(original, replacement), data.elseCase.replaceSymbol(original, replacement)));
    };

    var result = new Expr.ExprCustom<BranchData>(branchOpData, infFunc, instFn, getChildrenFn, replaceSymbolFn,
        d -> new BranchData(d.cond.copy(), d.thenCase.copy(), d.elseCase.copy()));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (Expr.ExprCustom<BranchData>) instantiatedExpr;
      var data = custExpr.getData();

      assert data.cond.getUnderlyingOperation().isPresent();
      assert data.cond.getUnderlyingOperation().get().getOutput().isPresent();
      var condValue = data.cond.getUnderlyingOperation().get().getOutputValueOrThrow();

      assert data.thenCase instanceof Expr.ExprLetRec;
      assert data.elseCase instanceof Expr.ExprLetRec;

      var thenLetRec = (Expr.ExprLetRec) data.thenCase;
      var elseLetRec = (Expr.ExprLetRec) data.elseCase;

      var exprsForThenBlock = OperationExprConversionUtils.getAllChildrenForScopeExpression(thenLetRec,
          thenLetRec.body());
      assert exprsForThenBlock.stream().allMatch(e -> e.getUnderlyingOperation().isPresent());

      var exprsForElseBlock = OperationExprConversionUtils.getAllChildrenForScopeExpression(elseLetRec,
          elseLetRec.body());
      assert exprsForElseBlock.stream().allMatch(e -> e.getUnderlyingOperation().isPresent());

      var thenBlock = new Block();
      var elseBlock = new Block();

      for (var e : exprsForThenBlock) {
        // SAFETY: already asserted, that the underlying operation is actually present.
        thenBlock.addOperation(e.getUnderlyingOperation().get());
      }

      for (var e : exprsForElseBlock) {
        // SAFETY: already asserted, that the underlying operation is actually present.
        elseBlock.addOperation(e.getUnderlyingOperation().get());
      }

      var newCfOp = new CfOps.BranchCondOp(op.getLocation(), condValue, thenBlock, elseBlock).getOperation();
      newCfOp.getTemporaryRegion().addBlock(thenBlock);
      newCfOp.getTemporaryRegion().addBlock(elseBlock);

      return newCfOp;
    });

    return result;
  }

  public static Expr convertAssert(
      Operation op,
      TypeInference engine) {

    record AssertData(Expr cond, Optional<Expr> message) {
    }

    CfOps.AssertOp assertOp = (CfOps.AssertOp) op.asOp();

    Expr condExpr = new Expr.ExprVar(Symbol.of(assertOp.getCond()));
    Optional<Expr> messageExpr = assertOp.getMessage().map(msg -> new Expr.ExprVar(Symbol.of(msg)));
    var assertData = new AssertData(condExpr, messageExpr);

    InferFunction<AssertData> infFunc = (eng, env, data) -> {

      InferResult resCond = eng.infer(data.cond(), env);
      Subst finalSubst = resCond.subst();
      var condType = finalSubst.apply(resCond.type());

      var unifyCondRes = eng.unify(condType, new AlgorithmWType.LitType(TypeIdent.from("bool")));
      finalSubst = unifyCondRes.subst().compose(finalSubst);

      if (data.message.isPresent()) {
        InferResult resMessage = eng.infer(data.message.get(), env);
        finalSubst = resMessage.subst().compose(finalSubst);
        var messageType = finalSubst.apply(resMessage.type());

        var messageUnifyRes = eng.unify(messageType, new AlgorithmWType.LitType(TypeIdent.from("string")));
        finalSubst = messageUnifyRes.subst().compose(finalSubst);
      }

      return new InferFunctionResult(finalSubst, new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_UNIT));
    };

    GetChildrenFunction<AssertData> getChildrenFn = (data) -> {
      if (data.message.isPresent()) {
        return List.of(data.cond, data.message.get());
      } else {
        return List.of(data.cond);
      }
    };

    InstantiateFunction<AssertData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new Expr.ExprCustom<AssertData>(toInstantiate, new AssertData(data.cond.instantiate(eng, env, solution),
          data.message.map(msg -> msg.instantiate(engine, env, solution))));
    };

    ReplaceSymbolFunction<AssertData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new Expr.ExprCustom<AssertData>(oldExpr, new AssertData(data.cond.replaceSymbol(original, replacement),
          data.message.map(msg -> msg.replaceSymbol(original, replacement))));
    };

    var result = new Expr.ExprCustom<AssertData>(assertData, infFunc, instFn, getChildrenFn, replaceSymbolFn,
        d -> new AssertData(d.cond.copy(), d.message.map(m -> m.copy())));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (Expr.ExprCustom<AssertData>) instantiatedExpr;
      var data = custExpr.getData();

      assert data.cond.getUnderlyingOperation().isPresent();
      assert data.cond.getUnderlyingOperation().get().getOutput().isPresent();
      var condValue = data.cond.getUnderlyingOperation().get().getOutputValueOrThrow();

      if (data.message.isPresent()) {
        assert data.message.get().getUnderlyingOperation().isPresent();
        assert data.message.get().getUnderlyingOperation().get().getOutput().isPresent();
        var messageValue = data.message.get().getUnderlyingOperation().get().getOutputValueOrThrow();

        return new CfOps.AssertOp(op.getLocation(), condValue, messageValue).getOperation();
      } else {
        return new CfOps.AssertOp(op.getLocation(), condValue).getOperation();
      }
    });

    return result;
  }
}

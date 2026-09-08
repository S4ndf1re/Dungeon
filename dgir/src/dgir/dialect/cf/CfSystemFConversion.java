package dgir.dialect.cf;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Block;
import dgir.core.ir.Operation;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.InferenceTree;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.systemf.CheckResult;
import dgir.core.ir.types.systemf.Context;
import dgir.core.ir.types.systemf.Entry;
import dgir.core.ir.types.systemf.Expr;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.systemf.SystemFInference;
import dgir.core.ir.types.systemf.SystemFType;
import dgir.core.ir.types.systemf.TypeInference;
import dgir.core.ir.types.systemf.TypeResult;
import dgir.core.ir.types.systemf.Expr.Custom.CheckFunction;
import dgir.core.ir.types.systemf.Expr.Custom.GetChildrenFunction;
import dgir.core.ir.types.systemf.Expr.Custom.InferFunction;
import dgir.core.ir.types.systemf.Expr.Custom.InstantiateFunction;

public final class CfSystemFConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinSystemFConversion() {
    ConverterRegistry.<ExprOrOperator<Expr, SystemFType>, Expr, SystemFType, TypeInference>addOperatorsToDialect(
        SystemFInference.class,
        Pair.of(CfOps.BranchOp.class, CfSystemFConversion::convertBranchOp),
        Pair.of(CfOps.BranchCondOp.class, CfSystemFConversion::convertBranchCondOp),
        Pair.of(CfOps.AssertOp.class, CfSystemFConversion::convertAssert));
  }

  private static CheckResult checkWithBool(TypeInference engine, Context ctx, Expr expr) {
    var inferred = engine.infer(ctx, expr);
    var finalCtx = inferred.ctx();
    var exprType = finalCtx.apply(inferred.type());

    var subRes = engine.subtype(finalCtx, exprType,
        new SystemFType.Lit(TypeIdent.from("bool")));
    return new CheckResult(subRes.ctx(), subRes.tree());
  }

  public static Expr convertBranchOp(
      Operation op,
      TypeInference engine) {

    record BranchData(Expr body) {
    }

    CfOps.BranchOp branchOp = (CfOps.BranchOp) op.asOp();

    var expr = engine.generalBlockToInferenceExpr(GeneralBlock.fromBlock(branchOp.getTarget()));
    var branchOpData = new BranchData(expr);

    InferFunction<BranchData> infFunc = (eng, ctx, data) -> {
      return eng.infer(ctx, data.body);
    };

    GetChildrenFunction<BranchData> getChildrenFn = (data) -> List.of(data.body);

    InstantiateFunction<BranchData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new Expr.Custom<BranchData>(toInstantiate,
          new BranchData(data.body.instantiate(eng, env, solution)));
    };

    var result = new Expr.Custom<BranchData>(branchOpData, infFunc, null, instFn, getChildrenFn);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.Custom;

      @SuppressWarnings("unchecked")
      var custExpr = (Expr.Custom<BranchData>) instantiatedExpr;
      var data = custExpr.getData();
      assert data.body instanceof Expr.Let;
      var let = (Expr.Let) data.body;

      var exprsForBlock = OperationExprConversionUtils.getAllChildrenForScopeExpression(let, let.body());
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

    var condExpr = new Expr.Var(Symbol.of(castOp.getCond()));
    var thenCase = engine.generalBlockToInferenceExpr(GeneralBlock.fromBlock(castOp.getCondTrueBlock()));
    var elseCase = engine.generalBlockToInferenceExpr(GeneralBlock.fromBlock(castOp.getCondFalseBlock()));
    var branchOpData = new BranchData(condExpr, thenCase, elseCase);

    InferFunction<BranchData> infFunc = (eng, ctx, data) -> {
      var condChecked = checkWithBool(eng, ctx, data.cond());
      var currentCtx = condChecked.ctx();

      var resThen = eng.infer(currentCtx, data.thenCase());
      currentCtx = resThen.ctx();
      var thenType = currentCtx.apply(resThen.type());

      var resElse = eng.infer(currentCtx, data.elseCase());
      currentCtx = resElse.ctx();
      var elseType = currentCtx.apply(resElse.type());

      // TODO: figure out if it would actually be better if unit is returned,
      // always!
      var unifiedRes = eng.subtype(currentCtx, thenType, elseType);
      var finalType = unifiedRes.ctx().apply(thenType);

      return new TypeResult(
          finalType,
          unifiedRes.ctx(),
          new InferenceTree(
              "InfCond",
              ctx + " |- " + data,
              finalType.toString(),
              List.of(condChecked.tree(), resThen.tree(), resElse.tree(), unifiedRes.tree())));
    };

    GetChildrenFunction<BranchData> getChildrenFn = (data) -> List.of(data.cond, data.thenCase, data.elseCase);

    InstantiateFunction<BranchData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new Expr.Custom<BranchData>(toInstantiate,
          new BranchData(
              data.cond.instantiate(eng, env, solution),
              data.thenCase.instantiate(eng, env, solution),
              data.elseCase.instantiate(eng, env, solution)));
    };

    var result = new Expr.Custom<BranchData>(branchOpData, infFunc, null, instFn, getChildrenFn);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.Custom;

      @SuppressWarnings("unchecked")
      var custExpr = (Expr.Custom<BranchData>) instantiatedExpr;
      var data = custExpr.getData();

      assert data.cond.getUnderlyingOperation().isPresent();
      assert data.cond.getUnderlyingOperation().get().getOutput().isPresent();
      var condValue = data.cond.getUnderlyingOperation().get().getOutputValueOrThrow();

      assert data.thenCase instanceof Expr.Let;
      assert data.elseCase instanceof Expr.Let;

      var thenLet = (Expr.Let) data.thenCase;
      var elseLet = (Expr.Let) data.elseCase;

      var exprsForThenBlock = OperationExprConversionUtils.getAllChildrenForScopeExpression(thenLet, thenLet.body());
      assert exprsForThenBlock.stream().allMatch(e -> e.getUnderlyingOperation().isPresent());

      var exprsForElseBlock = OperationExprConversionUtils.getAllChildrenForScopeExpression(elseLet, elseLet.body());
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

    Expr condExpr = new Expr.Var(Symbol.of(assertOp.getCond()));
    Optional<Expr> messageExpr = assertOp.getMessage().map(msg -> new Expr.Var(Symbol.of(msg)));
    var assertData = new AssertData(condExpr, messageExpr);

    InferFunction<AssertData> infFunc = (eng, ctx, data) -> {
      var condChecked = checkWithBool(eng, ctx, data.cond());
      var currentCtx = condChecked.ctx();

      if (data.message.isPresent()) {
        var resMessage = eng.infer(currentCtx, data.message.get());
        currentCtx = resMessage.ctx();
        var messageType = currentCtx.apply(resMessage.type());

        var messageRes = eng.subtype(currentCtx, messageType,
            new SystemFType.Lit(TypeIdent.from("string")));
        currentCtx = messageRes.ctx();
      }

      return new TypeResult(
          new SystemFType.Lit(TypeIdent.TYPE_IDENT_UNIT),
          currentCtx,
          new InferenceTree(
              "InfAssert",
              ctx + " |- " + data,
              "unit",
              List.of()));
    };

    GetChildrenFunction<AssertData> getChildrenFn = (data) -> {
      if (data.message.isPresent()) {
        return List.of(data.cond, data.message.get());
      } else {
        return List.of(data.cond);
      }
    };

    InstantiateFunction<AssertData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new Expr.Custom<AssertData>(toInstantiate,
          new AssertData(
              data.cond.instantiate(eng, env, solution),
              data.message.map(msg -> msg.instantiate(eng, env, solution))));
    };

    var result = new Expr.Custom<AssertData>(assertData, infFunc, null, instFn, getChildrenFn);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.Custom;

      @SuppressWarnings("unchecked")
      var custExpr = (Expr.Custom<AssertData>) instantiatedExpr;
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

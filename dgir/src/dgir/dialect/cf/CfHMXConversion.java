package dgir.dialect.cf;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Block;
import dgir.core.ir.Operation;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXExpr.ExprCustom.GenerateFunction;
import dgir.core.ir.types.builtin.hmx.HMXExpr.ExprCustom.GetChildrenFunction;
import dgir.core.ir.types.builtin.hmx.HMXExpr.ExprCustom.InstantiateFunction;
import dgir.core.ir.types.builtin.hmx.HMXExpr.ExprCustom.ReplaceSymbolFunction;
import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;

public final class CfHMXConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<HMXExpr, HMXType, TypeInference>addOperatorsToDialect(HMXInference.class,
        Pair.of(CfOps.BranchOp.class, CfHMXConversion::convertBranchOp),
        Pair.of(CfOps.BranchCondOp.class, CfHMXConversion::convertBranchCondOp),
        Pair.of(CfOps.AssertOp.class, CfHMXConversion::convertAssert));
  }

  public static HMXExpr convertBranchOp(Operation op, TypeInference engine) {

    record BranchData(HMXExpr body) {
    }

    CfOps.BranchOp branchOp = (CfOps.BranchOp) op.asOp();

    var expr = engine.generalBlockToInferenceExpr(GeneralBlock.fromBlock(branchOp.getTarget()));
    var branchOpData = new BranchData(expr);

    GenerateFunction<BranchData> infFunc = (eng, env, type, data) -> {

      GenerateResult resValue = eng.generate(data.body, env, type);

      return resValue.constr();
    };

    GetChildrenFunction<BranchData> getChildrenFn = (data) -> {
      return List.of(data.body);
    };

    InstantiateFunction<BranchData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new HMXExpr.ExprCustom<BranchData>(toInstantiate,
          new BranchData(data.body.instantiate(eng, env, solution)));
    };

    ReplaceSymbolFunction<BranchData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new HMXExpr.ExprCustom<BranchData>(oldExpr,
          new BranchData(data.body.replaceSymbol(original, replacement)));
    };

    var result = new HMXExpr.ExprCustom<BranchData>(branchOpData, infFunc, instFn, getChildrenFn, replaceSymbolFn);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (HMXExpr.ExprCustom<BranchData>) instantiatedExpr;
      var body = custExpr.getData().body;

      var block = new Block();
      body.fillBlockScoped(block);

      var newCfOp = new CfOps.BranchOp(op.getLocation(), block).getOperation();
      newCfOp.getTemporaryRegion().addBlock(block);

      return newCfOp;
    });
    return result;
  }

  public static HMXExpr convertBranchCondOp(Operation op, TypeInference engine) {

    record BranchData(HMXExpr cond, HMXExpr thenCase, HMXExpr elseCase) {
    }

    CfOps.BranchCondOp castOp = (CfOps.BranchCondOp) op.asOp();

    var condExpr = new HMXExpr.ExprVar(Symbol.<HMXExpr, HMXType>of(castOp.getCond()));
    var thenCase = engine.generalBlockToInferenceExpr(GeneralBlock.fromBlock(castOp.getCondTrueBlock()));
    var elseCase = engine.generalBlockToInferenceExpr(GeneralBlock.fromBlock(castOp.getCondFalseBlock()));
    var branchOpData = new BranchData(condExpr, thenCase, elseCase);

    GenerateFunction<BranchData> infFunc = (eng, env, type, data) -> {

      var condTyVar = new TypeVar<HMXType>();
      var condType = new HMXType.Var(condTyVar);
      GenerateResult resCond = eng.generate(data.cond(), env, condType);

      var condConstr = new Constraint.Sub(condType, new HMXType.LitType(TypeIdent.from("bool")));

      GenerateResult resThen = eng.generate(data.thenCase(), env, type);

      GenerateResult resElse = eng.generate(data.elseCase(), env, type);

      // TODO: figure out if it would actually be better if unit is returned,
      // always!
      // Generating both cases against the expected type constrains them to the
      // same type, like the unify in the algorithm w version.
      return new Constraint.Exists(List.of(condTyVar),
          new Constraint.And(resCond.constr(), condConstr, resThen.constr(), resElse.constr()));
    };

    GetChildrenFunction<BranchData> getChildrenFn = (data) -> {
      return List.of(data.cond, data.thenCase, data.elseCase);
    };

    InstantiateFunction<BranchData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new HMXExpr.ExprCustom<BranchData>(toInstantiate, new BranchData(data.cond.instantiate(eng, env, solution),
          data.thenCase.instantiate(eng, env, solution), data.elseCase.instantiate(eng, env, solution)));
    };

    ReplaceSymbolFunction<BranchData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new HMXExpr.ExprCustom<BranchData>(oldExpr, new BranchData(data.cond.replaceSymbol(original, replacement),
          data.thenCase.replaceSymbol(original, replacement), data.elseCase.replaceSymbol(original, replacement)));
    };

    var result = new HMXExpr.ExprCustom<BranchData>(branchOpData, infFunc, instFn, getChildrenFn, replaceSymbolFn);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (HMXExpr.ExprCustom<BranchData>) instantiatedExpr;
      var data = custExpr.getData();

      var condValue = data.cond.getOutputValue();

      var thenBlock = new Block();
      data.thenCase.fillBlockScoped(thenBlock);

      var elseBlock = new Block();
      data.elseCase.fillBlockScoped(elseBlock);

      var newCfOp = new CfOps.BranchCondOp(op.getLocation(), condValue, thenBlock, elseBlock).getOperation();
      newCfOp.getTemporaryRegion().addBlock(thenBlock);
      newCfOp.getTemporaryRegion().addBlock(elseBlock);

      return newCfOp;
    });

    return result;
  }

  public static HMXExpr convertAssert(Operation op, TypeInference engine) {

    record AssertData(HMXExpr cond, Optional<HMXExpr> message) {
    }

    CfOps.AssertOp assertOp = (CfOps.AssertOp) op.asOp();

    HMXExpr condExpr = new HMXExpr.ExprVar(Symbol.<HMXExpr, HMXType>of(assertOp.getCond()));
    Optional<HMXExpr> messageExpr = assertOp.getMessage()
        .map(msg -> new HMXExpr.ExprVar(Symbol.<HMXExpr, HMXType>of(msg)));
    var assertData = new AssertData(condExpr, messageExpr);

    GenerateFunction<AssertData> infFunc = (eng, env, type, data) -> {

      var condTyVar = new TypeVar<HMXType>();
      var condType = new HMXType.Var(condTyVar);
      GenerateResult resCond = eng.generate(data.cond(), env, condType);

      var condConstr = new Constraint.Sub(condType, new HMXType.LitType(TypeIdent.from("bool")));

      var constrs = new ArrayList<Constraint>();
      constrs.add(resCond.constr());
      constrs.add(condConstr);

      TypeVar<HMXType> msgTyVar = null;
      if (data.message().isPresent()) {
        msgTyVar = new TypeVar<HMXType>();
        var messageType = new HMXType.Var(msgTyVar);
        GenerateResult resMessage = eng.generate(data.message().get(), env, messageType);
        constrs.add(resMessage.constr());
        constrs.add(new Constraint.Sub(messageType, new HMXType.LitType(TypeIdent.from("string"))));
      }

      constrs.add(new Constraint.Equal(type, new HMXType.LitType(TypeIdent.TYPE_IDENT_UNIT)));

      var quantified = msgTyVar == null ? List.of(condTyVar) : List.of(condTyVar, msgTyVar);
      return new Constraint.Exists(quantified, new Constraint.And(constrs));
    };

    GetChildrenFunction<AssertData> getChildrenFn = (data) -> {
      if (data.message.isPresent()) {
        return List.of(data.cond, data.message.get());
      } else {
        return List.of(data.cond);
      }
    };

    InstantiateFunction<AssertData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new HMXExpr.ExprCustom<AssertData>(toInstantiate, new AssertData(data.cond.instantiate(eng, env, solution),
          data.message.map(msg -> msg.instantiate(engine, env, solution))));
    };

    ReplaceSymbolFunction<AssertData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new HMXExpr.ExprCustom<AssertData>(oldExpr, new AssertData(data.cond.replaceSymbol(original, replacement),
          data.message.map(msg -> msg.replaceSymbol(original, replacement))));
    };

    var result = new HMXExpr.ExprCustom<AssertData>(assertData, infFunc, instFn, getChildrenFn, replaceSymbolFn);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (HMXExpr.ExprCustom<AssertData>) instantiatedExpr;
      var data = custExpr.getData();

      var condValue = data.cond.getOutputValue();

      if (data.message.isPresent()) {
        var messageValue = data.message.get().getOutputValue();

        return new CfOps.AssertOp(op.getLocation(), condValue, messageValue).getOperation();
      } else {
        return new CfOps.AssertOp(op.getLocation(), condValue).getOperation();
      }
    });

    return result;
  }
}

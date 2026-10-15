package dgir.dialect.cf;

import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Block;
import dgir.core.ir.Operation;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.expr.Assert;
import dgir.core.ir.types.builtin.hmx.expr.Branch;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.builtin.hmx.expr.Var;
import dgir.core.ir.types.compatibility.ConverterRegistry;

public final class CfHMXConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinHMXConversion() {
    ConverterRegistry.<HMXExpr, HMXType, TypeInference>addOperatorsToDialect(HMXInference.class,
        Pair.of(CfOps.BranchOp.class, CfHMXConversion::convertBranchOp),
        Pair.of(CfOps.BranchCondOp.class, CfHMXConversion::convertBranchCondOp),
        Pair.of(CfOps.AssertOp.class, CfHMXConversion::convertAssert));
  }

  public static HMXExpr convertBranchOp(Operation op, TypeInference engine) {

    CfOps.BranchOp branchOp = (CfOps.BranchOp) op.asOp();

    var expr = engine.generalBlockToInferenceExpr(GeneralBlock.fromBlock(branchOp.getTarget()));

    var result = new Branch(branchOp.getLocation(), expr);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Branch;

      var custExpr = (Branch) instantiatedExpr;
      var body = custExpr.block;

      var block = new Block();
      body.fillBlockScoped(block);

      var newCfOp = new CfOps.BranchOp(op.getLocation(), block).getOperation();
      newCfOp.getTemporaryRegion().addBlock(block);

      return newCfOp;
    });
    return result;
  }

  public static HMXExpr convertBranchCondOp(Operation op, TypeInference engine) {

    CfOps.BranchCondOp branchOp = (CfOps.BranchCondOp) op.asOp();

    var condExpr = new Var(branchOp.getLocation(), Symbol.<HMXExpr, HMXType>of(branchOp.getCond()));
    var thenCase = engine.generalBlockToInferenceExpr(GeneralBlock.fromBlock(branchOp.getCondTrueBlock()));
    var elseCase = engine.generalBlockToInferenceExpr(GeneralBlock.fromBlock(branchOp.getCondFalseBlock()));

    var result = new Branch(branchOp.getLocation(), condExpr, thenCase, elseCase);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Branch;

      var custExpr = (Branch) instantiatedExpr;

      var condValue = custExpr.condition.get().getLeft().getOutputValue();

      var thenBlock = new Block();
      custExpr.condition.get().getRight().fillBlockScoped(thenBlock);

      var elseBlock = new Block();
      custExpr.block.fillBlockScoped(elseBlock);

      var newCfOp = new CfOps.BranchCondOp(op.getLocation(), condValue, thenBlock, elseBlock).getOperation();
      newCfOp.getTemporaryRegion().addBlock(thenBlock);
      newCfOp.getTemporaryRegion().addBlock(elseBlock);

      return newCfOp;
    });

    return result;
  }

  public static HMXExpr convertAssert(Operation op, TypeInference engine) {

    CfOps.AssertOp assertOp = (CfOps.AssertOp) op.asOp();

    HMXExpr condExpr = new Var(assertOp.getLocation(), Symbol.<HMXExpr, HMXType>of(assertOp.getCond()));
    Optional<HMXExpr> messageExpr = assertOp.getMessage()
        .map(msg -> new Var(assertOp.getLocation(), Symbol.<HMXExpr, HMXType>of(msg)));

    var result = new Assert(assertOp.getLocation(), condExpr, messageExpr);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Assert;

      var custExpr = (Assert) instantiatedExpr;

      var condValue = custExpr.cond.getOutputValue();

      if (custExpr.message.isPresent()) {
        var messageValue = custExpr.message.get().getOutputValue();

        return new CfOps.AssertOp(op.getLocation(), condValue, messageValue).getOperation();
      } else {
        return new CfOps.AssertOp(op.getLocation(), condValue).getOperation();
      }
    });

    return result;
  }
}

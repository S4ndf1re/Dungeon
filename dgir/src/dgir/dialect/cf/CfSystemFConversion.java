package dgir.dialect.cf;

import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFInference;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.expr.Assert;
import dgir.core.ir.types.builtin.systemf.expr.Branch;
import dgir.core.ir.types.builtin.systemf.expr.Var;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.traits.IBlock;

public final class CfSystemFConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinSystemFConversion() {
    ConverterRegistry.<Expr, SystemFType, TypeInference>addOperatorsToDialect(
        SystemFInference.class,
        Pair.of(CfOps.BranchOp.class, CfSystemFConversion::convertBranchOp),
        Pair.of(CfOps.BranchCondOp.class, CfSystemFConversion::convertBranchCondOp),
        Pair.of(CfOps.AssertOp.class, CfSystemFConversion::convertAssert));
  }

  public static Expr convertBranchOp(
      Operation op,
      TypeInference engine) {

    CfOps.BranchOp branchOp = (CfOps.BranchOp) op.asOp();

    var expr = engine.generalBlockToInferenceExpr(GeneralBlock.fromBlock(branchOp.getTarget()));

    var result = new Branch(branchOp.getLocation(), expr);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Branch;

      var branchExpr = (Branch) instantiatedExpr;
      var body = branchExpr.block;
      assert body instanceof IBlock;
      var block = ((IBlock<Expr, SystemFType>) body).asBlock();

      var newCfOp = new CfOps.BranchOp(op.getLocation(), block).getOperation();
      newCfOp.getTemporaryRegion().addTemporaryBlock(block);

      return newCfOp;
    });
    return result;
  }

  @SuppressWarnings("unchecked")
  public static Expr convertBranchCondOp(
      Operation op,
      TypeInference engine) {

    CfOps.BranchCondOp branchCondOp = (CfOps.BranchCondOp) op.asOp();

    var condExpr = new Var(branchCondOp.getLocation(), Symbol.of(branchCondOp.getCond()));
    var thenCase = engine.generalBlockToInferenceExpr(GeneralBlock.fromBlock(branchCondOp.getCondTrueBlock()));
    var elseCase = engine.generalBlockToInferenceExpr(GeneralBlock.fromBlock(branchCondOp.getCondFalseBlock()));

    var result = new Branch(branchCondOp.getLocation(), condExpr, thenCase, elseCase);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Branch;

      var branchExpr = (Branch) instantiatedExpr;
      assert branchExpr.condition.isPresent();

      var condValue = branchExpr.condition.get().getLeft().getOutputValue();

      var thenExpr = branchExpr.condition.get().getRight();
      assert thenExpr instanceof IBlock;
      var thenBlock = ((IBlock<Expr, SystemFType>) thenExpr).asBlock();

      var elseExpr = branchExpr.block;
      assert elseExpr instanceof IBlock;
      var elseBlock = ((IBlock<Expr, SystemFType>) elseExpr).asBlock();

      var newCfOp = new CfOps.BranchCondOp(op.getLocation(), condValue, thenBlock, elseBlock).getOperation();
      newCfOp.getTemporaryRegion().addTemporaryBlock(thenBlock);
      newCfOp.getTemporaryRegion().addTemporaryBlock(elseBlock);

      return newCfOp;
    });

    return result;
  }

  public static Expr convertAssert(
      Operation op,
      TypeInference engine) {

    CfOps.AssertOp assertOp = (CfOps.AssertOp) op.asOp();

    Expr condExpr = new Var(assertOp.getLocation(), Symbol.of(assertOp.getCond()));
    Optional<Expr> messageExpr = assertOp.getMessage()
        .map(msg -> new Var(assertOp.getLocation(), Symbol.of(msg)));

    var result = new Assert(assertOp.getLocation(), condExpr, messageExpr);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Assert;

      var assertExpr = (Assert) instantiatedExpr;

      var condValue = assertExpr.cond.getOutputValue();

      if (assertExpr.message.isPresent()) {
        var messageValue = assertExpr.message.get().getOutputValue();

        return new CfOps.AssertOp(op.getLocation(), condValue, messageValue).getOperation();
      } else {
        return new CfOps.AssertOp(op.getLocation(), condValue).getOperation();
      }
    });

    return result;
  }
}

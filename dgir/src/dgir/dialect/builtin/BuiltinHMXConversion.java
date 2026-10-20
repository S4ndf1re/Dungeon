package dgir.dialect.builtin;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.debug.Location;
import dgir.core.ir.Operation;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.expr.App;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.builtin.hmx.expr.Var;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.traits.IBlock;
import dgir.core.traits.IGlobal;
import dgir.dialect.builtin.BuiltinOps.IdOp;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.func.FuncOps;
import dgir.dialect.func.FuncTypes;

public final class BuiltinHMXConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinHMXConversion() {
    ConverterRegistry.<HMXExpr, HMXType, TypeInference>addOperatorsToDialect(
        HMXInference.class,
        Pair.of(ProgramOp.class, BuiltinHMXConversion::convertProgramOp),
        Pair.of(IdOp.class, BuiltinHMXConversion::convertIdOp));
  }

  public static HMXExpr convertProgramOp(
      Operation op,
      TypeInference engine) {
    BuiltinOps.ProgramOp programOp = (BuiltinOps.ProgramOp) op.asOp();
    var ops = programOp.getEntryBlock().getOperations();

    var generalBlock = new GeneralBlock();

    // Add Operations in two phases: first functions
    for (var o : ops) {
      if (o.asOp() instanceof FuncOps.FuncOp) {
        // TODO: change generalBlock to allow for assumed types!
        // Specifically for main, this might be needed to always assume () -> () types
        generalBlock.addOperation(o);
      }
    }

    // Then non-functions
    for (var o : ops) {
      if (!(o.asOp() instanceof FuncOps.FuncOp)) {
        generalBlock.addOperation(o);
      }
    }

    generalBlock.addOperation(new FuncOps.CallOp(Location.UNKNOWN, "main", FuncTypes.FuncType.empty()).getOperation());

    var convertedBlock = engine.generalBlockToInferenceExpr(generalBlock);

    convertedBlock.setInstantiateOperationCallback(instantiatedExpr -> {

      assert instantiatedExpr instanceof IBlock;
      @SuppressWarnings("unchecked")
      var instantiatedBlockExpr = (IBlock<HMXExpr, HMXType>) instantiatedExpr;

      var block = instantiatedBlockExpr.asBlock();

      var newOp = new ProgramOp(programOp.getLocation());

      var firstRegion = newOp.getRegion(0).get();
      firstRegion.removeAllBlocks();

      // SAFETY: This filtering is save, as the program op can only ever be the root
      // and hence cannot be polymorphic, ever
      //
      // NOTE: one of the expressions is the call to main, as inserted into the body
      // of the let expressions that represents the programOps body! This must be
      // filtered out
      // Generally, as only global operations are allowed, IGlobal can directly be
      // filtered. As the previous ProgramOp held the same constraints, we can impose
      // them again!
      List<Operation> toRemove = new ArrayList<>();
      for (var operation : block.getOperations()) {
        if (!(operation.asOp() instanceof IGlobal)) {
          toRemove.add(operation);
        }
      }
      for (var operation : toRemove) {
        block.removeOperation(operation);
      }

      firstRegion.addBlock(block);


      return newOp.getOperation();
    });

    return convertedBlock;
  }

  public static HMXExpr convertIdOp(
      Operation op,
      TypeInference engine) {
    BuiltinOps.IdOp idOp = (BuiltinOps.IdOp) op.asOp();

    var param = Symbol.<HMXExpr, HMXType>of(idOp.getOperand());

    var loc = idOp.getLocation();
    var expr = OperationExprConversionUtils.buildIdentityApplication(engine, new Var(loc, param), loc);
    expr.setInstantiateOperationCallback(instantiatedExpr -> {

      assert instantiatedExpr instanceof App;
      var app = (App) instantiatedExpr;

      var returnType = app.getInferredType();
      assert returnType.get().isFullySpecified();

      var appParam = app.args().get(0);
      assert appParam != null;

      var paramValue = appParam.getOutputValue();

      assert paramValue.getType().getAsKnownOrThrow().asParameterizedNominalType()
          .equals(returnType.get().asTypeParameter().getConcrete());

      return new IdOp(idOp.getLocation(), paramValue).getOperation();
    });
    return expr;
  }

}

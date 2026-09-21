package dgir.dialect.builtin;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.debug.Location;
import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.traits.IGlobal;
import dgir.dialect.builtin.BuiltinOps.IdOp;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.func.FuncOps;
import dgir.dialect.func.FuncTypes;

public final class BuiltinHMXConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
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

      assert instantiatedExpr instanceof HMXExpr.ExprLetRec;
      var instantiatedLetExpr = (HMXExpr.ExprLetRec) instantiatedExpr;

      var exprsInBlock = instantiatedLetExpr.getAllChildrenForScopeExpression(instantiatedLetExpr.body());

      var newOp = new ProgramOp(programOp.getLocation());

      // NOTE: one of the expressions is the call to main, as inserted into the body
      // of the let expressions that represents the programOps body! This must be
      // filtered out
      // Generally, as only global operations are allowed, IGlobal can directly be
      // filtered. As the previous ProgramOp held the same constraints, we can impose
      // them again!
      for (var e : exprsInBlock) {
        if (e.getUnderlyingOperation().map(underlyingOp -> underlyingOp.asOp() instanceof IGlobal).orElse(false)) {
          // SAFETY: already checked in the above case
          var eOp = e.getUnderlyingOperation();
          newOp.addOperation(eOp.get());
        }
      }

      return newOp.getOperation();
    });

    return convertedBlock;
  }

  public static HMXExpr convertIdOp(
      Operation op,
      TypeInference engine) {
    BuiltinOps.IdOp idOp = (BuiltinOps.IdOp) op.asOp();

    var param = Symbol.<HMXExpr, HMXType>of(idOp.getOperand());
    var absParam = Symbol.<HMXExpr, HMXType>of(new Value());

    var expr = new HMXExpr.ExprApp(new HMXExpr.ExprAbs(absParam, new HMXExpr.ExprVar(absParam)), new HMXExpr.ExprVar(param));
    expr.setInstantiateOperationCallback(instantiatedExpr -> {

      assert instantiatedExpr instanceof HMXExpr.ExprApp;
      var app = (HMXExpr.ExprApp) instantiatedExpr;

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

package dgir.dialect.builtin;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.debug.Location;
import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFInference;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.expr.App;
import dgir.core.ir.types.builtin.systemf.expr.Var;
import dgir.core.ir.types.builtin.systemf.expr.Let;
import dgir.core.traits.IGlobal;
import dgir.dialect.builtin.BuiltinOps.IdOp;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.func.FuncOps;
import dgir.dialect.func.FuncTypes;

public final class BuiltinSystemFConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinSystemFConversion() {
    ConverterRegistry.<Expr, SystemFType, TypeInference>addOperatorsToDialect(
        SystemFInference.class,
        Pair.of(ProgramOp.class, BuiltinSystemFConversion::convertProgramOp),
        Pair.of(IdOp.class, BuiltinSystemFConversion::convertIdOp));
  }

  public static Expr convertProgramOp(
      Operation op,
      TypeInference engine) {
    ProgramOp programOp = (ProgramOp) op.asOp();
    var ops = programOp.getEntryBlock().getOperations();

    var generalBlock = new GeneralBlock();

    // Add Operations in two phases: first functions
    for (var o : ops) {
      if (o.asOp() instanceof FuncOps.FuncOp) {
        generalBlock.addOperation(o);
      }
    }

    // Then non-functions
    for (var o : ops) {
      if (!(o.asOp() instanceof FuncOps.FuncOp)) {
        generalBlock.addOperation(o);
      }
    }

    generalBlock
        .addOperation(new FuncOps.CallOp(Location.UNKNOWN, "main", FuncTypes.FuncType.empty()).getOperation());

    var convertedBlock = engine.generalBlockToInferenceExpr(generalBlock);

    convertedBlock.setInstantiateOperationCallback(instantiatedExpr -> {

      assert instantiatedExpr instanceof Let;
      var instantiatedLetExpr = (Let) instantiatedExpr;

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

  public static Expr convertIdOp(
      Operation op,
      TypeInference engine) {
    BuiltinOps.IdOp idOp = (BuiltinOps.IdOp) op.asOp();

    var param = Symbol.<Expr, SystemFType>of(idOp.getOperand());
    var absParam = Symbol.<Expr, SystemFType>of(new Value());

    var loc = idOp.getLocation();

    var expr = OperationExprConversionUtils.buildIdentityApplication(
        engine, new Var(loc, param), loc);

    expr.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof App;
      var app = (App) instantiatedExpr;

      var returnType = app.getInferredType();
      assert returnType.get().isFullySpecified();

      var paramValue = app.getAllApplicationParameters().getFirst().getOutputValue();

      return new IdOp(idOp.getLocation(), paramValue).getOperation();
    });
    return expr;
  }
}

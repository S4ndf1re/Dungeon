package dgir.dialect.builtin;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.debug.Location;
import dgir.core.ir.Operation;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.InferenceTree;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.systemf.TypeResult;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.systemf.Expr;
import dgir.core.ir.types.systemf.SystemFInference;
import dgir.core.ir.types.systemf.SystemFType;
import dgir.core.ir.types.systemf.TypeInference;
import dgir.core.ir.types.systemf.Expr.Custom.InferFunction;
import dgir.core.ir.types.systemf.Expr.Custom.InstantiateFunction;
import dgir.core.traits.IGlobal;
import dgir.dialect.builtin.BuiltinOps.IdOp;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.func.FuncOps;
import dgir.dialect.func.FuncTypes;

public final class BuiltinSystemFConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinSystemFConversion() {
    ConverterRegistry.<ExprOrOperator<Expr, SystemFType>, Expr, SystemFType, TypeInference>addOperatorsToDialect(
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

      assert instantiatedExpr instanceof Expr.Let;
      var instantiatedLetExpr = (Expr.Let) instantiatedExpr;

      var exprsInBlock = OperationExprConversionUtils.getAllChildrenForScopeExpression(instantiatedLetExpr,
          instantiatedLetExpr.body());

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

    record IdData(Expr param) {
    }

    // The identity is fully polymorphic: its inferred type is exactly the type of
    // its operand.
    InferFunction<IdData> infFunc = (eng, ctx, data) -> eng.infer(ctx, data.param);

    InstantiateFunction<IdData> instFn = (toInstantiate, eng, env, solution, data) -> new Expr.Custom<IdData>(
        toInstantiate,
        new IdData(data.param.instantiate(eng, env, solution)));

    var expr = new Expr.Custom<IdData>(
        new IdData(new Expr.Var(Symbol.of(idOp.getOperand()))),
        infFunc,
        null,
        null,
        (d) -> List.of(d.param));

    expr.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.Custom;

      @SuppressWarnings("unchecked")
      var custom = (Expr.Custom<IdData>) instantiatedExpr;

      var param = custom.getData().param;

      var paramSymbol = OperationExprConversionUtils.getOutputSymbol(param);
      assert paramSymbol.isPresent();

      return new IdOp(idOp.getLocation(), paramSymbol.get().getValue()).getOperation();
    });
    return expr;
  }
}

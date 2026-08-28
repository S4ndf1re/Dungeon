package dgir.dialect.builtin;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.debug.Location;
import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.algorithmw.AlgorithmWType;
import dgir.core.ir.types.algorithmw.Expr;
import dgir.core.ir.types.algorithmw.TypeInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.traits.IGlobal;
import dgir.core.traits.IHasResult;
import dgir.dialect.builtin.BuiltinOps.IdOp;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.func.FuncOps;
import dgir.dialect.func.FuncTypes;

public final class BuiltinAlgoWConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<ExprOrOperator<Expr, AlgorithmWType>, Expr, AlgorithmWType, TypeInference>addOperatorsToDialect(
        AlgorithmWInference.class,
        Pair.of(ProgramOp.class, BuiltinAlgoWConversion::convertProgramOp),
        Pair.of(IdOp.class, BuiltinAlgoWConversion::convertIdOp));
  }

  public static Expr convertProgramOp(
      Operation op,
      TypeInference engine) {
    BuiltinOps.ProgramOp programOp = (BuiltinOps.ProgramOp) op.asOp();
    var ops = programOp.getEntryBlock().getOperations();

    var fnOps = ops.stream().filter(o -> o.asOp() instanceof FuncOps.FuncOp).toList();
    var nonFnOps = ops.stream().filter(o -> !(o.asOp() instanceof FuncOps.FuncOp)).toList();

    var generalBlock = new GeneralBlock();
    for (var fnOp : fnOps) {
      // TODO: change generalBlock to allow for assumed types!
      // Specifically for main, this might be needed to always assume () -> () types
      generalBlock.addOperation(fnOp);
    }

    for (var nonFnOp : nonFnOps) {
      generalBlock.addOperation(nonFnOp);
    }

    generalBlock.addOperation(new FuncOps.CallOp(Location.UNKNOWN, "main", FuncTypes.FuncType.empty()).getOperation());

    var convertedBlock = engine.generalBlockToInferenceExpr(generalBlock);

    convertedBlock.setInstantiateOperationCallback(instantiatedExpr -> {

      assert instantiatedExpr instanceof Expr.ExprLetRec;
      var instantiatedLetExpr = (Expr.ExprLetRec) instantiatedExpr;

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

    var param = Symbol.<Expr, AlgorithmWType>of(idOp.getOperand());
    var absParam = Symbol.<Expr, AlgorithmWType>of(new Value());

    var expr = new Expr.ExprApp(new Expr.ExprAbs(absParam, new Expr.ExprVar(absParam)), new Expr.ExprVar(param));
    expr.setInstantiateOperationCallback(instantiatedExpr -> {

      assert instantiatedExpr instanceof Expr.ExprApp;
      var app = (Expr.ExprApp) instantiatedExpr;

      var returnType = app.getInferredType();
      assert returnType.isPresent() && returnType.get().isFullySpecified();

      var appParam = app.args.get(0);
      assert appParam != null && appParam.getUnderlyingOperation().isPresent();

      var paramOp = appParam.getUnderlyingOperation().get();
      assert paramOp.asOp() instanceof IHasResult;

      assert paramOp.getOutputOrThrow().getType().isKnown();
      assert paramOp.getOutputOrThrow().getType().getAsKnownOrThrow().asParameterizedNominalType()
          .equals(returnType.get().asTypeParameter().getConcrete());

      return new IdOp(idOp.getLocation(), paramOp.getOutput().get().getValue()).getOperation();
    });
    return expr;
  }

}

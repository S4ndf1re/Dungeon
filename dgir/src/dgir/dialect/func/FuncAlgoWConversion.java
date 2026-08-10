package dgir.dialect.func;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeDialect.TypeInferenceSolver;
import dgir.core.ir.types.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.Expr;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;

public final class FuncAlgoWConversion {
  // NOTE: this is still very error prone, as the functions and ops must match perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.addOperatorsToDialect(AlgorithmWInference.class,
        Pair.of(FuncOps.FuncOp.class, FuncAlgoWConversion::convertFuncOp),
        Pair.of(FuncOps.ReturnOp.class, FuncAlgoWConversion::convertReturnOp),
        Pair.of(FuncOps.CallOp.class, FuncAlgoWConversion::convertCallOp));
  }

  public static <EO extends ExprOrOperator<E>, E extends Expression, T extends dgir.core.ir.types.Type, C> E convertFuncOp(
      Operation op,
      TypeInferenceSolver<EO, E, T, C> engine) {
    FuncOps.FuncOp funcOp = (FuncOps.FuncOp) op.asOp();

    var i = 0;
    ArrayList<Symbol> params = new ArrayList<>();
    while (funcOp.getArgument(i).isPresent()) {
      params.add(Symbol.of(funcOp.getArgument(i).get()));
    }

    var block = GeneralBlock.fromBlock(funcOp.getEntryBlock());
    var blockAsExpr = engine.generalBlockToInferenceExpr(block);

    if (!(blockAsExpr instanceof Expr)) {
      throw new IllegalArgumentException("Invalid, as the engine is not of type algorithm w");
    }

    @SuppressWarnings("unchecked")
    E result = (E) new Expr.ExprAbs(List.copyOf(params), (Expr) blockAsExpr);

    return result;
  }

  @SuppressWarnings("unchecked")
  public static <EO extends ExprOrOperator<E>, E extends Expression, T extends dgir.core.ir.types.Type, C> E convertReturnOp(
      Operation op,
      TypeInferenceSolver<EO, E, T, C> engine) {
    FuncOps.ReturnOp returnOp = (FuncOps.ReturnOp) op.asOp();

    E result = null;
    if (returnOp.getReturnValue().isPresent()) {
      result = (E) new Expr.ExprReturn(new Expr.ExprVar(Symbol.of(returnOp.getReturnValue().get())));
    } else {
      result = (E) new Expr.ExprReturn(new Expr.ExprLit(new Literal.Unit()));
    }

    return result;
  }

  public static <EO extends ExprOrOperator<E>, E extends Expression, T extends dgir.core.ir.types.Type, C> E convertCallOp(
      Operation op,
      TypeInferenceSolver<EO, E, T, C> engine) {
    FuncOps.CallOp callOp = (FuncOps.CallOp) op.asOp();

    @SuppressWarnings("unchecked")
    E result = (E) new Expr.ExprApp(new Expr.ExprVar(Symbol.of(callOp.getCallee())),
        callOp.getOperands().stream()
            .map(operand -> (ExprOrOperator<Expr>) new Expr.ExprVar(Symbol.of(operand.getValueOrThrow()))).toList());

    return result;
  }
}

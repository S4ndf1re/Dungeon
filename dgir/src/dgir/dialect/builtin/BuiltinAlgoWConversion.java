package dgir.dialect.builtin;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.debug.Location;
import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeDialect.TypeInferenceSolver;
import dgir.core.ir.types.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.Expr;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.dialect.builtin.BuiltinOps.IdOp;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.func.FuncOps;
import dgir.dialect.func.FuncTypes;

public final class BuiltinAlgoWConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.addOperatorsToDialect(AlgorithmWInference.class,
        Pair.of(ProgramOp.class, BuiltinAlgoWConversion::convertProgramOp),
        Pair.of(IdOp.class, BuiltinAlgoWConversion::convertIdOp));
  }

  public static <T extends ExprOrOperator<E>, E extends Expression> E convertProgramOp(
      Operation op,
      TypeInferenceSolver<T, E> engine) {
    BuiltinOps.ProgramOp programOp = (BuiltinOps.ProgramOp) op.asOp();
    var ops = programOp.getEntryBlock().getOperations();

    var fnOps = ops.stream().filter(o -> o.asOp() instanceof FuncOps.FuncOp).toList();
    var nonFnOps = ops.stream().filter(o -> !(o.asOp() instanceof FuncOps.FuncOp)).toList();

    var generalBlock = new GeneralBlock();
    for (var fnOp : fnOps) {
      generalBlock.addOperation(fnOp);
    }

    for (var nonFnOp : nonFnOps) {
      generalBlock.addOperation(nonFnOp);
    }

    var convertedBlock = engine.generalBlockToInferenceExpr(generalBlock);
    generalBlock.addOperation(new FuncOps.CallOp(Location.UNKNOWN, "main", FuncTypes.FuncType.empty()).getOperation());

    E result = (E) convertedBlock;
    return result;
  }

  public static <T extends ExprOrOperator<E>, E extends Expression> E convertIdOp(
      Operation op,
      TypeInferenceSolver<T, E> engine) {
    BuiltinOps.IdOp idOp = (BuiltinOps.IdOp) op.asOp();

    var param = Symbol.of(idOp.getOperand());
    var anonParam = Symbol.of(idOp.getResult());

    @SuppressWarnings("unchecked")
    E result = (E) new Expr.ExprApp(new Expr.ExprAbs(anonParam, new Expr.ExprVar(anonParam)), new Expr.ExprVar(param));
    return result;
  }

}

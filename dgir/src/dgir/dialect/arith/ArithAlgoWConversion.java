package dgir.dialect.arith;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.TypeDialect.TypeInferenceSolver;
import dgir.core.ir.types.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.Expr;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.dialect.arith.ArithOps.ConstantOp;

public final class ArithAlgoWConversion {
  // NOTE: this is still very error prone, as the functions and ops must match perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.addOperatorsToDialect(AlgorithmWInference.class,
        Pair.of(ConstantOp.class, ArithAlgoWConversion::convertConstOp));
  }

  public static <T extends ExprOrOperator<E>, E extends Expression> E convertConstOp(Operation op,
      TypeInferenceSolver<T, E> engine) {
    ArithOps.ConstantOp constOp = (ArithOps.ConstantOp) op.asOp();

    @SuppressWarnings("unchecked")
    E result = (E) new Expr.ExprLit(new Literal.Generic(constOp.getResult()));

    return result;
  }

  // TODO(jan): implement missing converters here!
}

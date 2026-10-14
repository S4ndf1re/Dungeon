package dgir.dialect.io;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.ValueOperand;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.algorithmw.expr.Abs;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.expr.App;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.expr.Lit;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;
import dgir.core.ir.types.builtin.algorithmw.expr.Var;
import dgir.core.ir.types.compatibility.ConverterRegistry;

public final class IoAlgoWConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<Expr, AlgorithmWType, TypeInference>addOperatorsToDialect(
        AlgorithmWInference.class,
        Pair.of(IoOps.PrintOp.class, IoAlgoWConversion::convertPrintOp),
        Pair.of(IoOps.ConsoleInOp.class, IoAlgoWConversion::convertConsoleIn));
  }

  public static Expr convertPrintOp(
      Operation op,
      TypeInference engine) {

    var printOp = (IoOps.PrintOp) op.asOp();
    List<Symbol<Expr, AlgorithmWType>> params = printOp.getOperands().stream().map(ValueOperand::getValue)
        .map(Optional::orElseThrow)
        .map(param -> Symbol.<Expr, AlgorithmWType>of(param)).toList();

    List<Symbol<Expr, AlgorithmWType>> paramsCopy = params.stream()
        .map(param -> Symbol.<Expr, AlgorithmWType>of(new Value())).toList();

    var result = new App(op.getLocation(),
        new Abs(op.getLocation(), paramsCopy,
            new Lit(op.getLocation(), new Literal.Unit())),
        params.stream().map(param -> (Expr) new Var(op.getLocation(), param)).toList());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof App;

      var app = (App) instantiatedExpr;

      return new IoOps.PrintOp(op.getLocation(),
          app.args().stream()
              .map(e -> e.getOutputValue()).toList())
          .getOperation();
    });

    return result;
  }

  public static Expr convertConsoleIn(
      Operation op,
      TypeInference engine) {

    IoOps.ConsoleInOp castOp = (IoOps.ConsoleInOp) op.asOp();

    var result = new App(op.getLocation(),
        new Abs(op.getLocation(), List.of(),
            new Lit(op.getLocation(), new Literal.Generic(castOp.getResult()))), List.of());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof App;

      assert instantiatedExpr.getInferredType().isPresent();
      assert instantiatedExpr.getInferredType().get().isFullySpecified();

      var irType = instantiatedExpr.inferredTypeToIrType();

      return new IoOps.ConsoleInOp(op.getLocation(), irType).getOperation();

    });

    return result;
  }
}

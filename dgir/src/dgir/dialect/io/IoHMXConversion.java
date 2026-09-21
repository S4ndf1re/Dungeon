package dgir.dialect.io;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.ValueOperand;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;

public final class IoHMXConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<HMXExpr, HMXType, TypeInference>addOperatorsToDialect(
        HMXInference.class,
        Pair.of(IoOps.PrintOp.class, IoHMXConversion::convertPrintOp),
        Pair.of(IoOps.ConsoleInOp.class, IoHMXConversion::convertConsoleIn));
  }

  public static HMXExpr convertPrintOp(
      Operation op,
      TypeInference engine) {

    var printOp = (IoOps.PrintOp) op.asOp();
    List<Symbol<HMXExpr, HMXType>> params = printOp.getOperands().stream().map(ValueOperand::getValue)
        .map(Optional::orElseThrow)
        .map(param -> Symbol.<HMXExpr, HMXType>of(param)).toList();

    List<Symbol<HMXExpr, HMXType>> paramsCopy = params.stream()
        .map(param -> Symbol.<HMXExpr, HMXType>of(new Value())).toList();

    var result = new HMXExpr.ExprApp(new HMXExpr.ExprAbs(paramsCopy, new HMXExpr.ExprLit(new Literal.Unit())),
        params.stream().map(param -> (HMXExpr) new HMXExpr.ExprVar(param)).toList());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprApp;

      var app = (HMXExpr.ExprApp) instantiatedExpr;

      return new IoOps.PrintOp(op.getLocation(),
          app.args().stream()
              .map(e -> e.getOutputValue()).toList())
          .getOperation();
    });

    return result;
  }

  public static HMXExpr convertConsoleIn(
      Operation op,
      TypeInference engine) {

    IoOps.ConsoleInOp castOp = (IoOps.ConsoleInOp) op.asOp();

    var result = new HMXExpr.ExprApp(
        new HMXExpr.ExprAbs(List.of(), new HMXExpr.ExprLit(new Literal.Generic(castOp.getResult()))), List.of());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprApp;

      assert instantiatedExpr.getInferredType().isPresent();
      assert instantiatedExpr.getInferredType().get().isFullySpecified();

      var irType = instantiatedExpr.inferredTypeToIrType();

      return new IoOps.ConsoleInOp(op.getLocation(), irType).getOperation();

    });

    return result;
  }
}

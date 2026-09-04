package dgir.dialect.io;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.Value;
import dgir.core.ir.ValueOperand;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.algorithmw.AlgorithmWType;
import dgir.core.ir.types.algorithmw.Expr;
import dgir.core.ir.types.algorithmw.TypeInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;

public final class IoAlgoWConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<ExprOrOperator<Expr, AlgorithmWType>, Expr, AlgorithmWType, TypeInference>addOperatorsToDialect(
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

    var result = new Expr.ExprApp(new Expr.ExprAbs(paramsCopy, new Expr.ExprLit(new Literal.Unit())),
        params.stream().map(param -> (Expr) new Expr.ExprVar(param)).toList());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprApp;

      var app = (Expr.ExprApp) instantiatedExpr;

      return new IoOps.PrintOp(op.getLocation(),
          app.args().stream()
              .map(OperationExprConversionUtils::<Expr, AlgorithmWType>getSymbolValue).toList())
          .getOperation();
    });

    return result;
  }

  public static Expr convertConsoleIn(
      Operation op,
      TypeInference engine) {

    IoOps.ConsoleInOp castOp = (IoOps.ConsoleInOp) op.asOp();

    var result = new Expr.ExprApp(
        new Expr.ExprAbs(List.of(), new Expr.ExprLit(new Literal.Generic(castOp.getResult()))), List.of());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprApp;

      assert instantiatedExpr.getInferredType().isPresent();
      assert instantiatedExpr.getInferredType().get().isFullySpecified();
      var retType = instantiatedExpr.getInferredType().get();

      var irType = Type.fromGeneralParameterizedNominalType(retType.asTypeParameter().getConcrete());

      return new IoOps.ConsoleInOp(op.getLocation(), irType).getOperation();

    });

    return result;
  }
}

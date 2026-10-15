package dgir.dialect.io;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.traits.IApplication;
import dgir.core.ir.types.builtin.systemf.Entry;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFInference;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;

public final class IoSystemFConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinSystemFConversion() {
    ConverterRegistry.<Expr, SystemFType, TypeInference>addOperatorsToDialect(
        SystemFInference.class,
        Pair.of(IoOps.PrintOp.class, IoSystemFConversion::convertPrintOp),
        Pair.of(IoOps.ConsoleInOp.class, IoSystemFConversion::convertConsoleIn));
  }

  public static Expr convertPrintOp(
      Operation op,
      TypeInference engine) {

    var printOp = (IoOps.PrintOp) op.asOp();

    // Operand types are string-checked by the op verifier; inference accepts
    // fresh existentials (mirrors the AlgoW/HMX converters).
    var args = printOp.getOperands().stream()
        .map(operand -> engine.Var(printOp.getLocation(), Symbol.<Expr, SystemFType>of(operand.getValueOrThrow())))
        .toList();

    var paramTypes = new ArrayList<SystemFType>();
    for (int i = 0; i < printOp.getOperands().size(); i++) {
      var etvar = new TypeVar<SystemFType>();
      engine.getStartContext().push(new Entry.ETVarBnd(etvar));
      paramTypes.add(new SystemFType.EtVar(etvar));
    }
    paramTypes.add(new SystemFType.Lit(TypeIdent.TYPE_IDENT_UNIT));

    var fnType = engine.ArrowChain(paramTypes.toArray(new SystemFType[0]));

    var result = OperationExprConversionUtils.buildAnnotatedApplication(
        engine,
        args,
        engine.Lit(printOp.getLocation(), new Literal.Unit()),
        fnType,
        printOp.getLocation());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof IApplication;
      @SuppressWarnings("unchecked")
      var argExprs = ((IApplication<Expr, SystemFType>) instantiatedExpr).getAllApplicationParameters();

      return new IoOps.PrintOp(op.getLocation(),
          argExprs.stream()
              .map(Expr::getOutputValue).toList())
          .getOperation();
    });

    return result;
  }

  public static Expr convertConsoleIn(
      Operation op,
      TypeInference engine) {

    var consoleInOp = (IoOps.ConsoleInOp) op.asOp();

    var fnType = engine.ArrowChain(
        new SystemFType.Lit(TypeIdent.TYPE_IDENT_UNIT),
        new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING));

    var result = OperationExprConversionUtils.buildAnnotatedApplication(
        engine,
        List.of(),
        engine.Lit(consoleInOp.getLocation(), new Literal.Generic(consoleInOp.getResult())),
        fnType,
        consoleInOp.getLocation());

    // NOTE: the callback lives on the application node: apps are never
    // beta-reduced, so the node survives substitution of referencing variables.
    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr.getInferredType().isPresent();
      assert instantiatedExpr.getInferredType().get().isFullySpecified();

      var irType = instantiatedExpr.inferredTypeToIrType();

      return new IoOps.ConsoleInOp(op.getLocation(), irType).getOperation();
    });

    return result;
  }
}

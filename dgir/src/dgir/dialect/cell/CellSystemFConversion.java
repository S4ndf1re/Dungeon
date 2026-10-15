package dgir.dialect.cell;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.MaybeType;
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
import dgir.core.ir.types.builtin.systemf.expr.App;

public final class CellSystemFConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do it in the future.
  public static void registerBuiltinSystemFConversion() {
    ConverterRegistry.<Expr, SystemFType, TypeInference>addOperatorsToDialect(
        SystemFInference.class,
        Pair.of(CellOps.CreateCellOp.class, CellSystemFConversion::convertCreateCellOp),
        Pair.of(CellOps.SetCellOp.class, CellSystemFConversion::convertSetCellOp));
  }

  private static SystemFType freshEtVar(TypeInference engine) {
    var etvar = new TypeVar<SystemFType>();
    engine.getStartContext().push(new Entry.ETVarBnd(etvar));
    return new SystemFType.EtVar(etvar);
  }

  public static Expr convertCreateCellOp(
      Operation op,
      TypeInference engine) {

    var cellOp = (CellOps.CreateCellOp) op.asOp();

    Expr body;
    SystemFType resultEtVar = freshEtVar(engine);
    if (cellOp.getOperand().isPresent() && cellOp.getOperand().get().isPresent()) {
      // A cell created from an initial value exposes exactly the type of its
      // operand: the identity function a -> a applied to the operand.
      body = engine.Var(cellOp.getLocation(),
          Symbol.<Expr, SystemFType>of(cellOp.getOperand().get().get()));
    } else {
      // Operand-less creation requires the result type to be known; the typed
      // literal carries the declared cell type directly.
      body = engine.Lit(cellOp.getLocation(), new Literal.Generic(cellOp.getResult()));
    }

    var fnType = engine.ArrowChain(resultEtVar, resultEtVar);
    if (cellOp.getOperand().isEmpty() || cellOp.getOperand().get().isEmpty()) {
      // Zero-arg creation: the unit input placeholder is applied implicitly.
      fnType = engine.ArrowChain(new SystemFType.Lit(TypeIdent.TYPE_IDENT_UNIT), resultEtVar);
    }

    var result = OperationExprConversionUtils.buildAnnotatedApplication(
        engine,
        cellOp.getOperand().isPresent() && cellOp.getOperand().get().isPresent()
            ? List.of(engine.Var(cellOp.getLocation(),
                Symbol.<Expr, SystemFType>of(cellOp.getOperand().get().get())))
            : List.of(),
        body,
        fnType,
        cellOp.getLocation());

    // NOTE: the callback lives on the application node: apps are never
    // beta-reduced, so the node survives substitution of referencing variables.
    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof App;
      var argExprs = ((App) instantiatedExpr).getAllApplicationParameters();

      if (argExprs.isEmpty()) {
        return new CellOps.CreateCellOp(cellOp.getLocation(),
            MaybeType.of(instantiatedExpr.inferredTypeToIrType())).getOperation();
      }
      return new CellOps.CreateCellOp(cellOp.getLocation(),
          argExprs.getFirst().getOutputValue()).getOperation();
    });

    return result;
  }

  public static Expr convertSetCellOp(
      Operation op,
      TypeInference engine) {

    var setCellOp = (CellOps.SetCellOp) op.asOp();

    // Construct the function type cell -> value -> unit.
    var fnType = engine.ArrowChain(
        freshEtVar(engine),
        freshEtVar(engine),
        new SystemFType.Lit(TypeIdent.TYPE_IDENT_UNIT));

    var result = OperationExprConversionUtils.buildAnnotatedApplication(
        engine,
        setCellOp.getOperands().stream()
            .map(aOp -> engine.Var(setCellOp.getLocation(), Symbol.<Expr, SystemFType>of(aOp.getValueOrThrow())))
            .toList(),
        engine.Lit(setCellOp.getLocation(), new Literal.Unit()),
        fnType,
        setCellOp.getLocation());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof IApplication;
      @SuppressWarnings("unchecked")
      var argExprs = ((IApplication<Expr, SystemFType>) instantiatedExpr).getAllApplicationParameters();

      return new CellOps.SetCellOp(setCellOp.getLocation(),
          argExprs.get(0).getOutputValue(),
          argExprs.get(1).getOutputValue()).getOperation();
    });

    return result;
  }
}

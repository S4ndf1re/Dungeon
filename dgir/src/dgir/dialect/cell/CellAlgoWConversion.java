package dgir.dialect.cell;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.MaybeType;
import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;

public final class CellAlgoWConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<Expr, AlgorithmWType, TypeInference>addOperatorsToDialect(
        AlgorithmWInference.class,
        Pair.of(CellOps.CreateCellOp.class, CellAlgoWConversion::convertCreateCellOp),
        Pair.of(CellOps.SetCellOp.class, CellAlgoWConversion::convertSetCellOp));
  }

  /**
   * A cell created from an initial value is the identity function applied to
   * that value: the cell exposes exactly the type of its operand.
   *
   * <p>
   * A cell created from a declared type is a literal of that type, as there is
   * no operand to infer the type from. The declared type must therefore be
   * known at conversion time.
   */
  public static Expr convertCreateCellOp(
      Operation op,
      TypeInference engine) {
    var cellOp = (CellOps.CreateCellOp) op.asOp();

    if (cellOp.getOperand().isPresent() && cellOp.getOperand().get().isPresent()) {
      var operand = cellOp.getOperand().get().get();
      var param = Symbol.<Expr, AlgorithmWType>of(operand);
      var absParam = Symbol.<Expr, AlgorithmWType>of(new Value());

      var result = new Expr.ExprApp(
          new Expr.ExprAbs(absParam, new Expr.ExprVar(absParam)),
          new Expr.ExprVar(param));

      result.setInstantiateOperationCallback(instantiatedExpr -> {
        assert instantiatedExpr instanceof Expr.ExprApp;
        var app = (Expr.ExprApp) instantiatedExpr;

        var returnType = app.getInferredType();
        assert returnType.isPresent();
        assert returnType.get().isFullySpecified();

        var operandValue = app.args().get(0).getOutputValue();

        assert operandValue.getType().getAsKnownOrThrow().asParameterizedNominalType()
            .equals(returnType.get().asTypeParameter().getConcrete());

        return new CellOps.CreateCellOp(cellOp.getLocation(), operandValue).getOperation();
      });

      return result;
    }

    var resultValue = cellOp.getResult();
    if (resultValue.getType().isUnknown()) {
      throw new IllegalArgumentException(
          "CreateCellOp without an operand must have a known result type, as the type cannot"
              + " be inferred: %s".formatted(op));
    }

    var literalResult = new Expr.ExprApp(
        new Expr.ExprAbs(List.of(), new Expr.ExprLit(new Literal.Generic(resultValue))),
        List.of());

    literalResult.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprApp;
      var app = (Expr.ExprApp) instantiatedExpr;

      assert app.getInferredType().isPresent();
      assert app.getInferredType().get().isFullySpecified();

      return new CellOps.CreateCellOp(cellOp.getLocation(), MaybeType.of(app.inferredTypeToIrType()))
          .getOperation();
    });

    return literalResult;
  }

  /**
   * Assigning to a cell is a pure effect of unit type: the op carries no
   * result, so it is modeled as an abstraction over cell and assignee
   * returning unit, applied to both operand expressions. Type validation
   * (cell type equals assignee type) happens within the operation
   * verification!
   */
  public static Expr convertSetCellOp(
      Operation op,
      TypeInference engine) {
    var setCellOp = (CellOps.SetCellOp) op.asOp();

    var cell = Symbol.<Expr, AlgorithmWType>of(setCellOp.getLhs());
    var assignee = Symbol.<Expr, AlgorithmWType>of(setCellOp.getRhs());
    var cellParam = Symbol.<Expr, AlgorithmWType>of(new Value());
    var assigneeParam = Symbol.<Expr, AlgorithmWType>of(new Value());

    var result = new Expr.ExprApp(
        new Expr.ExprAbs(List.of(cellParam, assigneeParam), new Expr.ExprLit(new Literal.Unit())),
        List.of(new Expr.ExprVar(cell), new Expr.ExprVar(assignee)));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprApp;
      var app = (Expr.ExprApp) instantiatedExpr;

      var cellValue = app.args().get(0).getOutputValue();
      var assigneeValue = app.args().get(1).getOutputValue();

      return new CellOps.SetCellOp(setCellOp.getLocation(), cellValue, assigneeValue).getOperation();
    });

    return result;
  }
}

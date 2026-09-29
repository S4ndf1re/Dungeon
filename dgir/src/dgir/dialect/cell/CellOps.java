package dgir.dialect.cell;

import java.util.List;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;

import dgir.core.debug.Location;
import dgir.core.ir.Dialect;
import dgir.core.ir.MaybeType;
import dgir.core.ir.Op;
import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.traits.IBinaryOperands;
import dgir.core.traits.IHasResult;
import dgir.core.traits.INoResult;
import dgir.core.traits.IZeroOrOneOperand;
import dgir.dialect.cell.CellTypes.CellType;

public sealed interface CellOps {

  abstract class CellOp extends Op {
    CellOp() {
      super();
    }

    @Override
    public @NotNull Class<? extends Dialect> getDialect() {
      return CellDialect.class;
    }
  }

  public final class CreateCellOp extends CellOp implements CellOps, IHasResult, IZeroOrOneOperand {

    @Override
    public @NotNull String getIdent() {
      return "cell.create";
    }

    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return op -> {
        CreateCellOp cellOp = op.as(CreateCellOp.class).orElseThrow();

        if (cellOp.getOperand().isPresent() && cellOp.getOperand().get().isPresent()) {
          var opValue = cellOp.getOperand().get().get();
          var outputType = (CellType) cellOp.getResult().getType().getAsKnownOrThrow();
          if (!opValue.getType().equals(outputType.getWrappedType())) {
            throw new IllegalArgumentException(
                "operand type must equal output type: %s != %s".formatted(opValue.getType(),
                    outputType.getWrappedType()));
          }
        }

        return true;
      };
    }

    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Op> getOpFactory() {
      return op -> new CreateCellOp().setOperation(op);
    }

    private CreateCellOp() {
    }

    public CreateCellOp(Location loc) {
      setOperation(Operation.Create(loc, this, null, null, CellType.of()));
    }

    public CreateCellOp(Location loc, MaybeType type) {
      setOperation(Operation.Create(loc, this, null, null, CellType.of(type)));
    }

    public CreateCellOp(Location loc, Value operand) {
      setOperation(Operation.Create(loc, this, List.of(operand), null, CellType.of(operand.getType())));
    }
  }

  public final class SetCellOp extends CellOp implements CellOps, INoResult, IBinaryOperands {

    @Override
    public @NotNull String getIdent() {
      return "cell.set";
    }

    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return op -> {
        SetCellOp setCell = op.as(SetCellOp.class).orElseThrow();

        if (setCell.getLhs().getType().isUnknown()) {
          throw new IllegalArgumentException("Cell value must be known");
        }

        if (!(setCell.getLhs().getType().getAsKnownOrThrow() instanceof CellType)) {
          throw new IllegalArgumentException("LHS operand must be of type CellType");
        }

        if (setCell.getRhs().getType().isUnknown()) {
          throw new IllegalArgumentException("RHS operands value must be known");
        }

        var cellType = (CellType) setCell.getLhs().getType().getAsKnownOrThrow();
        if (cellType.getWrappedType().isUnknown()) {
          throw new IllegalArgumentException("Cells type parameter must be known");
        }

        if (cellType.getWrappedType().getAsKnownOrThrow().equals(setCell.getRhs().getType())) {
          throw new IllegalArgumentException("Wrapped Cell type must equal rhs type: %s != %s"
              .formatted(cellType.getWrappedType(), setCell.getRhs().getType()));
        }

        return true;
      };
    }

    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Op> getOpFactory() {
      return op -> new SetCellOp().setOperation(op);
    }

    private SetCellOp() {
    }

    public SetCellOp(Location loc, Value cell, Value assignee) {
      setOperation(Operation.Create(loc, this, List.of(cell, assignee), null, null));
    }

  }

}

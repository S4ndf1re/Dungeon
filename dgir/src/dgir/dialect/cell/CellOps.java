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

  /**
   * Create a new cell. This is the only way to introduce a Value into a block.
   *
   * This replaced the use of setOutputValue, which caused type inference
   * problems.
   * The only way to set output values outside of serialization is to use
   * SetCellOp.
   *
   * <p>
   * While this operation seems like a special cell type, this operation is no
   * more than any other IR-value.
   * As values are mutable within the IR, there is no need to implement a special
   * logic. This also makes it possible to use SetCellOP with any other ordinary
   * Value, instead of relying on setOutputValue
   */
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
          var outputType = cellOp.getResult().getType().getAsKnownOrThrow();
          if (!opValue.getType().equals(outputType)) {
            throw new IllegalArgumentException(
                "operand type must equal output type: %s != %s".formatted(opValue.getType(),
                    outputType));
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
      setOperation(Operation.Create(loc, this, null, null, MaybeType.of()));
    }

    public CreateCellOp(Location loc, MaybeType type) {
      setOperation(Operation.Create(loc, this, null, null, MaybeType.of(type)));
    }

    public CreateCellOp(Location loc, Value operand) {
      setOperation(Operation.Create(loc, this, List.of(operand), null, MaybeType.of(operand.getType())));
    }
  }

  /**
   * Set Cell op is the replacement of setOutputValue.
   * setOutputValue was the previous IR mechanism to introduce changeable
   * variables.
   * However, this caused problems in the type inference algorithms, due to
   * recursive let bindings.
   * Additionally, this new way enables a higher similarity to true SSA IRs, while
   * still beeing flexible.
   *
   * <p>
   * While this operation is designed to work with CreateCellOp (introduction of
   * values within a block), the operation also works with all value types!
   */
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

        if (setCell.getRhs().getType().isUnknown()) {
          throw new IllegalArgumentException("RHS operands value must be known");
        }

        var cellType = setCell.getLhs().getType().getAsKnownOrThrow();

        if (!cellType.equals(setCell.getRhs().getType())) {
          throw new IllegalArgumentException("Wrapped Cell type must equal rhs type: %s != %s"
              .formatted(cellType, setCell.getRhs().getType()));
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

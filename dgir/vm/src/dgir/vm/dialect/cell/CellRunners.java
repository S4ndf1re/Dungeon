package dgir.vm.dialect.cell;

import org.jetbrains.annotations.NotNull;

import dgir.core.ir.Operation;
import dgir.dialect.cell.CellOps;
import dgir.vm.api.Action;
import dgir.vm.api.OpRunner;
import dgir.vm.api.State;

public sealed interface CellRunners {

  final class CreateCellRunner extends OpRunner implements CellRunners {
    public CreateCellRunner() {
      super(CellOps.CreateCellOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      CellOps.CreateCellOp createOp = op.as(CellOps.CreateCellOp.class).orElseThrow();
      if (createOp.getOperand().isPresent() && createOp.getOperand().get().isPresent()) {
        var operandValue = createOp.getOperand().get().get();
        state.setValue(createOp.getResult(), state.getValueOrThrow(operandValue));
      } else {
        // Do nothing in this case, the first set call will actually set this value!
      }

      return Action.Next();
    }

  }

  final class SetCellRunner extends OpRunner implements CellRunners {
    public SetCellRunner() {
      super(CellOps.SetCellOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      CellOps.SetCellOp setOp = op.as(CellOps.SetCellOp.class).orElseThrow();

      state.setValue(setOp.getLhs(), state.getValueOrThrow(setOp.getRhs()));

      return Action.Next();
    }

  }
}

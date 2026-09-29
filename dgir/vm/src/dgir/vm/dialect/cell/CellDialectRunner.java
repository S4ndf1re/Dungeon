package dgir.vm.dialect.cell;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import dgir.core.ir.Dialect;
import dgir.dialect.cell.CellDialect;
import dgir.vm.api.DialectRunner;
import dgir.vm.api.OpRunner;

public class CellDialectRunner extends DialectRunner {
  private static CellDialectRunner instance;

  public static CellDialectRunner get() {
    synchronized (CellDialectRunner.class) {
      if (instance == null) {
        instance = new CellDialectRunner();
      }
    }
    return instance;
  }

  @Override
  public @NotNull Dialect getDialect() {
    return CellDialect.get();
  }

  @Override
  public @NotNull List<@NotNull OpRunner> allRunners() {
    return allRunners(CellRunners.class);
  }

}

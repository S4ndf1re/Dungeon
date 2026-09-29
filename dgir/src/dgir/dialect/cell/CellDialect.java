package dgir.dialect.cell;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import dgir.core.ir.AttributeDescriptor;
import dgir.core.ir.Dialect;
import dgir.core.ir.Op;
import dgir.core.ir.TypeDescriptor;

public class CellDialect extends Dialect {
  public static CellDialect instance;

  public static CellDialect get() {
    synchronized (CellDialect.class) {
      if (instance == null) {
        instance = new CellDialect();
      }

      return instance;
    }
  }

  @Override
  public @NotNull String getNamespace() {
    return "cell";
  }

  @Override
  public @NotNull @Unmodifiable List<Op> allOps() {
    return allOpsFromSealedInterface(CellOps.class);
  }

  @Override
  public @NotNull @Unmodifiable List<TypeDescriptor> allTypes() {
    return allTypesFromSealedInterface(CellTypes.CellTypeDescriptor.class);
  }

  @Override
  public @NotNull @Unmodifiable List<AttributeDescriptor> allAttributes() {
    return List.of();
  }

}

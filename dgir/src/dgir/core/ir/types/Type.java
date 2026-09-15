package dgir.core.ir.types;

import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;

public abstract class Type {

  public abstract GeneralTypeParameter asTypeParameter();

  public abstract boolean isFullySpecified();

  public final dgir.core.ir.Type toIrType() {
    assert this.isFullySpecified();
    return dgir.core.ir.Type.fromGeneralParameterizedNominalType(this.asTypeParameter().getConcrete());
  }
}

package dgir.core.ir.types;

import java.util.Set;

import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;

public abstract class Type<T extends Type<T>> {

  public abstract GeneralTypeParameter asTypeParameter();

  public abstract boolean isFullySpecified();

  public abstract Set<TypeVar<T>> freeTypeVars();

  public abstract boolean occursCheck(TypeVar<T> tyVar);

  public abstract void occursCheckAjustLevel(TypeVar<T> tyVar);

  /**
   * For some algorithms, union finds are used to track solutions. In order to
   * resolve the actual solution, make sure to call this function for the
   * corresponding algorithms
   *
   * @return custom logic may use union finds to resolve!
   */
  public abstract T deref();

  public final dgir.core.ir.Type toIrType() {
    assert this.isFullySpecified();
    return dgir.core.ir.Type.fromGeneralParameterizedNominalType(this.asTypeParameter().getConcrete());
  }
}

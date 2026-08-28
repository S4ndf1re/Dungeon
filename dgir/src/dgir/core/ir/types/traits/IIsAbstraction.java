package dgir.core.ir.types.traits;

import java.util.List;

import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.Type;

public interface IIsAbstraction<E extends Expression<E, T>, T extends Type> {
  /**
   * This is meant for abstractions / Arrow functions to return a list of all
   * abstracted symbols.
   *
   * <p>
   * Only true abstractions are expected to return values. True abstractions are
   * only found in the form of [symbol] -> body functions!
   *
   * @return a list of all abstracted symbols
   */
  public List<Symbol<E, T>> getAbstractionsOverSymbols();

  public E getAbstractionBody();
}

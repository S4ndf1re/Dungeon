package dgir.core.ir.types.traits;

import java.util.ArrayList;
import java.util.List;

import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.Type;

public interface IAbstraction<E extends Expression<E, T>, T extends Type> {
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

  @SuppressWarnings("unchecked")
  public default List<Symbol<E, T>> getAllAbstractedParamters() {
    ArrayList<Symbol<E, T>> params = new ArrayList<>();

    IAbstraction<E, T> current = this;

    while (true) {
      var abstractedOver = current.getAbstractionsOverSymbols();

      if (abstractedOver.isEmpty()) {
        break;
      }

      params.addAll(abstractedOver);

      var body = current.getAbstractionBody();

      if (body instanceof IAbstraction) {
        current = (IAbstraction<E, T>) body;
      } else {
        break;
      }
    }

    return List.copyOf(params);

  }
}

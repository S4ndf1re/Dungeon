package dgir.core.ir.types.traits;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Type;

public interface IApplication<E extends Expression<E, T>, T extends Type<T>> {
  /**
   * This is meant for applications / Arrow function applications(calls) to return
   * a list of all
   * parameter expressions.
   *
   * <p>
   * Only true applications are expected to return values. True abstractions are
   * only found in the form of ([symbol] -> body)([expr]) functions!
   *
   * @return a list of all application parameter expressions
   */
  public List<E> getApplications();

  public E getFunction();

  @SuppressWarnings("unchecked")
  public default List<E> getAllApplicationParameters() {
    ArrayDeque<E> params = new ArrayDeque<>();

    IApplication<E, T> current = this;

    while (true) {

      var applicationParams = current.getApplications();

      // Prepend here, as nested function applications are in reverse order.
      // For example:
      // Func type: fn = a -> b -> c -> d
      // Func application ((((fn a) b) c) d)
      // Hence the outer most application actually contains parameter d, but a is
      // expected! This problem is mitigated a bit for multi paramter function
      // applications, but those cannot be guaranteed by all Type Systems.
      prependAll(params, applicationParams);

      var func = current.getFunction();
      if (func instanceof IApplication) {
        current = (IApplication<E, T>) func;
      } else {
        break;
      }
    }

    return List.copyOf(params);
  }

  private static <E> void prependAll(Deque<E> deque, List<E> chunk) {
    for (int i = chunk.size() - 1; i >= 0; i--) {
      deque.addFirst(chunk.get(i));
    }
  }
}

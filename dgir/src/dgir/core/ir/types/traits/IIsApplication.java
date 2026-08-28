package dgir.core.ir.types.traits;

import java.util.List;

import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Type;

public interface IIsApplication<E extends Expression<E, T>, T extends Type> {
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
}

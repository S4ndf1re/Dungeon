package dgir.core.ir.types.traits;

import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Type;

public interface IExpressionCell<E extends Expression<E, T>, T extends Type> {

  public E unwrap();

  public void replaceIfMatches(E reference, E replacement);
}

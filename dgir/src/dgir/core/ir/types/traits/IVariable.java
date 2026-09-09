package dgir.core.ir.types.traits;

import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.Type;

public interface IVariable<E extends Expression<E, T>, T extends Type> {
  /**
   * When an {@link Expression} is a variable that is just a reference to another
   * {@link Symbol} within the algorithm specific environment,
   * this function is expected to return the {@link Symbol} to that reference.
   *
   * <p>
   * For an {@link Expression} like {@link ExprVar}, this is a trivial {@link Env}
   * lookup.
   * However, custom
   * {@link Expression}s may also provide this functionality in some way, and
   * hence must
   * expose the potentially referenced {@link Symbol}.
   *
   * @return `Some(var)` if `var` is a variable bound by this expression
   */
  public Symbol<E, T> getReferencedVariable();

}

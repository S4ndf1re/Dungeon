package dgir.core.ir.types.builtin.algorithmw;

import dgir.core.debug.Location;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.builtin.algorithmw.expr.Cell;
import dgir.core.ir.types.traits.IInstantiable;

public abstract class Expr extends Expression<Expr, AlgorithmWType>
    implements IInstantiable<Expr, AlgorithmWType, Subst, TypeInference> {

  protected Expr() {
  }

  protected Expr(Location loc) {
    super(loc);
  }

  protected Expr(Expr other) {
    super(other);
  }

  // Make sure, that exprs always equals via object reference (needed for in-set
  // storage!)
  @Override
  public boolean equals(Object obj) {
    return obj instanceof Expr && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public Expr getCellFor(Expr origin, Expr assignment) {
    return new Cell(origin, assignment);
  }

  /**
   * Infer the type of the expression. This method MUST be implemented for every
   * {@link Expr}.
   * After inferring the type (the return value), the engine automatically stores
   * the inferred type within the inferred expression.
   *
   * @param engine
   * @param env
   * @return the inferred {@link AlgorithmWType}
   */
  public abstract InferResult infer(TypeInference engine, Env env);
}

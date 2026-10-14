package dgir.core.ir.types.builtin.hmx;

import dgir.core.debug.Location;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.builtin.hmx.expr.Cell;
import dgir.core.ir.types.traits.IInstantiable;

public abstract class HMXExpr extends Expression<HMXExpr, HMXType>
    implements IInstantiable<HMXExpr, HMXType, Subst, TypeInference> {

  protected HMXExpr() {
  }

  protected HMXExpr(Location loc) {
    super(loc);
  }

  protected HMXExpr(HMXExpr other) {
    super(other);
  }

  // Make sure, that exprs always equals via object reference (needed for in-set
  // storage!)
  @Override
  public boolean equals(Object obj) {
    return obj instanceof HMXExpr && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public HMXExpr getCellFor(HMXExpr origin, HMXExpr assignment) {
    return new Cell(origin, assignment);
  }

  /**
   * Infer the type of the expression. This method MUST be implemented for every
   * {@link HMXExpr}.
   * After inferring the type (the return value), the engine automatically stores
   * the inferred type within the inferred expression.
   *
   * @param engine
   * @param env
   * @return the resulting {@link Constraint} of the inference
   */
  public abstract GenerateResult generate(TypeInference engine, Env env, HMXType type);
}

package dgir.core.ir.types.builtin.systemf;

import dgir.core.ir.types.traits.IInstantiable;
import dgir.core.debug.Location;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.builtin.systemf.expr.Cell;

/**
 * Expressions that are valid for the SytemF Type System. All needed methods for
 * inference and type checking are implemented here
 */
public abstract class Expr extends Expression<Expr, SystemFType>
    implements IInstantiable<Expr, SystemFType, Context, TypeInference> {

  public Expr() {
    super();
  }

  public Expr(Location loc) {
    super(loc);
  }

  public Expr(Expr other) {
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

  public abstract TypeResult infer(
      TypeInference engine,
      Context ctx);

  public CheckResult check(
      TypeInference engine,
      Context ctx,
      SystemFType ty) {
    if (ty instanceof SystemFType.ForAll forall) {
      var newCtx = ctx.copy();
      var mark = new Entry.Mark();
      newCtx.push(mark);
      newCtx.push(new Entry.TVarBnd(forall.boundVar));

      CheckResult checkRes = engine.check(newCtx, this, forall.body);
      Break3Result breakRes = checkRes.ctx().break3(
          entry -> entry instanceof Entry.Mark m &&
              m.equals(mark));

      Context finalCtx = new Context(breakRes.left(), checkRes.ctx());
      return new CheckResult(finalCtx);
    }

    var inferred = engine.infer(ctx, this);
    var inferredApplied = inferred.ctx().apply(inferred.type());
    var typeApplied = inferred.ctx().apply(ty);
    var subtyped = engine.subtype(inferred.ctx(), inferredApplied, typeApplied);
    return new CheckResult(subtyped.ctx());
  }

}

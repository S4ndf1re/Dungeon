package dgir.core.ir.types.builtin.hmx.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.Env;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Subst;
import dgir.core.ir.types.builtin.hmx.TypeInference;

public final class Ann extends HMXExpr {
  private final HMXExpr expr;
  private final HMXType type;

  public HMXExpr expr() {
    return this.expr.unwrapOrThis();
  }

  public HMXType type() {
    return this.type;
  }

  public Ann(HMXExpr expr, HMXType type) {
    this.expr = expr;
    this.type = type;
  }

  public Ann(Location loc, HMXExpr expr, HMXType type) {
    super(loc);
    this.expr = expr;
    this.type = type;
  }

  public Ann(Ann other) {
    super(other);
    this.expr = other.expr;
    this.type = other.type;
  }

  public Ann(Ann other, HMXExpr expr, HMXType type) {
    super(other);
    this.expr = expr;
    this.type = type;
  }

  @Override
  public List<HMXExpr> getChildren() {
    return List.of(this.expr);
  }

  @Override
  public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
    GenerateResult res = engine.generate(expr, env, type);

    // var inner = new Constraint.And(res.constr(), new Constraint.Sub(this.type,
    // type, true));
    var inner = new Constraint.And(new Constraint.Sub(this.type, type), res.constr());

    // Collect all possibly free type variables and let them exist as if introduced
    // by the annotation!
    var ftv = List.copyOf(this.type.deref().freeTypeVars());
    if (ftv.isEmpty()) {
      return new GenerateResult(inner);
    }

    return new GenerateResult(new Constraint.Exists(ftv, inner));
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Ann ann && this.expr.equals(ann.expr) && this.type.equals(ann.type)
        && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.expr, this.type, super.hashCode());
  }

  @Override
  public void replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
    this.expr.replaceSymbol(original, replacement);
  }

  @Override
  public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
    return this.expr.containsSymbol(symbol);
  }

  @Override
  public HMXExpr copy() {
    return new Ann(this);
  }

  @Override
  public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
    // Simply return the inner as fully instantiated!
    // Since the annotation is getting abstracted away, keep the information about
    // scope locality that is assumed for this expression!
    var innerExpr = this.expr.copy();
    innerExpr.setParentScopeExpression(this.getParentScopeExpr(), this.getParentScopePosition());

    return innerExpr.instantiate(engine, env, solution);
  }
}

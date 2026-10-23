package dgir.core.ir.types.builtin.systemf.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.builtin.systemf.TypeResult;
import dgir.core.ir.types.builtin.systemf.TypeInference;

public final class While extends Expr {
  public final Expr cond;
  public final Expr body;

  public While(Expr cond, Expr body) {
    this.cond = cond;
    this.body = body;

  }

  public While(Location loc, Expr cond, Expr body) {
    super(loc);
    this.cond = cond;
    this.body = body;
  }

  public While(While other) {
    super(other);
    this.cond = other.cond;
    this.body = other.body;
  }

  public While(While other, Expr cond, Expr body) {
    super(other);
    this.cond = cond;
    this.body = body;
  }

  @Override
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
      Context solutionContext) {
    return new While(this,
        this.cond.instantiate(engine, env, solutionContext),
        this.body.instantiate(engine, env, solutionContext));
  }

  @Override
  public TypeResult infer(TypeInference engine, Context ctx) {
    var condRes = engine.infer(ctx, this.cond);
    // NOTE: this is important! The DGIR assumes that the while condition is not
    // actually a truth value, but continue and end operation instead!
    // In order to comply with this decision,
    // make sure to actually unify with UNIT over BOOL
    engine.subtype(ctx, condRes.type(), new SystemFType.Lit(TypeIdent.TYPE_IDENT_UNIT));

    var bodyRes = engine.infer(ctx, this.body);
    engine.subtype(ctx, bodyRes.type(), new SystemFType.Lit(TypeIdent.TYPE_IDENT_UNIT));

    return new TypeResult(bodyRes.type().deref(), ctx);
  }

  @Override
  public List<Expr> getChildren() {
    return List.of(this.cond, this.body);
  }

  @Override
  public void replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
    this.cond.replaceSymbol(original, replacement);
    this.body.replaceSymbol(original, replacement);
  }


  @Override
  public boolean equals(Object obj) {
    return obj instanceof While other && this.cond.equals(other.cond) && this.body.equals(other.body)
        && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.cond, this.body, super.hashCode());
  }

  @Override
  public Expr copy() {
    return new While(this);
  }
}

package dgir.core.ir.types.builtin.hmx.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Env;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.builtin.hmx.Subst;
import dgir.core.ir.types.builtin.hmx.TypeInference;

public final class While extends HMXExpr {
  public final HMXExpr cond;
  public final HMXExpr body;

  public While(HMXExpr cond, HMXExpr body) {
    this.cond = cond;
    this.body = body;

  }

  public While(Location loc, HMXExpr cond, HMXExpr body) {
    super(loc);
    this.cond = cond;
    this.body = body;
  }

  public While(While other) {
    super(other);
    this.cond = other.cond;
    this.body = other.body;
  }

  public While(While other, HMXExpr cond, HMXExpr body) {
    super(other);
    this.cond = cond;
    this.body = body;
  }

  @Override
  public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env,
      Subst solutionContext) {
    return new While(this,
        this.cond.instantiate(engine, env, solutionContext),
        this.body.instantiate(engine, env, solutionContext));
  }

  @Override
  public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
    var condTyVar = new TypeVar<HMXType>();
    var bodyTyVar = new TypeVar<HMXType>();
    var condType = new HMXType.Var(this.getLocation(), condTyVar);
    var bodyType = new HMXType.Var(this.getLocation(), bodyTyVar);

    GenerateResult resCond = engine.generate(this.cond, env, condType);
    GenerateResult resBody = engine.generate(this.body, env, bodyType);

    return new GenerateResult(new Constraint.Exists(List.of(condTyVar, bodyTyVar),
        new Constraint.And(resCond.constr(), resBody.constr(),
            new Constraint.Equal(condType, new HMXType.LitType(this.getLocation(), TypeIdent.TYPE_IDENT_UNIT)),
            new Constraint.Equal(bodyType, new HMXType.LitType(this.getLocation(), TypeIdent.TYPE_IDENT_UNIT)),
            new Constraint.Equal(type, new HMXType.LitType(this.getLocation(), TypeIdent.TYPE_IDENT_UNIT)))));
  }

  @Override
  public List<HMXExpr> getChildren() {
    return List.of(this.cond, this.body);
  }

  @Override
  public void replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
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
  public HMXExpr copy() {
    return new While(this);
  }

  @Override
  public String toString() {
    return "while " + this.cond + " { " + this.body + " }";
  }
}

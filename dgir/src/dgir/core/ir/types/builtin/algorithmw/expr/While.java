package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Env;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.builtin.algorithmw.InferResult;
import dgir.core.ir.types.builtin.algorithmw.Subst;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;

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
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env,
      Subst solutionContext) {
    return new While(this,
        this.cond.instantiate(engine, env, solutionContext),
        this.body.instantiate(engine, env, solutionContext));
  }

  @Override
  public InferResult infer(TypeInference engine, Env env) {
    var condRes = engine.infer(this.cond, env);
    // NOTE: this is important! The DGIR assumes that the while condition is not
    // actually a truth value, but continue and end operation instead!
    // In order to comply with this decision,
    // make sure to actually unify with UNIT over BOOL
    engine.unify(condRes.type(), new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_UNIT));

    var bodyRes = engine.infer(this.body, env);
    engine.unify(bodyRes.type(), new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_UNIT));

    return new InferResult(bodyRes.type().deref());
  }

  @Override
  public List<Expr> getChildren() {
    return List.of(this.cond, this.body);
  }

  @Override
  public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
    return new While(this,
        this.cond.replaceSymbol(original, replacement),
        this.body.replaceSymbol(original, replacement));
  }

  @Override
  public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
    return this.cond.containsSymbol(symbol) || this.body.containsSymbol(symbol);
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

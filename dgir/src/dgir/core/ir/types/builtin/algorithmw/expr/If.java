package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

public final class If extends Expr {
  public final Expr cond;
  public final Expr thenExpr;
  public final Optional<Expr> elseExpr;

  public If(Expr cond, Expr thenExpr) {
    this.cond = cond;
    this.thenExpr = thenExpr;
    this.elseExpr = Optional.empty();

  }

  public If(Location loc, Expr cond, Expr thenExpr) {
    super(loc);
    this.cond = cond;
    this.thenExpr = thenExpr;
    this.elseExpr = Optional.empty();
  }

  public If(Expr cond, Expr thenExpr, Expr elseExpr) {
    this.cond = cond;
    this.thenExpr = thenExpr;
    this.elseExpr = Optional.ofNullable(elseExpr);
  }

  public If(Location loc, Expr cond, Expr thenExpr, Expr elseExpr) {
    super(loc);
    this.cond = cond;
    this.thenExpr = thenExpr;
    this.elseExpr = Optional.ofNullable(elseExpr);
  }

  public If(Location loc, Expr cond, Expr thenExpr, Optional<Expr> elseExpr) {
    super(loc);
    this.cond = cond;
    this.thenExpr = thenExpr;
    this.elseExpr = elseExpr;
  }

  public If(If other) {
    super(other);
    this.cond = other.cond;
    this.thenExpr = other.thenExpr;
    this.elseExpr = other.elseExpr;
  }

  public If(If other, Expr cond, Expr thenExpr, Optional<Expr> elseExpr) {
    super(other);
    this.cond = cond;
    this.thenExpr = thenExpr;
    this.elseExpr = elseExpr;
  }

  @Override
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env,
      Subst solutionContext) {
    return new If(this,
        this.cond.instantiate(engine, env, solutionContext),
        this.thenExpr.instantiate(engine, env, solutionContext),
        this.elseExpr.map(e -> e.instantiate(engine, env, solutionContext)));
  }

  @Override
  public InferResult infer(TypeInference engine, Env env) {
    var condRes = engine.infer(this.cond, env);
    engine.unify(condRes.type(), new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_BOOL));

    var thenRes = engine.infer(this.thenExpr, env);

    if (this.elseExpr.isPresent()) {
      var elseRes = engine.infer(this.elseExpr.get(), env);
      engine.unify(thenRes.type(), elseRes.type());
    } else {
      // Fallback, when the result should not really be usable outside of the if, as
      // no else branch exists
      engine.unify(thenRes.type(), new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_UNIT));
    }

    return new InferResult(thenRes.type().deref());
  }

  @Override
  public List<Expr> getChildren() {
    if (this.elseExpr.isPresent()) {
      return List.of(this.cond, this.thenExpr, this.elseExpr.get());
    }
    return List.of(this.cond, this.thenExpr);
  }

  @Override
  public void replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
    this.cond.replaceSymbol(original, replacement);
    this.thenExpr.replaceSymbol(original, replacement);
    this.elseExpr.ifPresent(e -> e.replaceSymbol(original, replacement));
  }

  @Override
  public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
    return this.cond.containsSymbol(symbol) || this.thenExpr.containsSymbol(symbol)
        || this.elseExpr.map(e -> e.containsSymbol(symbol)).orElse(false);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof If other && this.cond.equals(other.cond) && this.thenExpr.equals(other.thenExpr)
        && this.elseExpr.equals(other.elseExpr)
        && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.cond, this.thenExpr, this.elseExpr, super.hashCode());
  }

  @Override
  public Expr copy() {
    return new If(this);
  }
}

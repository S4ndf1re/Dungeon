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

public final class Assert extends Expr {
  public final Expr cond;
  public final Optional<Expr> message;

  public Assert(Expr cond) {
    this.cond = cond;
    this.message = Optional.empty();
  }

  public Assert(Location loc, Expr cond) {
    super(loc);
    this.message = Optional.empty();
    this.cond = cond;
  }

  public Assert(Location loc, Expr cond, Optional<Expr> message) {
    super(loc);
    this.cond = cond;
    this.message = message;
  }

  public Assert(Expr cond, Optional<Expr> message) {
    this.cond = cond;
    this.message = message;
  }

  public Assert(Expr cond, Expr message) {
    this.cond = cond;
    this.message = Optional.ofNullable(message);
  }

  public Assert(Location loc, Expr cond, Expr message) {
    super(loc);
    this.cond = cond;
    this.message = Optional.ofNullable(message);
  }

  public Assert(Assert other) {
    super(other);
    this.cond = other.cond;
    this.message = other.message;
  }

  public Assert(Assert other, Expr cond, Optional<Expr> message) {
    super(other);
    this.cond = cond;
    this.message = message;
  }

  @Override
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env,
      Subst solutionContext) {
    return new Assert(this,
        this.cond.instantiate(engine, env, solutionContext),
        this.message.map(msg -> msg.instantiate(engine, env, solutionContext)));
  }

  @Override
  public InferResult infer(TypeInference engine, Env env) {

    var condRes = engine.infer(this.cond, env);
    engine.unify(condRes.type(), new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_BOOL));

    if (this.message.isPresent()) {
      var msg = this.message.get();
      var msgRes = engine.infer(msg, env);
      engine.unify(msgRes.type(), new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_STRING));
    }

    return new InferResult(new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_UNIT));
  }

  @Override
  public List<Expr> getChildren() {
    if (this.message.isPresent()) {
      return List.of(this.cond, this.message.get());
    }
    return List.of(this.cond);
  }

  @Override
  public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
    return new Assert(this, this.cond.replaceSymbol(original, replacement),
        this.message.map(msg -> msg.replaceSymbol(original, replacement)));
  }

  @Override
  public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
    return this.cond.containsSymbol(symbol) || this.message.map(msg -> msg.containsSymbol(symbol)).orElse(false);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Assert other && this.cond.equals(other.cond) && this.message.equals(other.message)
        && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.cond, this.message, super.hashCode());
  }

  @Override
  public Expr copy() {
    return new Assert(this);
  }
}

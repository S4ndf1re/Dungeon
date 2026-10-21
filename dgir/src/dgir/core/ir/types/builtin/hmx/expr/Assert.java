package dgir.core.ir.types.builtin.hmx.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dgir.core.debug.Location;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.Env;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Subst;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.InstEnv;

public final class Assert extends HMXExpr {
  public final HMXExpr cond;
  public final Optional<HMXExpr> message;

  public Assert(HMXExpr cond) {
    this.cond = cond;
    this.message = Optional.empty();
  }

  public Assert(Location loc, HMXExpr cond) {
    super(loc);
    this.message = Optional.empty();
    this.cond = cond;
  }

  public Assert(Location loc, HMXExpr cond, Optional<HMXExpr> message) {
    super(loc);
    this.cond = cond;
    this.message = message;
  }

  public Assert(HMXExpr cond, Optional<HMXExpr> message) {
    this.cond = cond;
    this.message = message;
  }

  public Assert(HMXExpr cond, HMXExpr message) {
    this.cond = cond;
    this.message = Optional.ofNullable(message);
  }

  public Assert(Location loc, HMXExpr cond, HMXExpr message) {
    super(loc);
    this.cond = cond;
    this.message = Optional.ofNullable(message);
  }

  public Assert(Assert other) {
    super(other);
    this.cond = other.cond;
    this.message = other.message;
  }

  public Assert(Assert other, HMXExpr cond, Optional<HMXExpr> message) {
    super(other);
    this.cond = cond;
    this.message = message;
  }

  @Override
  public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env,
      Subst solutionContext) {
    return new Assert(this,
        this.cond.instantiate(engine, env, solutionContext),
        this.message.map(msg -> msg.instantiate(engine, env, solutionContext)));
  }

  @Override
  public GenerateResult generate(TypeInference engine, Env env, HMXType type) {

    List<Constraint> andConstraints = new ArrayList<>();
    List<TypeVar<HMXType>> toExist = new ArrayList<>();

    var condTypeVar = new TypeVar<HMXType>();
    var condType = new HMXType.Var(condTypeVar);
    andConstraints.add(engine.generate(this.cond, env, condType).constr());
    andConstraints.add(new Constraint.Equal(condType, new HMXType.LitType(TypeIdent.TYPE_IDENT_BOOL)));
    toExist.add(condTypeVar);

    if (this.message.isPresent()) {
      var msgTypeVar = new TypeVar<HMXType>();
      var msgType = new HMXType.Var(msgTypeVar);
      andConstraints.add(engine.generate(this.message.get(), env, msgType).constr());
      andConstraints.add(new Constraint.Equal(msgType, new HMXType.LitType(TypeIdent.TYPE_IDENT_STRING)));
      toExist.add(msgTypeVar);
    }

    andConstraints.add(new Constraint.Equal(type, new HMXType.LitType(TypeIdent.TYPE_IDENT_UNIT)));

    return new GenerateResult(new Constraint.Exists(toExist, new Constraint.And(andConstraints)));
  }

  @Override
  public List<HMXExpr> getChildren() {
    if (this.message.isPresent()) {
      return List.of(this.cond, this.message.get());
    }
    return List.of(this.cond);
  }

  @Override
  public void replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
    this.cond.replaceSymbol(original, replacement);
    this.message.ifPresent(msg -> msg.replaceSymbol(original, replacement));
  }

  @Override
  public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
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
  public HMXExpr copy() {
    return new Assert(this);
  }
}

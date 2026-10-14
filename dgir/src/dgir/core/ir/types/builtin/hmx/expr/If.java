package dgir.core.ir.types.builtin.hmx.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dgir.core.debug.Location;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.Env;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.builtin.hmx.Subst;
import dgir.core.ir.types.builtin.hmx.TypeInference;

public final class If extends HMXExpr {
  public final HMXExpr cond;
  public final HMXExpr thenExpr;
  public final Optional<HMXExpr> elseExpr;

  public If(HMXExpr cond, HMXExpr thenHMXExpr) {
    this.cond = cond;
    this.thenExpr = thenHMXExpr;
    this.elseExpr = Optional.empty();
  }

  public If(Location loc, HMXExpr cond, HMXExpr thenHMXExpr) {
    super(loc);
    this.cond = cond;
    this.thenExpr = thenHMXExpr;
    this.elseExpr = Optional.empty();
  }

  public If(HMXExpr cond, HMXExpr thenHMXExpr, HMXExpr elseHMXExpr) {
    this.cond = cond;
    this.thenExpr = thenHMXExpr;
    this.elseExpr = Optional.ofNullable(elseHMXExpr);
  }

  public If(Location loc, HMXExpr cond, HMXExpr thenHMXExpr, HMXExpr elseHMXExpr) {
    super(loc);
    this.cond = cond;
    this.thenExpr = thenHMXExpr;
    this.elseExpr = Optional.ofNullable(elseHMXExpr);
  }

  public If(Location loc, HMXExpr cond, HMXExpr thenHMXExpr, Optional<HMXExpr> elseHMXExpr) {
    super(loc);
    this.cond = cond;
    this.thenExpr = thenHMXExpr;
    this.elseExpr = elseHMXExpr;
  }

  public If(If other) {
    super(other);
    this.cond = other.cond;
    this.thenExpr = other.thenExpr;
    this.elseExpr = other.elseExpr;
  }

  public If(If other, HMXExpr cond, HMXExpr thenHMXExpr, Optional<HMXExpr> elseHMXExpr) {
    super(other);
    this.cond = cond;
    this.thenExpr = thenHMXExpr;
    this.elseExpr = elseHMXExpr;
  }

  @Override
  public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env,
      Subst solutionContext) {
    return new If(this,
        this.cond.instantiate(engine, env, solutionContext),
        this.thenExpr.instantiate(engine, env, solutionContext),
        this.elseExpr.map(e -> e.instantiate(engine, env, solutionContext)));
  }

  @Override
  public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
    List<TypeVar<HMXType>> toExist = new ArrayList<>();
    List<Constraint> constraints = new ArrayList<>();

    var condTypeVar = new TypeVar<HMXType>();
    var condType = new HMXType.Var(condTypeVar);
    constraints.add(engine.generate(this.cond, env, condType).constr());
    constraints.add(new Constraint.Equal(condType, new HMXType.LitType(TypeIdent.TYPE_IDENT_BOOL)));
    toExist.add(condTypeVar);

    var thenTypeVar = new TypeVar<HMXType>();
    var thenType = new HMXType.Var(thenTypeVar);
    constraints.add(engine.generate(this.thenExpr, env, thenType).constr());
    toExist.add(thenTypeVar);

    if (this.elseExpr.isPresent()) {
      var elseTypeVar = new TypeVar<HMXType>();
      var elseType = new HMXType.Var(elseTypeVar);
      constraints.add(engine.generate(this.elseExpr.get(), env, elseType).constr());
      constraints.add(new Constraint.Equal(elseType, thenType));
      toExist.add(elseTypeVar);
    } else {
      constraints.add(new Constraint.Equal(thenType, new HMXType.LitType(TypeIdent.TYPE_IDENT_UNIT)));
    }

    constraints.add(new Constraint.Equal(type, thenType));

    return new GenerateResult(new Constraint.Exists(toExist, new Constraint.And(constraints)));
  }

  @Override
  public List<HMXExpr> getChildren() {
    if (this.elseExpr.isPresent()) {
      return List.of(this.cond, this.thenExpr, this.elseExpr.get());
    }
    return List.of(this.cond, this.thenExpr);
  }

  @Override
  public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
    return new If(this,
        this.cond.replaceSymbol(original, replacement),
        this.thenExpr.replaceSymbol(original, replacement),
        this.elseExpr.map(e -> e.replaceSymbol(original, replacement)));
  }

  @Override
  public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
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
  public HMXExpr copy() {
    return new If(this);
  }
}

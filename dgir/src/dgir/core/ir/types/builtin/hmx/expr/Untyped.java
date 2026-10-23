package dgir.core.ir.types.builtin.hmx.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.Env;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Subst;
import dgir.core.ir.types.builtin.hmx.TypeInference;

public final class Untyped extends HMXExpr {
  private HMXType type;

  public Untyped(Location loc) {
    super(loc);
    this.type = new HMXType.Var(this.getLocation(), new TypeVar<>());
  }

  private Untyped(Untyped other) {
    super(other);
    this.type = other.type;
  }

  @Override
  public final String toString() {
    return "" + type;
  }

  @Override
  public List<HMXExpr> getChildren() {
    return List.of();
  }


  @Override
  public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
    // The var was created without a level (conversion time); stamp it with the
    // current level before unification, mirroring Constraint.Exists.solve.
    this.type.deref().freeTypeVars().forEach(tv -> tv.setLevel(engine.getCurrentLevel()));
    return new GenerateResult(new Constraint.Equal(this.type, type));
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Untyped other && this.type.equals(other.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.type, super.hashCode());
  }

  @Override
  public void replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
    // Nothing to replace, as this expression does not reference any symbols!
  }

  @Override
  public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
    return this;
  }

  @Override
  public HMXExpr copy() {
    return new Untyped(this);
  }
}

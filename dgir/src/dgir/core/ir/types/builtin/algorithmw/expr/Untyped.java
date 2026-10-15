package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Env;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.InferResult;
import dgir.core.ir.types.builtin.algorithmw.Subst;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;

public final class Untyped extends Expr {
  private AlgorithmWType type;

  public Untyped(Location loc) {
    super(loc);
    this.type = new AlgorithmWType.Var(new TypeVar<>());
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
  public List<Expr> getChildren() {
    return List.of();
  }

  @Override
  public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
    return false;
  }

  @Override
  public InferResult infer(TypeInference engine, Env env) {
    // The var was created without a level (conversion time); stamp it with the
    // current level before it participates in any unification.
    this.type.deref().freeTypeVars().forEach(tv -> tv.setLevel(engine.getCurrentLevel()));
    return new InferResult(this.type);
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
  public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
    return this;
  }

  @Override
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
    return this;
  }

  @Override
  public Expr copy() {
    return new Untyped(this);
  }
}

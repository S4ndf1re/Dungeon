package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Env;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.InferResult;
import dgir.core.ir.types.builtin.algorithmw.Subst;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;

public final class Ann extends Expr {
  private final Expr expr;
  private final AlgorithmWType type;

  public Expr expr() {
    return this.expr.unwrapOrThis();
  }

  public AlgorithmWType type() {
    return this.type;
  }

  public Ann(Expr expr, AlgorithmWType type) {
    this.expr = expr;
    this.type = type;
  }

  public Ann(Location loc, Expr expr, AlgorithmWType type) {
    super(loc);
    this.expr = expr;
    this.type = type;
  }

  public Ann(Ann other) {
    super(other);
    this.expr = other.expr;
    this.type = other.type;
  }

  public Ann(Ann other, Expr expr, AlgorithmWType type) {
    super(other);
    this.expr = expr;
    this.type = type;
  }

  @Override
  public List<Expr> getChildren() {
    return List.of(this.expr);
  }

  @Override
  public InferResult infer(TypeInference engine, Env env) {

    InferResult res = engine.infer(expr, env);

    engine.unify(res.type(), type);

    return new InferResult(type);
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
  public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
    return new Ann(this, this.expr.replaceSymbol(original, replacement), this.type);
  }

  @Override
  public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
    return this.expr.containsSymbol(symbol);
  }

  @Override
  public Expr copy() {
    return new Ann(this);
  }

  @Override
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
    // Simply return the inner as fully instantiated!
    return this.expr.instantiate(engine, env, solution);
  }
}

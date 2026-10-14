package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

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

public final class Branch extends Expr {
  public final Optional<Pair<Expr, Expr>> condition;
  public final Expr block;

  public Branch(Expr block) {
    condition = Optional.empty();
    this.block = block;

  }

  public Branch(Location loc, Expr block) {
    super(loc);
    condition = Optional.empty();
    this.block = block;
  }

  public Branch(Expr condition, Expr thenBlock, Expr elseBlock) {
    this.condition = Optional.ofNullable(Pair.of(condition, thenBlock));
    this.block = elseBlock;
  }

  public Branch(Location loc, Expr condition, Expr thenBlock, Expr block) {
    super(loc);
    this.condition = Optional.ofNullable(Pair.of(condition, thenBlock));
    this.block = block;
  }

  public Branch(Branch other) {
    super(other);
    this.condition = other.condition;
    this.block = other.block;
  }

  public Branch(Branch other, Optional<Pair<Expr, Expr>> condition, Expr block) {
    super(other);
    this.condition = condition;
    this.block = block;
  }

  @Override
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env,
      Subst solutionContext) {
    return new Branch(this,
        this.condition.map(cond -> Pair.of(cond.getLeft().instantiate(engine, env, solutionContext),
            cond.getRight().instantiate(engine, env, solutionContext))),
        this.block.instantiate(engine, env, solutionContext));
  }

  @Override
  public InferResult infer(TypeInference engine, Env env) {
    var elseBlockRes = engine.infer(this.block, env);

    if (this.condition.isPresent()) {
      var infRes = engine.infer(this.condition.get().getLeft(), env);
      engine.unify(infRes.type(), new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_BOOL));
      var thenBlockRes = engine.infer(this.condition.get().getRight(), env);
      engine.unify(thenBlockRes.type(), elseBlockRes.type());
    }

    return new InferResult(elseBlockRes.type().deref());
  }

  @Override
  public List<Expr> getChildren() {
    if (this.condition.isPresent()) {
      return List.of(this.condition.get().getLeft(), this.condition.get().getRight(), this.block);
    }
    return List.of(this.block);
  }

  @Override
  public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
    return new Branch(this,
        this.condition.map(cond -> Pair.of(cond.getLeft().replaceSymbol(original, replacement),
            cond.getRight().replaceSymbol(original, replacement))),
        this.block.replaceSymbol(original, replacement));
  }

  @Override
  public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
    return this.block.containsSymbol(symbol)
        || this.condition.isPresent() && (this.condition.get().getLeft().containsSymbol(symbol)
            || this.condition.get().getRight().containsSymbol(symbol));
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Branch other && this.condition.equals(other.condition) && this.block.equals(other.block)
        && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.condition, this.block, super.hashCode());
  }

  @Override
  public Expr copy() {
    return new Branch(this);
  }
}

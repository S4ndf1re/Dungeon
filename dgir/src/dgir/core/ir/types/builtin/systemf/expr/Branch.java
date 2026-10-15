package dgir.core.ir.types.builtin.systemf.expr;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.debug.Location;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.builtin.systemf.TypeResult;
import dgir.core.ir.types.builtin.systemf.TypeInference;

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
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
      Context solutionContext) {
    return new Branch(this,
        this.condition.map(cond -> Pair.of(cond.getLeft().instantiate(engine, env, solutionContext),
            cond.getRight().instantiate(engine, env, solutionContext))),
        this.block.instantiate(engine, env, solutionContext));
  }

  @Override
  public TypeResult infer(TypeInference engine, Context ctx) {
    var elseBlockRes = engine.infer(ctx, this.block);

    if (this.condition.isPresent()) {
      var infRes = engine.infer(ctx, this.condition.get().getLeft());
      engine.subtype(ctx, infRes.type(), new SystemFType.Lit(TypeIdent.TYPE_IDENT_BOOL));
      var thenBlockRes = engine.infer(ctx, this.condition.get().getRight());
      engine.subtype(ctx, thenBlockRes.type(), elseBlockRes.type());
    }

    return new TypeResult(elseBlockRes.type().deref(), ctx);
  }

  @Override
  public List<Expr> getChildren() {
    if (this.condition.isPresent()) {
      return List.of(this.condition.get().getLeft(), this.condition.get().getRight(), this.block);
    }
    return List.of(this.block);
  }

  @Override
  public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
    return new Branch(this,
        this.condition.map(cond -> Pair.of(cond.getLeft().replaceSymbol(original, replacement),
            cond.getRight().replaceSymbol(original, replacement))),
        this.block.replaceSymbol(original, replacement));
  }

  @Override
  public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
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

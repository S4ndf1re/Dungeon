package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Env;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.InferResult;
import dgir.core.ir.types.builtin.algorithmw.Subst;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;
import dgir.core.ir.types.traits.IBlock;

public final class Block extends Expr implements IBlock<Expr, AlgorithmWType> {
  public final Expr innerExpr;

  public Block(Expr inner) {
    this.innerExpr = inner;
  }

  public Block(Block other, Expr inner) {
    super(other);
    this.innerExpr = inner;
  }

  public Block(Block other) {
    super(other);
    this.innerExpr = other.innerExpr;
  }

  @Override
  public boolean equals(Object obj) {
    // NOTE: Block exprs are referencable entities (branch targets), not
    // positional children. Equality therefore ignores the parentScope/position
    // stamps inherited from Expression — two instantiations of the same block
    // reached via different reference paths must cons to a single instance,
    // otherwise reconstruction emits the block once per reference path.
    return obj instanceof Block other
        && this.getInferredType().equals(other.getInferredType())
        && this.innerExpr.equals(other.innerExpr);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.getInferredType(), this.innerExpr);
  }

  @Override
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solutionContext) {
    // TODO: Maybe memoize here
    return new Block(this, this.innerExpr.instantiate(engine, env, solutionContext));
  }

  @Override
  public InferResult infer(TypeInference engine, Env env) {
    return this.innerExpr.infer(engine, env);
  }

  @Override
  public List<Expr> getChildren() {
    return this.innerExpr.getChildren();
  }

  @Override
  public List<Expr> getInstantiableChildren() {
    return this.innerExpr.getInstantiableChildren();
  }

  /**
   * Memoized per instance so a shared block (referenced by several branches)
   * is replaced only ONCE per (original, replacement) pair — preserving DAG
   * sharing during symbol reinstantiation instead of duplicating the block
   * per reference path.
   */
  private java.util.HashMap<
      java.util.Map.Entry<Symbol<Expr, AlgorithmWType>, Symbol<Expr, AlgorithmWType>>,
      Expr> replaceSymbolMemo;

  @Override
  public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
    var key = java.util.Map.entry(original, replacement);
    if (this.replaceSymbolMemo == null) {
      this.replaceSymbolMemo = new java.util.HashMap<>();
    }
    var cached = this.replaceSymbolMemo.get(key);
    if (cached != null) {
      return cached;
    }
    var replaced = new Block(this, this.innerExpr.replaceSymbol(original, replacement));
    this.replaceSymbolMemo.put(key, replaced);
    return replaced;
  }

  @Override
  public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
    return this.innerExpr.containsSymbol(symbol);
  }

  @Override
  public Expr copy() {
    return new Block(this);
  }

  @Override
  public Expr getInnerExpression() {
    return this.innerExpr;
  }

}

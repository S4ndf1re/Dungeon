package dgir.core.ir.types.builtin.hmx.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.hmx.Env;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Subst;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.traits.IBlock;

public final class Block extends HMXExpr implements IBlock<HMXExpr, HMXType> {
  public final HMXExpr innerExpr;

  public Block(HMXExpr inner) {
    this.innerExpr = inner;
  }

  public Block(Block other, HMXExpr inner) {
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
  public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solutionContext) {
    // TODO: Maybe memoize here
    return new Block(this, this.innerExpr.instantiate(engine, env, solutionContext));
  }

  @Override
  public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
    return this.innerExpr.generate(engine, env, type);
  }

  @Override
  public List<HMXExpr> getChildren() {
    return this.innerExpr.getChildren();
  }

  @Override
  public List<HMXExpr> getInstantiableChildren() {
    return this.innerExpr.getInstantiableChildren();
  }

  /**
   * Memoized per instance: the merge block of a diamond is referenced by
   * multiple Branch exprs, and a symbol replacement cascading through the tree
   * reaches it once per reference path. Without the memo, each path builds a
   * fresh copy (with a fresh inner and fresh operation clones) and the final
   * tree contains the block once per path — duplicating the emitted IR.
   * With the memo, every path gets the SAME replaced instance and sharing is
   * preserved.
   */
  private java.util.HashMap<
      java.util.Map.Entry<Symbol<HMXExpr, HMXType>, Symbol<HMXExpr, HMXType>>,
      HMXExpr> replaceSymbolMemo;

  @Override
  public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
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
  public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
    return this.innerExpr.containsSymbol(symbol);
  }

  @Override
  public HMXExpr copy() {
    return new Block(this);
  }

  @Override
  public HMXExpr getInnerExpression() {
    return this.innerExpr;
  }

}

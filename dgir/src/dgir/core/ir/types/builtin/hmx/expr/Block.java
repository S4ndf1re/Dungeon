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

  @Override
  public void replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
    // NOTE: in-place replacement preserves sharing by construction — the shared
    // inner expression is mutated once and stays a single instance, no memo
    // needed.
    this.innerExpr.replaceSymbol(original, replacement);
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

package dgir.core.ir.types.builtin.hmx.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

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

public final class Branch extends HMXExpr {
  public Optional<Pair<HMXExpr, HMXExpr>> condition;
  public final HMXExpr block;

  public Branch(HMXExpr block) {
    condition = Optional.empty();
    this.block = block;

  }

  public Branch(Location loc, HMXExpr block) {
    super(loc);
    condition = Optional.empty();
    this.block = block;
  }

  public Branch(HMXExpr condition, HMXExpr thenBlock, HMXExpr elseBlock) {
    this.condition = Optional.ofNullable(Pair.of(condition, thenBlock));
    this.block = elseBlock;
  }

  public Branch(Location loc, HMXExpr condition, HMXExpr thenBlock, HMXExpr block) {
    super(loc);
    this.condition = Optional.ofNullable(Pair.of(condition, thenBlock));
    this.block = block;
  }

  public Branch(Branch other) {
    super(other);
    this.condition = other.condition;
    this.block = other.block;
  }

  public Branch(Branch other, Optional<Pair<HMXExpr, HMXExpr>> condition, HMXExpr block) {
    super(other);
    this.condition = condition;
    this.block = block;
  }

  @Override
  public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env,
      Subst solutionContext) {
    return new Branch(this,
        this.condition.map(cond -> Pair.of(cond.getLeft().instantiate(engine, env, solutionContext),
            cond.getRight().instantiate(engine, env, solutionContext))),
        this.block.instantiate(engine, env, solutionContext));
  }

  @Override
  public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
    List<TypeVar<HMXType>> toExist = new ArrayList<>();
    List<Constraint> constraints = new ArrayList<>();

    var elseBlockTypeVar = new TypeVar<HMXType>();
    var elseBlockType = new HMXType.Var(elseBlockTypeVar);
    constraints.add(engine.generate(this.block, env, elseBlockType).constr());
    constraints.add(new Constraint.Equal(type, elseBlockType));
    toExist.add(elseBlockTypeVar);

    if (this.condition.isPresent()) {
      var condTypeVar = new TypeVar<HMXType>();
      var condType = new HMXType.Var(condTypeVar);
      constraints.add(engine.generate(this.condition.get().getLeft(), env, condType).constr());
      constraints.add(new Constraint.Equal(condType, new HMXType.LitType(TypeIdent.TYPE_IDENT_BOOL)));
      toExist.add(condTypeVar);

      var thenBlockTypeVar = new TypeVar<HMXType>();
      var thenBlockType = new HMXType.Var(thenBlockTypeVar);
      constraints.add(engine.generate(this.condition.get().getRight(), env, thenBlockType).constr());
      constraints.add(new Constraint.Equal(thenBlockType, elseBlockType));
      toExist.add(thenBlockTypeVar);
    }

    return new GenerateResult(new Constraint.Exists(toExist, new Constraint.And(constraints)));
  }

  @Override
  public List<HMXExpr> getChildren() {
    if (this.condition.isPresent()) {
      return List.of(this.condition.get().getLeft(), this.condition.get().getRight(), this.block);
    }
    return List.of(this.block);
  }

  @Override
  public void replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
    if (this.condition.isPresent()) {
      this.condition.get().getLeft().replaceSymbol(original, replacement);
      this.condition.get().getRight().replaceSymbol(original, replacement);
    }
    this.block.replaceSymbol(original, replacement);
  }

  @Override
  public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
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
  public HMXExpr copy() {
    return new Branch(this);
  }
}

package dgir.core.ir.types.builtin.hmx;

import java.util.List;

import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.builtin.hmx.traits.OneShotConstraint;

public abstract class Constraint {

  public abstract void solve(TypeInference engine, Env env);

  public abstract Constraint applySubst(Subst subst);

  /**
   * Equal constraint t==t' (Often solved with unification)
   */
  public static final class Equal extends Constraint {
    public final HMXType tLeft;
    public final HMXType tRight;

    public Equal(HMXType tLeft, HMXType tRight) {
      this.tLeft = tLeft;
      this.tRight = tRight;
    }

    @Override
    public void solve(TypeInference engine, Env env) {
      engine.unify(this.tLeft, this.tRight);
    }

    @Override
    public Constraint applySubst(Subst subst) {
      return new Equal(subst.apply(this.tLeft), subst.apply(tRight));
    }
  }

  /**
   * Trivial constraint, always solves
   */
  public static final class Trivial extends Constraint {

    @Override
    public void solve(TypeInference engine, Env env) {
      // Trivial, never fails
    }

    @Override
    public Constraint applySubst(Subst subst) {
      return this;
    }

  }

  /**
   * Subtype constraint tLeft <: tRight. Unlike {@link Inst} this is NOT a
   * one-shot constraint: deferred copies are re-checked with fresh variables
   * on every instantiation of the scheme they belong to.
   */
  public static final class Sub extends Constraint {
    public final HMXType tLeft;
    public final HMXType tRight;

    public Sub(HMXType tLeft, HMXType tRight) {
      this.tLeft = tLeft;
      this.tRight = tRight;
    }

    @Override
    public void solve(TypeInference engine, Env env) {
      try {
        engine.subtype(this.tLeft, this.tRight);
      } catch (TypeInference.UnresolvedSubtypeException e) {
        if (!engine.getAllowSubtypeFailure()) {
          throw e;
        }
        // An open variable is involved and we are within a generalize block; the leaf
        // cannot be decided yet. It
        // stays in the stored constraint tree and is re-checked by the second
        // pass of Inst.solve after the fresh variables are bound, and on every
        // further instantiation.
      }
    }

    @Override
    public Constraint applySubst(Subst subst) {
      return new Sub(subst.apply(this.tLeft), subst.apply(this.tRight));
    }
  }

  /**
   * Instantiate the variable taken from context, and equate against type
   */
  public static final class Inst extends Constraint implements OneShotConstraint {
    public final Symbol<HMXExpr, HMXType> variable;
    public final HMXType type;

    private boolean solved;

    public Inst(Symbol<HMXExpr, HMXType> variable, HMXType type) {
      this.variable = variable;
      this.type = type;
    }

    private Inst(Inst other, HMXType substitutedType) {
      this.variable = other.variable;
      this.type = substitutedType;
      this.solved = other.solved;
    }

    @Override
    public void solve(TypeInference engine, Env env) {
      var scheme = env.get(this.variable);
      if (scheme == null) {
        throw new TypingException.UnknownVariable(this.variable);
      }

      var inferredType = scheme.instantiate(engine);
      engine.solveConstraint(inferredType.getRight(), env);
      engine.unify(this.type, inferredType.getLeft());
    }

    @Override
    public boolean isSolved() {
      return this.solved;
    }

    @Override
    public void markSolved() {
      this.solved = true;
    }

    @Override
    public Constraint applySubst(Subst subst) {
      return new Inst(this, subst.apply(this.type));
    }
  }

  public static final class And extends Constraint {
    public final List<Constraint> constraints;

    public And() {
      this.constraints = List.of();
    }

    public And(Constraint... constraints) {
      this.constraints = List.of(constraints);
    }

    public And(List<Constraint> constraints) {
      this.constraints = List.copyOf(constraints);
    }

    @Override
    public void solve(TypeInference engine, Env env) {
      this.constraints.forEach(c -> engine.solveConstraint(c, env));
    }

    @Override
    public Constraint applySubst(Subst subst) {
      return new And(this.constraints.stream().map(c -> c.applySubst(subst)).toList());
    }
  }

  /**
   * Existentially quantify symbols into context for inner constraint
   */
  public static final class Exists extends Constraint {
    public final List<TypeVar<HMXType>> toQuantify;
    public final Constraint inner;

    @SafeVarargs
    public Exists(Constraint inner, TypeVar<HMXType>... toQuantify) {
      this.toQuantify = List.of(toQuantify);
      this.inner = inner;
    }

    public Exists(List<TypeVar<HMXType>> toQuantify, Constraint inner) {
      this.toQuantify = List.copyOf(toQuantify);
      this.inner = inner;
    }

    @Override
    public void solve(TypeInference engine, Env env) {
      for (var v : this.toQuantify) {
        v.setLevel(engine.getCurrentLevel());
      }

      engine.solveConstraint(this.inner, env);
    }

    @Override
    public Constraint applySubst(Subst subst) {
      return new Exists(this.toQuantify.stream()
          .map(tv -> new HMXType.Var(tv))
          .map(tv -> tv.deref())
          .map(tv -> subst.apply(tv))
          .filter(tv -> tv instanceof HMXType.Var)
          .map(tv -> ((HMXType.Var) tv).tyVar)
          .toList(),
          this.inner.applySubst(subst));
    }
  }

  public static final record LetBinding(Symbol<HMXExpr, HMXType> name, Scheme scheme) {
    public LetBinding(Symbol<HMXExpr, HMXType> name, HMXType type) {
      this(name, new Scheme(List.of(), type, new Constraint.Trivial()));
    }

    public LetBinding applySubst(Subst subst) {
      return new LetBinding(this.name, this.scheme.apply(subst));
    }
  }

  /**
   * Existentially quantify symbols into context for inner constraint
   */
  public static final class LetRec extends Constraint {

    public final List<LetBinding> bindings;
    public final Constraint inner;

    @SafeVarargs
    public LetRec(Constraint inner, LetBinding... bindings) {
      this.bindings = List.of(bindings);
      this.inner = inner;
    }

    public LetRec(List<LetBinding> bindings, Constraint inner) {
      this.bindings = List.copyOf(bindings);
      this.inner = inner;
    }

    @Override
    public void solve(TypeInference engine, Env env) {
      var newEnv = env.copy();

      for (var binding : this.bindings) {
        binding.scheme.vars().forEach(v -> v.setLevel(engine.getCurrentLevel()));
        // Instead of putting the real scheme with the real constraint into the env,
        // build a shell first!
        newEnv.put(binding.name, new Scheme(binding.scheme.vars(), binding.scheme.type(), new Constraint.Trivial()));
      }

      for (var binding : this.bindings) {
        binding.scheme.vars().forEach(v -> v.setLevel(engine.getCurrentLevel()));
        var scheme = engine.solveConstraintAndGeneralize(binding.scheme.constr(), newEnv, binding.scheme.type());
        newEnv.put(binding.name, scheme);
      }

      engine.solveConstraint(this.inner, newEnv);
    }

    @Override
    public Constraint applySubst(Subst subst) {
      return new LetRec(this.bindings.stream().map(b -> b.applySubst(subst)).toList(), this.inner.applySubst(subst));
    }
  }

  /**
   * Existentially quantify symbols into context for inner constraint
   */
  public static final class LetSeq extends Constraint {

    public final List<LetBinding> bindings;
    public final Constraint inner;

    @SafeVarargs
    public LetSeq(Constraint inner, LetBinding... bindings) {
      this.bindings = List.of(bindings);
      this.inner = inner;
    }

    public LetSeq(List<LetBinding> bindings, Constraint inner) {
      this.bindings = List.copyOf(bindings);
      this.inner = inner;
    }

    @Override
    public void solve(TypeInference engine, Env env) {
      var newEnv = env.copy();

      for (var binding : this.bindings) {
        binding.scheme.vars().forEach(v -> v.setLevel(engine.getCurrentLevel()));
        var scheme = engine.solveConstraintAndGeneralize(binding.scheme.constr(), newEnv, binding.scheme.type());
        newEnv.put(binding.name, scheme);
      }

      engine.solveConstraint(this.inner, newEnv);
    }

    @Override
    public Constraint applySubst(Subst subst) {
      return new LetSeq(this.bindings.stream().map(b -> b.applySubst(subst)).toList(), this.inner.applySubst(subst));
    }
  }

}

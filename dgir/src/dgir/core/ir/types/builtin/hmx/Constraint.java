package dgir.core.ir.types.builtin.hmx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.builtin.hmx.traits.OneShotConstraint;

public abstract class Constraint {

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  public abstract void solve(TypeInference engine, Env env);

  public abstract Constraint applySubst(Subst subst);

  public abstract List<Constraint> getChildren();

  public class ConstraintVisitor {
    @FunctionalInterface
    public static interface VisitCallback {
      void visit(Constraint c);
    }

    public void visit(Constraint c, VisitCallback callback) {
      ArrayDeque<Constraint> toVisit = new ArrayDeque<>();
      toVisit.add(c);

      while (toVisit.isEmpty()) {
        var current = toVisit.removeFirst();
        callback.visit(current);
        toVisit.addAll(current.getChildren());
      }
    }
  }

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

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Equal eq && this.tLeft.equals(eq.tLeft) && this.tRight.equals(eq.tRight);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.tLeft, this.tRight);
    }

    @Override
    public List<Constraint> getChildren() {
      return List.of();
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

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public List<Constraint> getChildren() {
      return List.of();
    }
  }

  /**
   * Subtype constraint tLeft <: tRight. Unlike {@link Inst} this is NOT a
   * one-shot constraint: deferred copies are re-checked with fresh variables on
   * every instantiation of the scheme they belong to.
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
        engine.addDeferredConstraint(this);
      }
    }

    @Override
    public Constraint applySubst(Subst subst) {
      return new Sub(subst.apply(this.tLeft), subst.apply(this.tRight));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Sub sb && this.tLeft.equals(sb.tLeft) && this.tRight.equals(sb.tRight);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.tLeft, this.tRight);
    }

    @Override
    public List<Constraint> getChildren() {
      return List.of();
    }
  }

  public static final class DetermineByCallbackAndUnify extends Constraint {

    public static final class CallbackNotReady extends RuntimeException {
    }

    @FunctionalInterface
    public interface DetermineCallback {
      HMXType determine(HMXType... types) throws CallbackNotReady;
    }

    public final DetermineCallback callback;
    public final HMXType unifyAgainst;
    public final HMXType[] originalTypes;

    public DetermineByCallbackAndUnify(DetermineCallback callback, HMXType unifyAgainst, HMXType... types) {
      this.callback = callback;
      this.unifyAgainst = unifyAgainst;
      this.originalTypes = types;
    }

    @Override
    public void solve(TypeInference engine, Env env) {
      try {
        var result = this.callback.determine(this.originalTypes);
        engine.unify(result, this.unifyAgainst);
      } catch (CallbackNotReady e) {
        if (!engine.getAllowSubtypeFailure()) {
          throw e;
        }
        // Only allow the exception, if the subtyping constraints are not resolved
        // completely!
        engine.addDeferredConstraint(this);
      }
    }

    @Override
    public Constraint applySubst(Subst subst) {
      HMXType[] newTypes = new HMXType[this.originalTypes.length];
      newTypes = Arrays.asList(this.originalTypes).stream().map(t -> subst.apply(t)).toList()
          .toArray(newTypes);

      return new DetermineByCallbackAndUnify(this.callback, this.unifyAgainst, newTypes);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof DetermineByCallbackAndUnify dt
          && List.of(this.originalTypes).equals(List.of(dt.originalTypes))
          && this.callback.equals(dt.callback) && this.unifyAgainst.equals(dt.unifyAgainst);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.unifyAgainst, this.callback, List.of(this.originalTypes));
    }

    @Override
    public List<Constraint> getChildren() {
      return List.of();
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

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Inst in && this.type.equals(in.type) && this.variable.equals(in.variable)
          && this.solved == in.solved;
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.type, this.variable, this.solved);
    }

    @Override
    public List<Constraint> getChildren() {
      return List.of();
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

    @Override
    public boolean equals(Object obj) {
      return obj instanceof And and && this.constraints.equals(and.constraints);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.constraints);
    }

    @Override
    public List<Constraint> getChildren() {
      return List.copyOf(this.constraints);
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
      return new Exists(this.toQuantify.stream().map(tv -> new HMXType.Var(tv)).map(tv -> tv.deref())
          .map(tv -> subst.apply(tv)).filter(tv -> tv instanceof HMXType.Var)
          .map(tv -> ((HMXType.Var) tv).tyVar).toList(), this.inner.applySubst(subst));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Exists ext && this.toQuantify.equals(ext.toQuantify) && this.inner.equals(ext.inner);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.toQuantify, this.inner);
    }

    @Override
    public List<Constraint> getChildren() {
      return List.of(this.inner);
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
        newEnv.put(binding.name,
            new Scheme(List.of(), binding.scheme.type(), binding.scheme.constr()));
      }

      for (var binding : this.bindings) {
        var scheme = engine.solveConstraintAndGeneralize(binding.scheme.constr(), newEnv,
            binding.scheme.type());
        // This scheme is a shell scheme, that just holds the unsolved constraints (i.e.
        // Sub constrs)
        var emptyVarScheme = new Scheme(List.of(), scheme.type(), scheme.constr());
        newEnv.put(binding.name, emptyVarScheme);
      }

      // As a final stage, try to resolve the schemes whereever possible, before
      // moving to the body!
      for (var binding : this.bindings) {
        var scheme = newEnv.get(binding.name);
        // Solve the shell scheme. After that, all unsolved schemes are actually
        // errornous!
        var newScheme = engine.solveConstraintAndGeneralize(scheme.constr(), newEnv,
            scheme.type());

        newEnv.put(binding.name, newScheme);
      }

      // There is another problem here: While some schemes are fully solved, others
      // have dropped their constraints needed for full solutions!
      engine.solveConstraint(this.inner, newEnv);
    }

    @Override
    public Constraint applySubst(Subst subst) {
      return new LetRec(this.bindings.stream().map(b -> b.applySubst(subst)).toList(),
          this.inner.applySubst(subst));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof LetRec rec && this.bindings.equals(rec.bindings) && this.inner.equals(rec.inner);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.bindings, this.inner);
    }

    @Override
    public List<Constraint> getChildren() {
      var children = new ArrayList<Constraint>();

      for (var binding : this.bindings) {
        children.add(binding.scheme().constr());
      }

      children.add(this.inner);

      return List.copyOf(children);
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
        var scheme = engine.solveConstraintAndGeneralize(binding.scheme.constr(), newEnv,
            binding.scheme.type());
        newEnv.put(binding.name, scheme);
      }

      engine.solveConstraint(this.inner, newEnv);
    }

    @Override
    public Constraint applySubst(Subst subst) {
      return new LetSeq(this.bindings.stream().map(b -> b.applySubst(subst)).toList(),
          this.inner.applySubst(subst));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof LetRec rec && this.bindings.equals(rec.bindings) && this.inner.equals(rec.inner);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.bindings, this.inner);
    }

    @Override
    public List<Constraint> getChildren() {
      var children = new ArrayList<Constraint>();

      for (var binding : this.bindings) {
        children.add(binding.scheme().constr());
      }

      children.add(this.inner);

      return List.copyOf(children);
    }
  }

}

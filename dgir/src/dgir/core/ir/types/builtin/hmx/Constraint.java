package dgir.core.ir.types.builtin.hmx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.builtin.hmx.traits.OneShotConstraint;

/**
 * The HM(X) constraint language. The set of constraints is closed (sealed):
 * the solver reasons about exactly these kinds of leaves.
 *
 * <p>
 * Of the leaves, only {@link DetermineByCallbackAndUnify} can stay undecided:
 * {@link Equal} unifies, and {@link Sub} is normalized immediately into merged
 * variables and bounds. A determine leaf needs concrete argument types (like
 * resolving an overloaded operator), so while those are unknown it stays in
 * the constraint of the enclosing scheme and is resolved per instance.
 */
public abstract sealed class Constraint {

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  public abstract void solve(TypeInference engine, Env env);

  public abstract Constraint applySubst(Subst subst);

  public abstract List<Constraint> getChildren();

  public abstract Set<TypeVar<HMXType>> freeTypeVars();

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

    @Override
    public String toString() {
      return this.tLeft + " = " + this.tRight;
    }

    @Override
    public Set<TypeVar<HMXType>> freeTypeVars() {
      Set<TypeVar<HMXType>> freeTypeVars = new HashSet<>();
      freeTypeVars.addAll(this.tLeft.freeTypeVars());
      freeTypeVars.addAll(this.tRight.freeTypeVars());
      return Set.copyOf(freeTypeVars);
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

    @Override
    public String toString() {
      return "true";
    }

    @Override
    public Set<TypeVar<HMXType>> freeTypeVars() {
      return Set.of();
    }
  }

  /**
   * Subtype constraint tLeft <: tRight. Solved immediately by normalization (see
   * {@link TypeInference#subtype}), so it never remains in a scheme: what it
   * says about variables is kept as merged variables and bounds, which
   * instantiation copies onto each instance.
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
      // Normalize immediately: decompose structural subtypes, merge variables
      // and record bounds on the variables involved. Nothing here waits for
      // more information: a leaf over a variable only records a bound (checked
      // once the variable gets a type), and a leaf over concrete types is
      // decided for good. The bounds are part of the variables, so they travel
      // with a scheme (Scheme.toSubst copies them onto each instance).
      engine.subtype(this.tLeft, this.tRight);
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

    @Override
    public String toString() {
      return this.tLeft + " <: " + this.tRight;
    }

    @Override
    public Set<TypeVar<HMXType>> freeTypeVars() {
      Set<TypeVar<HMXType>> freeTypeVars = new HashSet<>();
      freeTypeVars.addAll(this.tLeft.freeTypeVars());
      freeTypeVars.addAll(this.tRight.freeTypeVars());
      return Set.copyOf(freeTypeVars);
    }
  }

  /**
   * Computes a result type from the argument types with a callback (e.g. the
   * result width of an arithmetic operation) and unifies it with
   * {@link #unifyAgainst}. The callback needs concrete argument types; while
   * they are unknown it throws {@link CallbackNotReady} and the leaf is
   * deferred. This is the only kind of leaf that remains in a scheme's
   * constraint, and it can only be resolved per instance, once that instance's
   * argument types are known.
   */
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
        if (!engine.getAllowDeferral()) {
          throw e;
        }
        // During inference the argument types may not be decided yet: defer.
        // The leaf ends up in the enclosing scheme's constraint (or, at the top
        // level, in the global deferred list) and is retried there.
        engine.addDeferredConstraint(this);
      }
    }

    @Override
    public Constraint applySubst(Subst subst) {
      HMXType[] newTypes = new HMXType[this.originalTypes.length];
      newTypes = Arrays.asList(this.originalTypes).stream().map(t -> subst.apply(t)).toList()
          .toArray(newTypes);

      // The result variable is part of the leaf like its arguments: an
      // instance must determine its own result, not the scheme's.
      return new DetermineByCallbackAndUnify(this.callback, subst.apply(this.unifyAgainst), newTypes);
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

    @Override
    public String toString() {
      var types = Arrays.stream(this.originalTypes).map(Object::toString)
          .collect(java.util.stream.Collectors.joining(", "));
      return "determine(" + types + ") ~ " + this.unifyAgainst;
    }

    @Override
    public Set<TypeVar<HMXType>> freeTypeVars() {
      Set<TypeVar<HMXType>> ftv = new HashSet<>();

      ftv.addAll(this.unifyAgainst.freeTypeVars());
      for (int i = 0; i < this.originalTypes.length; i++) {
        ftv.addAll(this.originalTypes[i].freeTypeVars());
      }

      return Set.copyOf(ftv);
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

      engine.unify(inferredType.getLeft(), this.type);
      engine.solveConstraint(inferredType.getRight(), env);
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

    @Override
    public String toString() {
      return "inst(" + this.variable + " : " + this.type + ")" + (this.solved ? " [solved]" : "");
    }

    @Override
    public Set<TypeVar<HMXType>> freeTypeVars() {
      return this.type.freeTypeVars();
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

    @Override
    public String toString() {
      return this.constraints.stream().map(Object::toString)
          .collect(java.util.stream.Collectors.joining(" ∧ ", "(", ")"));
    }

    @Override
    public Set<TypeVar<HMXType>> freeTypeVars() {
      Set<TypeVar<HMXType>> ftv = new HashSet<>();
      for (var constr : this.constraints) {
        ftv.addAll(constr.freeTypeVars());
      }
      return Set.copyOf(ftv);
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

    @Override
    public String toString() {
      var vars = this.toQuantify.stream().map(Object::toString)
          .collect(java.util.stream.Collectors.joining(" "));
      return "∃" + vars + ". " + this.inner;
    }

    @Override
    public Set<TypeVar<HMXType>> freeTypeVars() {
      var ftv = new HashSet<>(this.inner.freeTypeVars());

      ftv.addAll(this.toQuantify);
      return Set.copyOf(ftv);
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

    @Override
    public String toString() {
      var bindings = this.bindings.stream()
          .map(b -> b.name() + " = " + b.scheme())
          .collect(java.util.stream.Collectors.joining(" and "));
      return "let rec " + bindings + " in " + this.inner;
    }

    @Override
    public Set<TypeVar<HMXType>> freeTypeVars() {
      var ftv = new HashSet<>(this.inner.freeTypeVars());

      for (var bnd : this.bindings) {
        ftv.addAll(bnd.scheme.freeTypeVars());
      }

      return Set.copyOf(ftv);
    }

  }

  private static List<MixBinding> openRecList(List<MixBinding> bindings) {
    return bindings.stream().filter(MixBinding::recursive).toList();
  }

  /**
   * A single binding of a {@link LetMix} group: the binding itself plus its
   * recursion marker. Recursive bindings take part in the group's
   * monomorphic core (like OCaml's {@code let rec}); non-recursive bindings
   * generalize immediately after their own definition (like a plain {@code
   * let}).
   */
  public record MixBinding(LetBinding binding, boolean recursive) {
    public MixBinding applySubst(Subst subst) {
      return new MixBinding(this.binding.applySubst(subst), this.recursive);
    }
  }

  /**
   * A mixed let/let-rec binding group. Bindings are processed in their given
   * order, so every binding sees the generalized schemes of all preceding
   * non-recursive bindings. Recursive bindings are pre-seeded with
   * monomorphic shells so forward and mutual references resolve, stay
   * monomorphic for the whole group, and are generalized when the group
   * ends.
   */
  public static final class LetMix extends Constraint {

    public final List<MixBinding> bindings;
    public final Constraint inner;

    public LetMix(List<MixBinding> bindings, Constraint inner) {
      this.bindings = List.copyOf(bindings);
      this.inner = inner;
    }

    @Override
    public void solve(TypeInference engine, Env env) {
      var newEnv = env.copy();
      // The enclosing scopes' free variables: the exact set no binding may
      // quantify. Captured before any shell enters the environment.
      var outerFtv = env.freeTypeVars();

      // Pre-seed every binding with a monomorphic shell so forward and
      // mutual references resolve during the ordered processing below.
      for (var mixBinding : this.bindings) {
        var binding = mixBinding.binding();
        binding.scheme.vars().forEach(v -> v.setLevel(engine.getCurrentLevel()));
        newEnv.put(binding.name(),
            new Scheme(List.of(), binding.scheme.type(), binding.scheme.constr()));
      }

      // Phase A — the recursive core: solve all recursive bindings
      // monomorphically against the shells (mutual and forward references
      // resolve inside the group).
      for (var mixBinding : this.bindings) {
        if (!mixBinding.recursive()) {
          continue;
        }
        var binding = mixBinding.binding();
        var scheme = engine.solveConstraintAndGeneralize(binding.scheme.constr(), newEnv,
            binding.scheme.type());
        newEnv.put(binding.name(),
            new Scheme(List.of(), scheme.type(), scheme.constr()));
      }

      // Phase B — generalize the recursive core: re-solve its residual
      // constraints with the group converged, then publish.
      for (var mixBinding : openRecList(this.bindings)) {
        var scheme = newEnv.get(mixBinding.binding().name());
        var newScheme = engine.solveConstraintAndGeneralize(scheme.constr(), newEnv,
            scheme.type());

        var owned = new java.util.LinkedHashSet<TypeVar<HMXType>>(
            newScheme.type().deref().freeTypeVars());
        // Keep every variable generalize quantified, including those only in
        // the residual constraint; they must be fresh per instantiation.
        newScheme.vars().forEach(v -> owned.add(v.find()));
        owned.removeIf(v -> outerFtv.contains(v.find()));
        engine.markGeneric(owned);

        newEnv.put(mixBinding.binding().name(),
            new Scheme(List.copyOf(owned), newScheme.type(), newScheme.constr()));
      }

      // Phase C — non-recursive bindings, in order: each solves against the
      // already-generalized recursive core (and all previously generalized
      // lets), then generalizes immediately — every later binding and the
      // body see it polymorphically.
      for (var mixBinding : this.bindings) {
        if (mixBinding.recursive()) {
          continue;
        }
        var binding = mixBinding.binding();
        var scheme = engine.solveConstraintAndGeneralize(binding.scheme.constr(), newEnv,
            binding.scheme.type());

        var owned = new java.util.LinkedHashSet<TypeVar<HMXType>>(
            scheme.type().deref().freeTypeVars());
        scheme.vars().forEach(v -> owned.add(v.find()));
        owned.removeIf(v -> outerFtv.contains(v.find()));
        engine.markGeneric(owned);

        newEnv.put(binding.name(),
            new Scheme(List.copyOf(owned), scheme.type(), scheme.constr()));
      }

      // Final convergence pass: re-solve every published scheme's constraint
      // against the original variables of its definition.
      //
      // FIXME: since subtype leaves are normalized, a scheme's constraint only
      // holds determine leaves (DetermineByCallbackAndUnify), e.g. the result
      // type of an arithmetic or comparison operation, waiting for concrete
      // argument types. Deferral is still allowed here, so most of
      // them are just deferred again, now into the global deferred list. There
      // the fixpoint loop in TypeInference.solve decides their argument
      // variables and runs them, on the originals.
      //
      // Reconstruction needs that, because it reads the originals for these
      // results: Subst.expand instantiates only the variables of the
      // referenced type, not the scheme's constraint-only variables, and it
      // does not resolve the scheme's determine leaves for the instance. For
      // example, main's copies of foo's result in
      // MixedWidthAnnotationProgramTest.instanceDependentInternalType are not
      // mentioned by main's type (unit -> unit), so they stay open without
      // this pass.
      //
      // Deciding them on the original is only right for results that are the
      // same for every instance, or for a definition with a single instance
      // (main). The originals' generic variables are decided from evidence
      // only and never guessed, so a determine leaf over them stays open here
      // rather than being fixed for all instances. Every leaf re-deferred here
      // lands in the global list as a "schematic" leaf, which
      // verifyPendingConstraints has to skip.
      //
      // Removing this pass requires reconstruction to instantiate the full
      // scheme (all quantified variables) and resolve its determine leaves per
      // instance, the way overloading is resolved during translation.
      for (var mixBinding : this.bindings) {
        var scheme = newEnv.get(mixBinding.binding().name());
        engine.solveConstraint(scheme.constr(), newEnv);
      }

      engine.solveConstraint(this.inner, newEnv);
    }

    @Override
    public Constraint applySubst(Subst subst) {
      return new LetMix(this.bindings.stream().map(b -> b.applySubst(subst)).toList(),
          this.inner.applySubst(subst));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof LetMix mix && this.bindings.equals(mix.bindings)
          && this.inner.equals(mix.inner);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.bindings, this.inner);
    }

    @Override
    public List<Constraint> getChildren() {
      var children = new ArrayList<Constraint>();
      for (var mixBinding : this.bindings) {
        children.add(mixBinding.binding().scheme().constr());
      }
      children.add(this.inner);
      return List.copyOf(children);
    }

    @Override
    public String toString() {
      var bindings = this.bindings.stream()
          .map(b -> (b.recursive() ? "rec " : "") + b.binding().name() + " = " + b.binding().scheme())
          .collect(java.util.stream.Collectors.joining("\n and "));
      return "let \n" + bindings + "\nin " + this.inner;
    }

    @Override
    public Set<TypeVar<HMXType>> freeTypeVars() {
      var ftv = new HashSet<>(this.inner.freeTypeVars());

      for (var bnd : this.bindings) {
        ftv.addAll(bnd.binding.scheme.freeTypeVars());
      }

      return Set.copyOf(ftv);
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

    @Override
    public String toString() {
      var bindings = this.bindings.stream()
          .map(b -> b.name() + " = " + b.scheme())
          .collect(java.util.stream.Collectors.joining("; "));
      return "let " + bindings + " in " + this.inner;
    }

    @Override
    public Set<TypeVar<HMXType>> freeTypeVars() {
      var ftv = new HashSet<>(this.inner.freeTypeVars());

      for (var bnd : this.bindings) {
        ftv.addAll(bnd.scheme.freeTypeVars());
      }

      return Set.copyOf(ftv);
    }
  }

}

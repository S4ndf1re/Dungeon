package dgir.core.ir.types.builtin.hmx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeInferenceSolver;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.Expression.ExpressionVisitor;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitGetChildrenOption;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.builtin.hmx.expr.Ann;
import dgir.core.ir.types.builtin.hmx.expr.App;
import dgir.core.ir.types.builtin.hmx.Constraint.DetermineByCallbackAndUnify;
import dgir.core.ir.types.builtin.hmx.expr.Abs;
import dgir.core.ir.types.builtin.hmx.expr.LetMix;
import dgir.core.ir.types.builtin.hmx.expr.LetRec;
import dgir.core.ir.types.builtin.hmx.expr.Lit;
import dgir.core.ir.types.builtin.hmx.expr.Seq;
import dgir.core.ir.types.builtin.hmx.expr.Var;
import dgir.core.ir.types.builtin.hmx.expr.Block;
import dgir.core.ir.types.builtin.hmx.traits.OneShotConstraint;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;

public final class TypeInference
    extends TypeInferenceSolver<TypeInference, HMXExpr, HMXType> {

  private int currentLevel;
  private boolean allowDeferral;
  private ArrayList<Constraint> deferredConstraints;
  private final Set<TypeVar<HMXType>> genericVars = new HashSet<>();

  public TypeInference() {
    this(new TypeDialectConverterRegistry());
    this.currentLevel = 0;
    this.allowDeferral = false;
    this.deferredConstraints = new ArrayList<>();
  }

  public TypeInference(TypeDialectConverterRegistry registry) {
    super(registry);
    this.currentLevel = 0;
    this.allowDeferral = false;
    this.deferredConstraints = new ArrayList<>();
  }

  @Override
  public Pair<HMXType, Optional<ConversionContext<HMXExpr, HMXType>>> generalNominalTypeToInferenceType(
      GeneralParameterizedNominalType type,
      Optional<ConversionContext<HMXExpr, HMXType>> data) {
    List<HMXType> paramTypes = type.getTypedParameters().stream().map(param -> switch (param) {
      case GeneralTypeParameter.Concrete con -> this.generalNominalTypeToInferenceType(con.ty(), data).getLeft();
      case GeneralTypeParameter.Unknown unk -> new HMXType.Var(new TypeVar<>());
      case GeneralTypeParameter.Numeric num -> new HMXType.NumericType(num.number());
    }).toList();

    return Pair.of(new HMXType.LitType(type.getIdent(), paramTypes), null);
  }

  @Override
  public HMXExpr Let(List<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings, HMXExpr body) {
    return new LetRec(bindings, body);
  }

  @Override
  public HMXExpr LetPoly(
      List<Pair<Symbol<HMXExpr, HMXType>, Pair<HMXExpr, Boolean>>> bindings, HMXExpr body) {
    var mixDefs = bindings.stream()
        .map(bnd -> new LetMix.MixDef(bnd.getLeft(), bnd.getRight().getLeft(),
            bnd.getRight().getRight()))
        .toList();
    return new LetMix(mixDefs, body);
  }

  @Override
  public HMXExpr Seq(List<HMXExpr> exprs) {
    return new Seq(exprs);
  }

  @Override
  public HMXExpr Var(Symbol<HMXExpr, HMXType> symbol) {
    return new Var(symbol);
  }

  @Override
  public HMXExpr Lit(Literal lit) {
    return new Lit(lit);
  }

  @Override
  public HMXExpr App(HMXExpr func, List<HMXExpr> args) {
    return new App(func, args);
  }

  @Override
  public HMXExpr Abs(List<Symbol<HMXExpr, HMXType>> params, HMXExpr body) {
    return new Abs(params, body);
  }

  @Override
  public HMXExpr Ann(HMXExpr body, HMXType type) {
    return new Ann(body, type);
  }

  @Override
  public HMXExpr Block(HMXExpr body) {
    return new Block(body);
  }

  /**
   * Enqueues every free type variable of {@code type} (union-find roots only)
   * that has not been enqueued yet.
   */
  private void enqueueTypeVars(HMXType type, Deque<TypeVar<HMXType>> queue, Set<TypeVar<HMXType>> enqueued) {
    for (var tv : type.freeTypeVars()) {
      var root = tv.find();
      if (enqueued.add(root)) {
        queue.add(root);
      }
    }
  }

  /**
   * Enqueues the union-find roots of all free type variables of a deferred
   * constraint, so the determination worklist can compose bounds for variables
   * that are only referenced by constraints still waiting to be solved.
   */
  private void enqueueTypeVars(Constraint constraint, Deque<TypeVar<HMXType>> queue, Set<TypeVar<HMXType>> enqueued) {
    for (var tv : constraint.freeTypeVars()) {
      var root = tv.find();
      if (enqueued.add(root)) {
        queue.add(root);
      }
    }
  }

  public void markGeneric(Collection<TypeVar<HMXType>> vars) {
    this.genericVars.addAll(vars);
  }

  private boolean isGeneric(TypeVar<HMXType> tv) {
    var root = tv.find();
    return this.genericVars.stream().anyMatch(v -> v.find() == root);
  }

  private boolean determineWorklist(Deque<TypeVar<HMXType>> queue, Set<TypeVar<HMXType>> visited,
      boolean allowUpperBoundGuess, boolean keepGenericOpen) {
    var progress = false;
    while (!queue.isEmpty()) {
      var root = queue.poll().find();
      var guess = allowUpperBoundGuess && !(keepGenericOpen && this.isGeneric(root));
      progress |= new HMXType.Var(root).determineTypeWhenBoundConstrainted(this, guess);
      for (var bound : root.getLowerBounds()) {
        this.enqueueTypeVars(bound, queue, visited);
      }
      for (var bound : root.getUpperBounds()) {
        this.enqueueTypeVars(bound, queue, visited);
      }
      if (root.getAssigendType().isPresent()) {
        this.enqueueTypeVars(root.getAssigendType().get(), queue, visited);
      }
    }
    return progress;
  }

  @Override
  public SolveResult<HMXExpr, HMXType> solve(ExprOrOperator<HMXExpr, HMXType> exprOrOp) {
    Env env = new Env();
    HMXExpr expr = this.asExpression(exprOrOp);
    var resultType = new HMXType.Var(new TypeVar<HMXType>());
    GenerateResult res = this.generate(expr, env, resultType);


    // Allow Deferral, i.e. collect all constraints that could not get solved, yet.
    // Those constrants land in the deferred constraint list and is to be solved later
    this.allowDeferral = true;
    this.solveConstraint(new Constraint.Exists(res.constr(), resultType.tyVar), env);
    this.allowDeferral = false;


    // Pass 1: determination from lower bounds only — evidence-driven; the
    // deferred constraints drain until stable and annotations decide the rest.
    // Pass 2: the exact same logic again, but with upper+lower bound type
    // selection, resolving whatever pass 1 had to leave open.
    this.runToFixpoint(expr, resultType, env);

    // The result types are unsound as constraints where not solvable
    var remaining = this.deferredConstraints.stream()
        .filter(c -> !(c instanceof Constraint.Trivial))
        .filter(c -> c.freeTypeVars().stream().noneMatch(this::isGeneric))
        .toList();
    if (!remaining.isEmpty()) {
      throw new TypingException.NotFullySpecified(resultType.deref());
    }

    var instantiated = expr.instantiate(this, new InstEnv<>(expr), Subst.newEmpty());

    instantiated = this.postSolve(instantiated);

    return new SolveResult<>(resultType.deref(), expr, instantiated);
  }

  /**
   * One round of deferred constraint draining plus lower-bound-driven
   * determination over the not-yet-instantiated expression.
   */
  private boolean deferredAndDeterminationRound(HMXExpr expr, HMXType resultType, Env env,
      boolean allowUpperBoundGuess) {
    var progress = false;

    if (!this.deferredConstraints.isEmpty()) {
      var pending = new ArrayList<>(this.deferredConstraints);
      this.deferredConstraints.clear();
      for (var leaf : pending) {
        try {
          this.solveConstraint(leaf, env);
        } catch (DetermineByCallbackAndUnify.CallbackNotReady e) {
          this.addDeferredConstraint(leaf);
        }
      }
      progress |= this.deferredConstraints.size() != pending.size();
    }

    var queue = new ArrayDeque<TypeVar<HMXType>>();
    var visited = new HashSet<TypeVar<HMXType>>();
    this.enqueueTypeVars(resultType, queue, visited);
    for (var deferred : this.deferredConstraints) {
      this.enqueueTypeVars(deferred, queue, visited);
    }
    // Quantified variables are reachable only through their scheme; seed them
    // so evidence (lower bounds) gathered on them is not left undecided.
    for (var generic : this.genericVars) {
      var root = generic.find();
      if (visited.add(root)) {
        queue.add(root);
      }
    }
    // Every expression's type, including the bodies of let-bound definitions:
    // a variable decided only by bounds (subtype leaves are normalized away)
    // is otherwise reachable from nowhere and would stay open.
    new ExpressionVisitor<HMXExpr, HMXType>(VisitOrder.IN_ORDER, VisitGetChildrenOption.ALL_CHILDREN).visit(expr,
        e -> {
          if (e.getInferredType().isPresent()) {
            this.enqueueTypeVars(e.getInferredType().get(), queue, visited);
          }
        });
    progress |= this.determineWorklist(queue, visited, allowUpperBoundGuess, true);
    return progress;
  }

  /**
   * Runs deferred constraint draining plus determination to fixpoint, in two
   * passes: first with lower-bound-only type selection, then again with
   * upper+lower bound selection to resolve whatever pass 1 left open.
   */
  private void runToFixpoint(HMXExpr expr, HMXType resultType, Env env) {
    var progress = true;
    while (progress) {
      progress = this.deferredAndDeterminationRound(expr, resultType, env, false);
    }
    progress = true;
    while (progress) {
      progress = this.deferredAndDeterminationRound(expr, resultType, env, true);
    }
  }

  /**
   * First infer, the `exprOrOp`. If `exprOrOp` is of type Operator, first convert
   * to an {@link HMXExpr} using the conversion untilities and buffer the
   * converted
   * result.
   *
   * <p>
   * After inferring either the {@link HMXExpr} or the {@Link Operation}, store
   * the
   * inferred result in combination with the already inferred {@link HMXExpr}
   */
  public GenerateResult generate(HMXExpr expr, Env env, HMXType type) {
    this.currentLevel += 1;
    GenerateResult res = expr.generate(this, env, type);
    this.currentLevel -= 1;
    expr.setInferredType(Optional.ofNullable(type.deref()));
    return res;
  }

  /**
   * Unifies two types: makes them equal by assigning and merging type
   * variables. A variable on either side is bound to the other side (the right
   * one first); otherwise both sides must have the same shape, and their parts
   * are unified pairwise.
   *
   * @throws TypingException.UnificationFailed if the shapes differ
   */
  public void unify(HMXType left, HMXType right) {
    if (right instanceof HMXType.Var rightVar) {
      this.unifyVar(rightVar, left);
      return;
    }

    switch (left) {
      case HMXType.Var leftVar -> this.unifyVar(leftVar, right);
      case HMXType.Arrow a -> {
        if (!(right instanceof HMXType.Arrow b)) {
          throw new TypingException.UnificationFailed(left, right);
        }
        this.unify(a.from, b.from);
        this.unify(a.to, b.to);
      }
      case HMXType.LitType a -> {
        if (!(right instanceof HMXType.LitType b && b.tyName.equals(a.tyName))) {
          throw new TypingException.UnificationFailed(left, right);
        }
        if (a.parameters.size() != b.parameters.size()) {
          throw new RuntimeException(
              "Parameter count mismatch: " + a.parameters.size() + " vs " + b.parameters.size());
        }
        for (int i = 0; i < a.parameters.size(); i++) {
          this.unify(a.parameters.get(i), b.parameters.get(i));
        }
      }
      case HMXType.NumericType a -> {
        if (!(right instanceof HMXType.NumericType b && a.size == b.size)) {
          throw new TypingException.UnificationFailed(left, right);
        }
      }
      case HMXType.Tuple a -> {
        if (!(right instanceof HMXType.Tuple b)) {
          throw new TypingException.UnificationFailed(left, right);
        }
        if (a.elements.size() != b.elements.size()) {
          throw new TypingException.TupleSizeMismatch(a, b, a.elements.size(), b.elements.size());
        }
        for (int i = 0; i < a.elements.size(); i++) {
          this.unify(a.elements.get(i), b.elements.get(i));
        }
      }
    }
  }

  /**
   * Binds a type variable to another type, or merges it with another variable.
   * A variable that already has a type unifies that type instead. Before a
   * variable gets a type, the type must satisfy the variable's bounds; a
   * failing subtype check aborts immediately.
   */
  private void unifyVar(HMXType.Var var, HMXType other) {
    if (other instanceof HMXType.Var otherVar) {
      // Two variables: merge their classes. If both already had types, those
      // types must agree as well.
      var assigned = var.tyVar.unify(otherVar.tyVar);
      if (assigned.isPresent()) {
        this.unify(assigned.get().getLeft(), assigned.get().getRight());
      }
      return;
    }

    if (other.occursCheck(var.tyVar)) {
      throw new TypingException.OccursCheckFailed(other, var.tyVar);
    }

    if (var.tyVar.getAssigendType().isPresent()) {
      this.unify(var.tyVar.getAssigendType().get(), other);
      return;
    }

    other.occursCheckAjustLevel(var.tyVar);
    for (var lowerBound : var.tyVar.getLowerBounds()) {
      this.subtype(lowerBound, other);
    }
    for (var upperBound : var.tyVar.getUpperBounds()) {
      this.subtype(other, upperBound);
    }
    var.tyVar.assignType(other);
  }

  @Override
  public HMXType Arrow(HMXType from, HMXType to) {
    return new HMXType.Arrow(from, to);
  }

  public static final class UnresolvedSubtypeException extends RuntimeException {
    public final HMXType left;
    public final HMXType right;

    public UnresolvedSubtypeException(HMXType left, HMXType right) {
      super("Unresolved subtype constraint: " + left + " <: " + right);
      this.left = left;
      this.right = right;
    }
  }

  public void subtype(HMXType left, HMXType right) {
    var l = left.deref();
    var r = right.deref();


    if (l instanceof HMXType.Var lv) {
      if (r instanceof HMXType.Var rv) {
        var unifyRes = lv.tyVar.unify(rv.tyVar);
        if (unifyRes.isPresent()) {
          this.subtype(unifyRes.get().getLeft(), unifyRes.get().getRight());
        }
        return;
      }

      r.occursCheckAjustLevel(lv.tyVar);

      // Closure: every lower bound of the variable must fit the new upper
      // bound (L <: var <: r implies L <: r). Only a new bound needs the check.
      if (lv.tyVar.assignUpperBound(r)) {
        for (var lower : lv.tyVar.getLowerBounds()) {
          this.subtype(lower, r);
        }
      }

      return;
    }

    if (r instanceof HMXType.Var rv) {

      l.occursCheckAjustLevel(rv.tyVar);

      // Closure: the new lower bound must fit every upper bound of the
      // variable (l <: var <: U implies l <: U).
      if (rv.tyVar.assignLowerBound(l)) {
        for (var upper : rv.tyVar.getUpperBounds()) {
          this.subtype(l, upper);
        }
      }

      return;
    }

    if (l instanceof HMXType.Arrow a && r instanceof HMXType.Arrow b) {
      // Contravariant in the parameter, covariant in the result: the
      // operands of the parameter check are swapped.
      this.subtype(b.from, a.from);
      this.subtype(a.to, b.to);
      return;
    }

    if (l instanceof HMXType.LitType a && r instanceof HMXType.LitType b) {
      try {
        var aType = a.toIrType();
        var bType = b.toIrType();

        if (aType.isSubtypeOf(bType)) {
          return;
        }
      } catch (TypingException.NotFullySpecified e) {
        // Do nothing, this case may be ok, but only if the rest matches!
      }

      if (a.tyName.equals(b.tyName)) {
        if (a.parameters.size() != b.parameters.size()) {
          throw new RuntimeException(
              "Parameter count mismatch: " +
                  a.parameters.size() +
                  " vs " +
                  b.parameters.size());
        }

        // Covariant type parameters.
        for (int i = 0; i < a.parameters.size(); i++) {
          this.subtype(a.parameters.get(i), b.parameters.get(i));
        }

        return;
      }

      throw new TypingException.SubtypingFailed(l, r);
    }

    if (l instanceof HMXType.NumericType a && r instanceof HMXType.NumericType b) {
      if (a.size <= b.size) {
        return;
      }

      throw new TypingException.SubtypingFailed(l, r);
    }

    if (l instanceof HMXType.Tuple a && r instanceof HMXType.Tuple b) {
      if (a.elements.size() != b.elements.size()) {
        throw new TypingException.TupleSizeMismatch(
            a,
            b,
            a.elements.size(),
            b.elements.size());
      }

      // Covariant element-wise.
      for (int i = 0; i < a.elements.size(); i++) {
        this.subtype(a.elements.get(i), b.elements.get(i));
      }

      return;
    }

    throw new TypingException.SubtypingFailed(l, r);
  }

  public int getCurrentLevel() {
    return this.currentLevel;
  }

  public boolean getAllowDeferral() {
    return this.allowDeferral;
  }

  public void setAllowDeferral(boolean allowDeferral) {
    this.allowDeferral = allowDeferral;
  }

  public void solveConstraint(Constraint constr, Env env) {
    if (constr instanceof OneShotConstraint oneShot && oneShot.isSolved()) {
      return;
    }

    if (constr instanceof OneShotConstraint oneShotSolved) {
      oneShotSolved.markSolved();
    }

    this.currentLevel += 1;
    constr.solve(this, env);
    this.currentLevel -= 1;

  }

  public Scheme solveConstraintAndGeneralize(Constraint constr, Env env, HMXType type) {
    var markConstraint = new Constraint.Trivial();
    this.addDeferredConstraint(markConstraint);

    // Subtype leaves are normalized right away (Sub.solve). Determine leaves
    // whose argument types are not decided yet defer; everything deferred
    // after the mark becomes this scheme's constraint and is re-solved at
    // every instantiation during inference.
    var oldAllowDeferral = this.getAllowDeferral();
    this.setAllowDeferral(true);
    this.solveConstraint(constr, env);
    this.setAllowDeferral(oldAllowDeferral);

    var finalType = type.deref();
    var idx = this.deferredConstraints.indexOf(markConstraint);

    List<Constraint> deferred = List.of();
    if (this.deferredConstraints.size() > 1) {
      deferred = this.deferredConstraints.subList(idx + 1, this.deferredConstraints.size());
    }
    this.deferredConstraints = new ArrayList<>(this.deferredConstraints.subList(0, idx));

    return finalType.generalize(this.currentLevel, new Constraint.And(deferred));
  }

  public void addDeferredConstraint(Constraint constr) {
    this.deferredConstraints.add(constr);
  }
}

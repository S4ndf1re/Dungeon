package dgir.core.ir.types.builtin.hmx;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeInferenceSolver;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.builtin.hmx.expr.LetRec;
import dgir.core.ir.types.builtin.hmx.expr.Lit;
import dgir.core.ir.types.builtin.hmx.expr.Seq;
import dgir.core.ir.types.builtin.hmx.expr.Var;
import dgir.core.ir.types.builtin.hmx.traits.OneShotConstraint;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;

public final class TypeInference
    extends TypeInferenceSolver<TypeInference, HMXExpr, HMXType> {

  private int currentLevel;
  private boolean allowSubtypeFailure;
  private ArrayList<Constraint> deferredConstraints;

  public TypeInference() {
    this(new TypeDialectConverterRegistry());
    this.currentLevel = 0;
    this.allowSubtypeFailure = false;
    this.deferredConstraints = new ArrayList<>();
  }

  public TypeInference(TypeDialectConverterRegistry registry) {
    super(registry);
    this.currentLevel = 0;
    this.allowSubtypeFailure = false;
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
  public HMXExpr newLetExpr(List<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings, HMXExpr body) {
    return new LetRec(bindings, body);
  }

  @Override
  public HMXExpr newSeqExpr(List<HMXExpr> exprs) {
    return new Seq(exprs);
  }

  @Override
  public HMXExpr newVarExpr(Symbol<HMXExpr, HMXType> symbol) {
    return new Var(symbol);
  }

  @Override
  public HMXExpr newLit(Literal lit) {
    return new Lit(lit);
  }

  @Override
  public SolveResult<HMXExpr, HMXType> solve(ExprOrOperator<HMXExpr, HMXType> exprOrOp) {
    Env env = new Env();
    HMXExpr expr = this.asExpression(exprOrOp);
    var resultType = new HMXType.Var(new TypeVar<HMXType>());
    GenerateResult res = this.generate(expr, env, resultType);

    // Since the result type var is actually not set for the engines level, this has
    // to be performed by a exists constraint on the first and outermost level!
    this.solveConstraint(new Constraint.Exists(res.constr(), resultType.tyVar), env);

    var instantiated = expr.instantiate(this, new InstEnv<>(expr), Subst.newEmpty());

    instantiated = this.postSolve(instantiated);

    return new SolveResult<>(resultType.deref(), expr, instantiated);
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

  public void unify(HMXType left, HMXType right) {
    if (right instanceof HMXType.Var) {
      right.unify(this, left);
      return;
    }
    left.unify(this, right);
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

      // Within scheme generalization, an open variable must NOT be equated
      // with the concrete type: assigning would collapse the scheme variables.
      // Defer the leaf into the scheme's stored constraint tree instead; it is
      // re-checked with fresh variables on every instantiation.
      if (this.allowSubtypeFailure) {
        throw new UnresolvedSubtypeException(l, r);
      }

      // Outside generalization the old conservative logic applies: equate the
      // variable with the concrete type. Every accepted program stays
      // well-typed; directional bounds are lost.
      if (r.occursCheck(lv.tyVar)) {
        throw new TypingException.OccursCheckFailed(r, lv.tyVar);
      }

      r.occursCheckAjustLevel(lv.tyVar);
      lv.tyVar.assignType(r);
      return;
    }

    if (r instanceof HMXType.Var rv) {
      if (this.allowSubtypeFailure) {
        throw new UnresolvedSubtypeException(l, r);
      }

      if (l.occursCheck(rv.tyVar)) {
        throw new TypingException.OccursCheckFailed(l, rv.tyVar);
      }

      l.occursCheckAjustLevel(rv.tyVar);
      rv.tyVar.assignType(l);
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

  public boolean getAllowSubtypeFailure() {
    return this.allowSubtypeFailure;
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

    var oldSubtypeFailure = this.getAllowSubtypeFailure();
    this.allowSubtypeFailure = true;
    this.solveConstraint(constr, env);
    this.allowSubtypeFailure = oldSubtypeFailure;

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

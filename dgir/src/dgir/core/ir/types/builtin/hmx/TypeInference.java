package dgir.core.ir.types.builtin.hmx;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Value;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.InferenceTree;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeInferenceSolver;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.builtin.hmx.traits.OneShotConstraint;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;
import dgir.core.ir.types.traits.IAbstraction;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.traits.ISymbol;

public final class TypeInference
    extends TypeInferenceSolver<TypeInference, HMXExpr, HMXType> {

  private int currentLevel;
  private boolean allowSubtypeFailure;

  public TypeInference() {
    this(new TypeDialectConverterRegistry());
    this.currentLevel = 0;
    this.allowSubtypeFailure = false;
  }

  public TypeInference(TypeDialectConverterRegistry registry) {
    super(registry);
    this.currentLevel = 0;
    this.allowSubtypeFailure = false;
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
  public HMXExpr generalBlockToInferenceExpr(GeneralBlock block) {
    ArrayList<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings = new ArrayList<>();
    Optional<Symbol<HMXExpr, HMXType>> lastValue = Optional.empty();

    for (var op : block.getOperations()) {
      var opOutput = op.getOutput();
      if (opOutput.isPresent()) {
        var sym = Symbol.<HMXExpr, HMXType>of(opOutput.get().getValue());
        var expr = this.asExpression(ExprOrOperator.of(op));
        if (expr.containsSymbol(sym)) {
          throw new TypingException.CyclicSymbolAssignment(sym, expr);
        }
        bindings.add(Pair.of(sym, expr));
        lastValue = Optional.of(sym);
      } else {
        /*
         * NOTE: handle everything as a returnable value, even though something like a
         * function is not actually a expression! This is done to correctly typecheck
         * each function and their parameters!
         */
        Symbol<HMXExpr, HMXType> sym = null;
        if (op.asOp() instanceof ISymbol isym) {
          sym = Symbol.<HMXExpr, HMXType>of(isym.getSymbol());
        } else {
          var val = new Value();
          sym = Symbol.<HMXExpr, HMXType>of(val);
        }
        var expr = this.asExpression(ExprOrOperator.of(op));
        if (expr.containsSymbol(sym)) {
          throw new TypingException.CyclicSymbolAssignment(sym, expr);
        }
        bindings.add(Pair.of(sym, expr));
        lastValue = Optional.of(sym);
      }
    }

    if (lastValue.isPresent()) {
      return new HMXExpr.ExprLetRec(bindings,
          new HMXExpr.ExprSeq(bindings.stream().filter(bnd -> !(bnd.getRight() instanceof IAbstraction))
              .map(bnd -> (HMXExpr) new HMXExpr.ExprVar(bnd.getLeft())).toList()));
    } else {
      return new HMXExpr.ExprLetRec(bindings, new HMXExpr.ExprLit(new Literal.Unit()));
    }
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

  public InferenceTree unify(HMXType left, HMXType right) {
    if (right instanceof HMXType.Var) {
      return right.unify(this, left);
    }
    return left.unify(this, right);
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

  public InferenceTree subtype(HMXType left, HMXType right) {
    var l = left.deref();

    var r = right.deref();

    if (l instanceof HMXType.Var lv) {
      if (r instanceof HMXType.Var rv) {
        var unifyRes = lv.tyVar.unify(rv.tyVar);
        if (unifyRes.isPresent()) {
          return this.subtype(unifyRes.get().getLeft(), unifyRes.get().getRight());
        }
        return new InferenceTree(
            "Sub-Var-Var",
            l + " <: " + r);
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
      return new InferenceTree(
          "Sub-Var",
          l + " <: " + r,
          r + "/" + l);
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
      return new InferenceTree(
          "Sub-Var-Reverse",
          l + " <: " + r,
          l + "/" + r);
     }

    if (l instanceof HMXType.Arrow a && r instanceof HMXType.Arrow b) {
      // Contravariant in the parameter, covariant in the result: the
      // operands of the parameter check are swapped.
      var fromCheck = this.subtype(b.from, a.from);
      var toCheck = this.subtype(a.to, b.to);

      return new InferenceTree(
          "Sub-Arrow",
          l + " <: " + r,
          "",
          List.of(fromCheck, toCheck));
    }

    if (l instanceof HMXType.LitType a && r instanceof HMXType.LitType b) {
      try {
        var aType = a.toIrType();
        var bType = b.toIrType();

        if (!aType.isSubtypeOf(bType)) {
          throw new TypingException.SubtypingFailed(l, r);
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
        var trees = new ArrayList<InferenceTree>();
        for (int i = 0; i < a.parameters.size(); i++) {
          trees.add(this.subtype(a.parameters.get(i), b.parameters.get(i)));
        }

        return new InferenceTree(
            "Sub-Base",
            l + " <: " + r);
      }

      throw new TypingException.SubtypingFailed(l, r);
    }

    if (l instanceof HMXType.NumericType a && r instanceof HMXType.NumericType b) {
      if (a.size <= b.size) {
        return new InferenceTree(
            "Sub-NumericType",
            l + " <: " + r);
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
      var trees = new ArrayList<InferenceTree>();
      for (int i = 0; i < a.elements.size(); i++) {
        trees.add(this.subtype(a.elements.get(i), b.elements.get(i)));
      }

      return new InferenceTree(
          "Sub-Tuple",
          l + " <: " + r,
          "",
          List.copyOf(trees));
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

    this.currentLevel += 1;
    constr.solve(this, env);
    this.currentLevel -= 1;

    if (constr instanceof OneShotConstraint oneShotSolved) {
      oneShotSolved.markSolved();
    }
  }

  public Scheme solveConstraintAndGeneralize(Constraint constr, Env env, HMXType type) {
    var oldSubtypeFailure = this.getAllowSubtypeFailure();
    this.allowSubtypeFailure = true;
    this.solveConstraint(constr, env);
    this.allowSubtypeFailure = oldSubtypeFailure;

    var finalType = type.deref();
    return finalType.generalize(this.currentLevel, constr);
  }
}

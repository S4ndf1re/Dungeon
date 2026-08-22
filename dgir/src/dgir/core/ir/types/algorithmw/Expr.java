package dgir.core.ir.types.algorithmw;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import dgir.core.ir.types.Expression;
import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.InferenceTree;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.compatibility.Scope;

public abstract class Expr extends ExprOrOperator<Expr, AlgorithmWType>
    implements Expression<Expr, AlgorithmWType> {

  private Optional<AlgorithmWType> inferredType;
  // private HashSet<AlgorithmWType> instances;

  protected Expr() {
    this.inferredType = Optional.empty();
    // this.instances = new HashSet<>();
  }

  protected Expr(Expr other) {
    this.inferredType = Optional.ofNullable(other.inferredType.orElse(null));
    // this.instances = new HashSet<>();
  }

  // Make sure, that exprs always equals via object reference (needed for in-set
  // storage!)
  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  @Override
  public void setInferredType(AlgorithmWType inferredType) {
    this.inferredType = Optional.ofNullable(inferredType);
  }

  public void setInferredType(Optional<AlgorithmWType> inferredType) {
    this.inferredType = Optional.ofNullable(inferredType.orElse(null));
  }

  @Override
  public Optional<AlgorithmWType> getInferredType() {
    return this.inferredType;
  }

  @Override
  public boolean isExpr() {
    return true;
  }

  @Override
  public boolean isOperator() {
    return false;
  }

  @Override
  public Expr getExpr() {
    return this;
  }

  public abstract Expr copy();

  /**
   * When an {@link Expr} is a variable that is just a reference to another
   * {@link Symbol} within the {@link Env},
   * this function is expected to return the {@link Symbol} to that reference.
   *
   * <p>
   * For an {@link Expr} like {@link ExprVar}, this is a trivial {@link Env}
   * lookup.
   * However, custom
   * {@link Expr}s may also provide this functionality in some way, and hence must
   * expose the potentially referenced {@link Symbol}.
   *
   * @return `Some(var)` if `var` is a variable bound by this expression
   */
  public Optional<Symbol<Expr, AlgorithmWType>> getReferencedVariable() {
    return Optional.empty();
  }

  public void setReferencedExpr(Expr expr) {
  }

  /**
   * Infer the type of the expression. This method MUST be implemented for every
   * {@link Expr}.
   * After inferring the type (the return value), the engine automatically stores
   * the inferred type within the inferred expression.
   *
   * @param engine
   * @param env
   * @return the inferred {@link AlgorithmWType} and the resulting {@link Subst},
   *         combined into an {@link InferenceTree}
   */
  public abstract InferResult infer(TypeInference engine, Env env);

  /**
   * Instantiate the correct type instance for code generation.
   * This instance is stored within the expression.
   * Normally, a default implementation is provided,
   * that just applies the solution {@link Subst} to to the previous inferred
   * type.
   * Some expressions, like {@link ExprAbs} or {@link ExprApp} need to implement a
   * different
   * version of `instantiateInner`.
   * Especially {@link ExprAbs} needs to collect all fully instantiated instances.
   *
   * <p>
   * The {@link InstEnv} will act as an {@link Env}, but does not store types but
   * rather
   * expressions.
   * Additionally, the {@link InstEnv} will store all visited expressions in
   * combination
   * with the {@link Subst}.
   * Hence further unified solutions will get applied to all Expressions in the
   * tree,
   * even though an expression was visited for a partial solution earlier.
   *
   * @param engine   the inference engine that provides useful helper methods,
   *                 like `unify` and `asExpression`
   * @param env      the instance env, collecting visited expressions and acting
   *                 as a scope-like Env
   * @param solution a partial of full solution that can be used to infer all
   *                 types and instantiations
   */
  protected void instantiateInner(TypeInference engine, InstEnv env, Subst solution) {
    this.setInferredType(this.getInferredType().map(infType -> solution.apply(infType)));

    if (this.getInferredType().isPresent()) {
      // TODO: Instantiate should happen automatically by copying the expression trees
      // during instantiation!
      // AlgorithmWType appliedType = solution.apply(this.getInferredType().get());
      // if (appliedType.isFullySpecified() && !this.instances.contains(appliedType))
      // {
      // this.instances.add(appliedType);
      // }
    }

    this.getChildren().forEach(child -> engine.asExpression(child).instantiate(engine, env, solution));
  }

  /**
   * Instantiate the full expression tree to find and store all instantiations.
   * Additionally, every expression and its inferred type (determined during type
   * inference)
   * is substituted, resulting in a fully typed Expression tree.
   *
   * @param engine   the type inference engine used to infer all types
   * @param env      a env storing all in scope expressions
   * @param solution a solution Subst that may be extended with further
   *                 instantioation Substs
   */
  public final Expr instantiate(TypeInference engine, InstEnv env, Subst solution) {
    if (env.isVisisted(Pair.of(this, solution))) {
      return env.getConsed(this);
    }

    Expr instantiatedTarget = this.copy();
    env.visit(Pair.of(instantiatedTarget, solution));

    instantiatedTarget.instantiateInner(engine, env, solution);
    instantiatedTarget = env.getConsed(instantiatedTarget);

    // In case a variable points to an expression found within the env, the
    // variables inferred type must be unified with the solution,
    // to not loose polymorphic instantiation information.
    // Additionally, to make IR code generation simpler, the fully typed and
    // instantiated expression that was referenced is returned as a hash consed
    // version.
    var referencedExpr = instantiatedTarget.getReferencedVariable();
    if (referencedExpr.isPresent()) {
      var referencedFromEnv = env.get(instantiatedTarget.getReferencedVariable().get());
      if (referencedFromEnv.isPresent()) {
        var referencedExprAsExpr = engine.asExpression(referencedFromEnv.get());
        var referencedInferredType = referencedExprAsExpr.getInferredType();

        if (referencedInferredType.isPresent() && instantiatedTarget.getInferredType().isPresent()) {
          UnifyResult res = engine.unify(instantiatedTarget.getInferredType().get(), referencedInferredType.get());
          var finalSubst = res.subst().compose(solution);
          Expr instantiatedReferenced = referencedExprAsExpr.instantiate(engine, env, finalSubst);
          // After instantiation of the referenced expression, set the insantiated
          // Expression instance into the variable as context for IR code gen.
          instantiatedTarget.setReferencedExpr(instantiatedReferenced);
          return instantiatedTarget;
        }
      }
    }

    return instantiatedTarget;
  }

  public static final class ExprAnn extends Expr {

    private final Expr expr;
    private final AlgorithmWType type;

    public ExprAnn(Expr expr, AlgorithmWType type) {
      this.expr = expr;
      this.type = type;
    }

    public ExprAnn(ExprAnn other) {
      super(other);
      this.expr = other.expr.copy();
      this.type = other.type;
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(this.expr);
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {

      InferResult res = engine.infer(expr, env);

      var unifyRes = engine.unify(res.type(), type);
      var subst = unifyRes.subst().compose(res.subst());

      return new InferResult(
          subst,
          type,
          new InferenceTree(
              "T-Ann",
              env + " |- " + this,
              type.toString(),
              List.of(res.tree())));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprAnn ann && this.expr.equals(ann.expr) && this.type.equals(ann.type);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.expr, this.type);
    }

    @Override
    public Expr copy() {
      return new ExprAnn(this);
    }
  }

  public static final class ExprLit extends Expr {

    private GeneralParameterizedNominalType value;

    public ExprLit(Literal value) {
      this.value = value.toParameterizedNominalType();
    }

    private ExprLit(ExprLit other) {
      super(other);
      this.value = other.value;
    }

    @Override
    public final String toString() {
      return value.toString();
    }

    @Override
    public List<Expr> getChildren() {
      return List.of();
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      var algoWType = engine.generalNominalTypeToInferenceType(value, null);
      return new InferResult(
          Subst.newEmpty(),
          algoWType.getLeft(),
          new InferenceTree(
              "T-" + algoWType,
              env + " |- " + this,
              algoWType.toString(),
              List.of()));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprLit lit && this.value.equals(lit.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.value);
    }

    @Override
    public Expr copy() {
      return new ExprLit(this);
    }
  }

  public static final class ExprTuple extends Expr {

    private List<Expr> elements;

    public ExprTuple(List<Expr> elements) {
      this.elements = elements;
    }

    public ExprTuple(ExprTuple other) {
      super(other);
      this.elements = other.elements.stream().map(Expr::copy).toList();
    }

    @Override
    public final String toString() {
      return ("(" +
          elements
              .stream()
              .map(Object::toString)
              .collect(Collectors.joining(", "))
          +
          ")");
    }

    @Override
    public List<Expr> getChildren() {
      return this.elements.stream().map(ExprOrOperator::getExpr).toList();
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;
      Subst subst = Subst.newEmpty();
      ArrayList<AlgorithmWType> types = new ArrayList<>();
      ArrayList<InferenceTree> trees = new ArrayList<>();
      Env currentEnv = env.copy();

      for (var expr : elements) {
        var res = engine.infer(expr, currentEnv);
        subst = res.subst().compose(subst);
        currentEnv = currentEnv.apply(res.subst());
        types.add(res.type());
        trees.add(res.tree());
      }

      var resultType = new AlgorithmWType.Tuple(List.copyOf(types));

      return new InferResult(
          subst,
          resultType,
          new InferenceTree(
              "T-Tuple",
              input,
              "" + resultType,
              List.copyOf(trees)));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprTuple other && this.elements.equals(other.elements);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.elements);
    }

    @Override
    public Expr copy() {
      return new ExprTuple(this);
    }
  }

  public static final class ExprVar extends Expr {

    private final Symbol<Expr, AlgorithmWType> name;
    private Optional<Expr> referencedExpr;

    public ExprVar(Symbol<Expr, AlgorithmWType> name) {
      this.name = name;
      this.referencedExpr = Optional.empty();
    }

    private ExprVar(ExprVar other) {
      super(other);
      this.name = other.name;
      this.referencedExpr = other.referencedExpr;
    }

    @Override
    public final String toString() {
      return name + "";
    }

    @Override
    public Optional<Symbol<Expr, AlgorithmWType>> getReferencedVariable() {
      return Optional.of(this.name);
    }

    @Override
    public void setReferencedExpr(Expr expr) {
      this.referencedExpr = Optional.ofNullable(expr);
    }

    @Override
    public List<Expr> getChildren() {
      return List.of();
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;

      Scheme scheme = env.get(name);
      if (scheme != null) {
        AlgorithmWType instantiated = scheme.instantiate(engine, this.name);
        return new InferResult(
            Subst.newEmpty(),
            instantiated,
            new InferenceTree(
                "T-Var",
                input,
                instantiated.toString(),
                List.of()));
      } else {
        throw new TypingException.UnknownVariable(name);
      }
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprVar other && this.name.equals(other.name);
    }

    @Override
    public int hashCode() {
      return this.name.hashCode();
    }

    @Override
    public Expr copy() {
      return new ExprVar(this);
    }
  }

  public static final class ExprApp extends Expr {

    private final Expr func;
    private final List<Expr> args;
    private Optional<AlgorithmWType> inferredFunctionType;

    public ExprApp(
        Expr func,
        Expr arg) {
      this.func = func;
      this.args = List.of(arg);
    }

    public ExprApp(
        Expr func,
        List<Expr> args) {
      this.func = func;
      this.args = List.copyOf(args);
    }

    public ExprApp(
        ExprApp other) {
      super(other);
      this.func = other.func.copy();
      this.args = other.args.stream().map(Expr::copy).toList();
    }

    @Override
    public final String toString() {
      if (args.size() > 1) {
        return func + " (" + args.stream().map(Object::toString).collect(Collectors.joining(",")) + ")";
      } else if (!args.isEmpty()) {
        return func + " " + args.get(0);
      } else {
        return func + " ()";
      }
    }

    @Override
    public List<Expr> getChildren() {
      var list = new ArrayList<Expr>();
      list.add(this.func.getExpr());
      this.args.forEach(arg -> list.add(arg.getExpr()));
      return List.copyOf(list);
    }

    @Override
    protected void instantiateInner(TypeInference engine, InstEnv env, Subst solution) {
      this.inferredFunctionType = this.inferredFunctionType.map(fnTy -> solution.apply(fnTy));

      var funcExpr = engine.asExpression(this.func);
      if (this.inferredFunctionType.isPresent() && funcExpr.getInferredType().isPresent()) {
        UnifyResult res = engine.unify(this.inferredFunctionType.get(), funcExpr.getInferredType().get());
        Subst extendedSolution = res.subst().compose(solution);
        super.instantiateInner(engine, env, extendedSolution);
      } else {
        super.instantiateInner(engine, env, solution);
      }

    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;

      AlgorithmWType resultType = new AlgorithmWType.Var(new TypeVar());

      // TODO: when the function is inferred to be a symbol lookup
      // (it always is in IR context),
      // the function type that may be polymorphic is instantiated.
      // This instantiation must somehow be reflected
      // within the final Expression tree.
      // Somehow a all function calls with their call expressions must be tracked to
      // generate the correctly typed operations after the
      // type inference and checking.
      InferResult funcInferRes = engine.infer(func, env);

      Env envSubst = env.apply(funcInferRes.subst());

      Subst finalSubst = funcInferRes.subst();
      AlgorithmWType builtArrowType = resultType;
      ArrayList<InferenceTree> trees = new ArrayList<>();
      trees.add(funcInferRes.tree());

      for (var arg : this.args.reversed()) {
        InferResult argRes = engine.infer(arg, envSubst);
        finalSubst = argRes.subst().compose(finalSubst);
        envSubst = envSubst.apply(finalSubst);
        builtArrowType = new AlgorithmWType.Arrow(argRes.type(), builtArrowType);
        trees.add(argRes.tree());
      }

      // NOTE: in case the APP parameters are empty, i.e. a function without
      // parameters is configured, the APP behaviour and the ABS behaviour are
      // identical and treat the function type as a Unit -> t0. This means, that the
      // final
      // type is an arrow type that accepts a unit value as a parameter.
      if (builtArrowType == resultType) {
        builtArrowType = new AlgorithmWType.Arrow(new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_UNIT),
            builtArrowType);
      }

      AlgorithmWType funcTypeSubst = finalSubst.apply(funcInferRes.type());
      this.inferredFunctionType = Optional.of(funcTypeSubst);
      UnifyResult unifyRes = engine.unify(funcTypeSubst, builtArrowType);

      finalSubst = unifyRes.subst().compose(finalSubst);
      // NOTE: subst the resultType not the builtArrowType, as the the arrow type is
      // only needed for unification to build the final subst
      resultType = finalSubst.apply(resultType);

      trees.add(unifyRes.tree());

      return new InferResult(
          finalSubst,
          resultType,
          new InferenceTree(
              "T-App",
              input,
              "" + builtArrowType,
              List.copyOf(trees)));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprApp other && this.func.equals(other.func) && this.args.equals(other.args);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.func, this.args);
    }

    @Override
    public Expr copy() {
      return new ExprApp(this);
    }
  }

  public static final class ExprAbs extends Expr {

    private final List<Symbol<Expr, AlgorithmWType>> params;
    private final Expr body;

    public ExprAbs(Symbol<Expr, AlgorithmWType> param, Expr body) {
      this.params = List.of(param);
      this.body = body;
    }

    public ExprAbs(List<Symbol<Expr, AlgorithmWType>> params, Expr body) {
      this.params = List.copyOf(params);
      this.body = body;
    }

    public ExprAbs(ExprAbs other) {
      super(other);
      this.params = List.copyOf(other.params);
      this.body = other.body.copy();
    }

    @Override
    public final String toString() {
      if (params.size() > 1) {
        return "λ(" + params.stream().map(Object::toString).collect(Collectors.joining(",")) + ")." + body + "";
      } else if (!params.isEmpty()) {
        return "λ" + params.get(0) + "." + body + "";
      } else {
        return "λ()" + "." + body + "";
      }
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(this.body.getExpr());
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;

      Env newEnv = env.copy();
      ArrayList<Pair<Symbol<Expr, AlgorithmWType>, TypeVar>> paramsAndTypeVars = new ArrayList<>();
      for (var param : this.params) {
        var typeVar = new TypeVar();
        AlgorithmWType freshTypeVar = new AlgorithmWType.Var(typeVar);
        Scheme newScheme = new Scheme(List.of(), freshTypeVar);
        newEnv.put(param, newScheme);
        paramsAndTypeVars.add(Pair.of(param, typeVar));
      }

      Scope<AlgorithmWType> functionScope = newEnv.addScope();
      AlgorithmWType retTypeVar = new AlgorithmWType.Var(new TypeVar());
      InferResult res = engine.infer(body, newEnv);
      Subst subst = res.subst();
      ArrayList<InferenceTree> trees = new ArrayList<>();

      // Unify all return values. If return values do not have the same type, error
      // will get thrown here!
      for (var retType : functionScope.getAllReturnTypesInScope()) {
        var unifyRes = engine.unify(retTypeVar, subst.apply(retType));
        subst = unifyRes.subst().compose(subst);
        retTypeVar = subst.apply(retTypeVar);
        trees.add(unifyRes.tree());
      }

      // In terms of IR, the body will not have a direct return parameter. Though it
      // can be assumed,
      // that the last expression is always a return in a function, even if nothing is
      // returned.
      // Hence, It would make sense to treat the last return expression as an
      // expression that actually returns a value of type T.
      // This must then be unified with the retTypeVar collected from all return
      // statements (if present)
      subst = res.subst().compose(subst);
      retTypeVar = subst.apply(retTypeVar);
      UnifyResult retTypeUnify = engine.unify(res.type(), retTypeVar);
      subst = retTypeUnify.subst().compose(subst);

      AlgorithmWType appliedRetType = subst.apply(retTypeVar);
      AlgorithmWType resultType = appliedRetType;
      for (var paramAndTypeVar : paramsAndTypeVars.reversed()) {
        resultType = new AlgorithmWType.Arrow(subst.apply(new AlgorithmWType.Var(paramAndTypeVar.getRight())),
            resultType);
      }

      // NOTE: in case the ABS parameters are empty, i.e. a function without
      // parameters is configured, the APP behaviour and the ABS behaviour are
      // identical and treat the function type as a Unit -> t0. This means, that the
      // final
      // type is an arrow type that accepts a unit value as a parameter.
      if (resultType == appliedRetType) {
        resultType = new AlgorithmWType.Arrow(new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_UNIT), resultType);
      }

      return new InferResult(
          subst,
          resultType,
          new InferenceTree(
              "T-Abs",
              input,
              resultType.toString(),
              List.of(res.tree())));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprAbs other && this.params.equals(other.params) && this.body.equals(other.body);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.params, this.body);
    }

    @Override
    public Expr copy() {
      return new ExprAbs(this);
    }
  }

  /**
   * ExprLet is a multi let statement that infers multiple let expressions at the
   * same time.
   *
   * @implNote The lets are considered global scope: i.e. the let values are first
   *           assigned to a new {@link TypeVar}, that is then unified with the
   *           inferred assigned type.
   */
  public static final class ExprLet extends Expr {

    private final List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings;
    private final Expr body;

    public ExprLet(Symbol<Expr, AlgorithmWType> param, Expr value,
        Expr body) {
      this.bindings = List.of(Pair.of(param, value));
      this.body = body;
    }

    public ExprLet(List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings,
        Expr body) {
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public ExprLet(ExprLet other) {
      super(other);
      this.bindings = other.bindings.stream().map(binding -> Pair.of(binding.getLeft(), binding.getRight().copy()))
          .toList();
      this.body = other.body.copy();
    }

    @Override
    public final String toString() {
      return "let (" + this.bindings.stream().map(Object::toString).collect(Collectors.joining(", ")) + ") in "
          + body;
    }

    @Override
    protected void instantiateInner(TypeInference engine, InstEnv env, Subst solution) {
      var newEnv = new InstEnv(env);
      for (var bnd : this.bindings) {
        newEnv.put(bnd.getLeft(), bnd.getRight());
      }
      super.instantiateInner(engine, newEnv, solution);
    }

    @Override
    public List<Expr> getChildren() {
      var list = new ArrayList<Expr>();
      this.bindings.forEach(bnd -> list.add(bnd.getRight().getExpr()));
      list.add(this.body.getExpr());
      return List.copyOf(list);
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;

      Env newEnv = env.copy();
      ArrayList<Triple<Symbol<Expr, AlgorithmWType>, AlgorithmWType, Expr>> notUnified = new ArrayList<>(
          this.bindings.size());
      for (var binding : this.bindings) {
        var typeVar = new TypeVar();
        notUnified.add(Triple.of(binding.getLeft(), new AlgorithmWType.Var(typeVar), binding.getRight()));
        newEnv.put(binding.getLeft(), new AlgorithmWType.Var(typeVar).generalize(newEnv, Optional.empty()));
      }

      Subst subst = Subst.newEmpty();
      ArrayList<InferenceTree> trees = new ArrayList<>();

      for (var binding : notUnified) {
        var param = binding.getLeft();
        var typeVar = binding.getMiddle();
        var value = binding.getRight();

        InferResult res1 = engine.infer(value, newEnv);
        subst = res1.subst().compose(subst);
        Env envSubst = newEnv.apply(subst);

        UnifyResult unifyRes = engine.unify(typeVar, res1.type());
        subst = unifyRes.subst().compose(subst);
        envSubst = envSubst.apply(subst);

        Scheme generalizedType = subst.apply(res1.type()).generalize(envSubst, Optional.of(value));

        newEnv = envSubst.copy();
        newEnv.put(param, generalizedType);
        trees.add(res1.tree());
      }

      InferResult res2 = engine.infer(body, newEnv);

      Subst finalSubst = res2.subst().compose(subst);
      trees.add(res2.tree());

      return new InferResult(
          finalSubst,
          res2.type(),
          new InferenceTree(
              "T-Let*",
              input,
              "" + res2.type(),
              List.copyOf(trees)));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprLet other && this.bindings.equals(other.bindings) && this.body.equals(other.body);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.bindings, this.body);
    }

    @Override
    public Expr copy() {
      return new ExprLet(this);
    }
  }

  public static class ExprReturn extends Expr {
    private Expr value;

    public ExprReturn(Expr value) {
      this.value = value;
    }

    public ExprReturn(ExprReturn other) {
      super(other);
      this.value = other.value.copy();
    }

    @Override
    public String toString() {
      return "return " + this.value;
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(this.value.getExpr());
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;

      InferResult inferred = engine.infer(this.value, env);
      var topScope = env.topScope();

      var inferredType = inferred.subst().apply(inferred.type());
      if (topScope.isPresent()) {
        topScope.get().addReturnType(inferredType);
      }

      return new InferResult(inferred.subst(), inferredType,
          new InferenceTree("T-Return", input, "" + inferredType, List.of(inferred.tree())));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprReturn other && this.value.equals(other.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.value);
    }

    @Override
    public Expr copy() {
      return new ExprReturn(this.value);
    }
  }

  public static class ExprCustom<D> extends Expr {
    public static final record InferFunctionResult(
        Subst subst,
        AlgorithmWType type) {
    }

    @FunctionalInterface
    public interface InferFunction<D> {
      InferFunctionResult infer(TypeInference engine, Env env, D data);
    }

    @FunctionalInterface
    public interface InstantiateFunction<D> {
      void instantiate(TypeInference engine, InstEnv env, Subst solution, D data);
    }

    @FunctionalInterface
    public interface GetChildrenFunction<D> {
      List<Expr> getChildren(D data);
    }

    private D data;
    private InferFunction<D> inferFn;
    private Optional<InstantiateFunction<D>> instFn;
    private Optional<GetChildrenFunction<D>> getChildrenFn;

    public ExprCustom(
        D data, InferFunction<D> inferFn) {
      this(data, inferFn, null, null);
    }

    public ExprCustom(
        D data, InferFunction<D> inferFn, InstantiateFunction<D> instFn) {
      this(data, inferFn, instFn, null);
    }

    public ExprCustom(
        D data, InferFunction<D> inferFn, InstantiateFunction<D> instFn, GetChildrenFunction<D> getChildrenFn) {
      this.data = data;
      this.inferFn = inferFn;
      this.instFn = Optional.ofNullable(instFn);
      this.getChildrenFn = Optional.ofNullable(getChildrenFn);
    }

    public ExprCustom(ExprCustom<D> other) {
      super(other);
      this.data = other.data;
      this.inferFn = other.inferFn;
      this.instFn = other.instFn;
      this.getChildrenFn = other.getChildrenFn;
    }

    @Override
    public String toString() {
      return "custom";
    }

    @Override
    public List<Expr> getChildren() {
      if (this.getChildrenFn.isPresent()) {
        return this.getChildrenFn.get().getChildren(this.data).stream().map(ExprOrOperator::getExpr).toList();
      } else {
        return List.of();
      }
    }

    protected void instantiateInner(TypeInference engine, InstEnv env, Subst solution) {
      // TODO(jan): this contains logic bugs and there is no way to specify at what
      // point to call the super method! Additionally, no solution changes can be
      // forwarded
      super.instantiateInner(engine, env, solution);
      if (this.instFn.isPresent()) {
        this.instFn.get().instantiate(engine, env, solution, this.data);
      }
    };

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;
      var infRes = this.inferFn.infer(engine, env, data);
      return new InferResult(infRes.subst, infRes.type,
          new InferenceTree("T-Cust", input, "" + infRes.type, List.of()));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprCustom<?> other &&
          this.data.equals(other.data) &&
          this.inferFn.equals(other.inferFn) &&
          this.instFn.equals(other.instFn) &&
          this.getChildrenFn.equals(other.getChildrenFn);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.data, this.inferFn, this.instFn, this.getChildrenFn);
    }

    @Override
    public Expr copy() {
      return new ExprCustom<>(this);
    }

  }
}

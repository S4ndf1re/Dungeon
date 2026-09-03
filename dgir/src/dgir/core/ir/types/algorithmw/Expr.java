package dgir.core.ir.types.algorithmw;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.Value;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.InferenceTree;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.compatibility.Scope;
import dgir.core.ir.types.traits.IIsAbstraction;
import dgir.core.ir.types.traits.IIsApplication;

public abstract class Expr extends ExprOrOperator<Expr, AlgorithmWType>
    implements Expression<Expr, AlgorithmWType> {

  private Optional<Expr> parentScopeExpression;
  private Optional<Integer> parentScopePosition;
  private Optional<AlgorithmWType> inferredType;
  private Optional<Operation> underlyingOperation;
  private Optional<InstantiateOperation<Expr, AlgorithmWType>> instOp;

  protected Expr() {
    this.parentScopeExpression = Optional.empty();
    this.parentScopePosition = Optional.empty();
    this.inferredType = Optional.empty();
    this.underlyingOperation = Optional.empty();
    this.instOp = Optional.empty();
  }

  protected Expr(Expr other) {
    this.parentScopeExpression = Optional.ofNullable(other.parentScopeExpression.orElse(null));
    this.parentScopePosition = Optional.ofNullable(other.parentScopePosition.orElse(null));
    this.inferredType = Optional.ofNullable(other.inferredType.orElse(null));
    this.underlyingOperation = Optional.ofNullable(other.underlyingOperation.orElse(null));
    this.instOp = Optional.ofNullable(other.instOp.orElse(null));
  }

  // Make sure, that exprs always equals via object reference (needed for in-set
  // storage!)
  @Override
  public boolean equals(Object obj) {
    return obj instanceof Expr expr && this.inferredType.equals(expr.inferredType)
        && this.parentScopeExpression.orElse(null) == expr.parentScopeExpression.orElse(null);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.inferredType,
        this.parentScopeExpression.isPresent() ? System.identityHashCode(this.parentScopeExpression.get()) : 0);
  }

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

  @Override
  public Optional<Symbol<Expr, AlgorithmWType>> getReferencedVariable() {
    return Optional.empty();
  }

  @Override
  public void setUnderlyingOperation(Operation op) {
    this.underlyingOperation = Optional.of(op);
  }

  @Override
  public Optional<Operation> getUnderlyingOperation() {
    return Optional.ofNullable(this.underlyingOperation.orElse(null));
  }

  @Override
  public void setInstantiateOperationCallback(InstantiateOperation<Expr, AlgorithmWType> callback) {
    this.instOp = Optional.ofNullable(callback);
  }

  @Override
  public Optional<InstantiateOperation<Expr, AlgorithmWType>> getInstantiateOperationCallback() {
    return Optional.ofNullable(this.instOp.orElse(null));
  }

  public void setParentScopeExpression(Expr expr, int position) {
    this.parentScopeExpression = Optional.ofNullable(expr);
    this.parentScopePosition = Optional.of(position);
  }

  @Override
  public Optional<Expr> getParentScopeExpr() {
    return Optional.ofNullable(this.parentScopeExpression.orElse(null));
  }

  @Override
  public Optional<Integer> getParentScopePosition() {
    return Optional.ofNullable(this.parentScopePosition.orElse(null));
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
  protected abstract Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env,
      Subst solution);

  /**
   * Instantiate the full expression tree to find and store all instantiations.
   * Additionally, every expression and its inferred type (determined during type
   * inference)
   * is substituted, resulting in a fully typed Expression tree.
   *
   * <p>
   * In addition to the instantiation, a simple form of variable resolution is
   * performed, by beta-reducing variables into the concrete expressions
   * referenced by the ExprVars. This is important for later stage code generation
   *
   * @param engine   the type inference engine used to infer all types
   * @param env      a env storing all in scope expressions
   * @param solution a solution Subst that may be extended with further
   *                 instantioation Substs
   */
  public final Expr instantiate(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
    Expr expr = env.getConsed(this);
    // As variables may get visited more than once, even though they are equal, the
    // referencing logic must run non the less
    if (expr.getReferencedVariable().isEmpty() && env.isVisisted(Pair.of(expr, solution))) {
      return env.getConsed(expr);
    }

    env.visit(Pair.of(expr, solution));

    Expr instantiated = expr.instantiateInner(engine, env, solution);
    instantiated.setInferredType(instantiated.getInferredType().map(ty -> solution.apply(ty)));
    var instantiatedTarget = env.getConsed(instantiated);

    // The beta-reduction for variables.
    // When the variable is in scope, actually replace the returned
    // expression with the referenced instantiated Expr instance.
    // This will not work for abstract ExprAbs parameters,
    // as those are not bound to concrete expressions.
    // Sometimes, function application arguments are
    // further applied using beta reduction,
    // to result in a more normalized instantiation tree.
    // This will not be possible due to the nature of the
    // Operation conversion that will run later.
    //
    // FUTURE_WORK(jan): return a fully beta-reduced expression tree
    var referencedExpr = instantiatedTarget.getReferencedVariable();
    if (referencedExpr.isPresent()) {
      var referencedFromEnv = env.getExprAndPosition(instantiatedTarget.getReferencedVariable().get());
      if (referencedFromEnv.isPresent()) {
        var scopeExpression = env.getScopeExpression(instantiatedTarget.getReferencedVariable().get());

        var referencedExprAsExpr = engine.asExpression(referencedFromEnv.get().getLeft());
        var referencedInferredType = referencedExprAsExpr.getInferredType();

        if (referencedInferredType.isPresent() && instantiatedTarget.getInferredType().isPresent()) {
          UnifyResult res = engine.unify(instantiatedTarget.getInferredType().get(), referencedInferredType.get());
          var finalSubst = res.subst().compose(solution);

          // SAFETY: Setting the scope here is safe, as this expression will get
          // replaced every time in the final expression tree!
          // Optionally, unsetting the parentScope Expression could prevent bugs, but as
          // all further operations act on deep copies, this operation is ok!
          referencedExprAsExpr.setParentScopeExpression(scopeExpression.get(), referencedFromEnv.get().getRight());

          Expr instantiatedReferenced = referencedExprAsExpr.instantiate(engine, env, finalSubst);
          // After instantiation, return the actual expression not the variable!
          // NOTE: the instantiatedReferenced is already hash-consed
          return instantiatedReferenced;
        }
      }
    }

    return instantiatedTarget;
  }

  public static final class ExprAnn extends Expr {

    public final Expr expr;
    public final AlgorithmWType type;

    public ExprAnn(Expr expr, AlgorithmWType type) {
      this.expr = expr;
      this.type = type;
    }

    public ExprAnn(ExprAnn other) {
      super(other);
      this.expr = other.expr;
      this.type = other.type;
    }

    public ExprAnn(ExprAnn other, Expr expr, AlgorithmWType type) {
      super(other);
      this.expr = expr;
      this.type = type;
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
      return obj instanceof ExprAnn ann && this.expr.equals(ann.expr) && this.type.equals(ann.type)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.expr, this.type, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      return new ExprAnn(this, this.expr.replaceSymbol(original, replacement), this.type);
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.expr.containsSymbol(symbol);
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      // Simply return the inner as fully instantiated!
      return this.expr.instantiate(engine, env, solution);
    }
  }

  public static final class ExprLit extends Expr {

    public Literal value;

    public ExprLit(Literal value) {
      this.value = value;
    }

    @SuppressWarnings("unused")
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
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return false;
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      var algoWType = engine.generalNominalTypeToInferenceType(value.toParameterizedNominalType(), null);
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
      return obj instanceof ExprLit lit && this.value.equals(lit.value) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.value, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      return this;
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      return this;
    }
  }

  public static final class ExprTuple extends Expr {

    public final List<Expr> elements;

    public ExprTuple(List<Expr> elements) {
      this.elements = elements;
    }

    public ExprTuple(Expr... elements) {
      ArrayList<Expr> elems = new ArrayList<>();
      for (var elem : elements) {
        elems.add(elem);
      }

      this.elements = List.copyOf(elems);
    }

    public ExprTuple(ExprTuple other) {
      super(other);
      this.elements = List.copyOf(other.elements);
    }

    public ExprTuple(ExprTuple other, List<Expr> elements) {
      super(other);
      this.elements = List.copyOf(elements);
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
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.elements.stream().anyMatch(elem -> elem.containsSymbol(symbol));
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
      return obj instanceof ExprTuple other && this.elements.equals(other.elements) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.elements, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      return new ExprTuple(this,
          this.elements.stream().map(elem -> elem.replaceSymbol(original, replacement)).toList());
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      return new ExprTuple(this, this.elements.stream().map(elem -> elem.instantiate(engine, env, solution)).toList());
    }
  }

  public static final class ExprVar extends Expr {

    public final Symbol<Expr, AlgorithmWType> name;

    public ExprVar(Symbol<Expr, AlgorithmWType> name) {
      this.name = name;
    }

    @SuppressWarnings("unused")
    private ExprVar(ExprVar other) {
      super(other);
      this.name = other.name;
    }

    private ExprVar(ExprVar other, Symbol<Expr, AlgorithmWType> name) {
      super(other);
      this.name = name;
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
    public List<Expr> getChildren() {
      return List.of();
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.name.equals(symbol);
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
      return obj instanceof ExprVar other && this.name.equals(other.name) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.name, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      if (this.name.equals(original)) {
        return new ExprVar(this, replacement);
      }
      return this;
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      // Nothing to instantiate;
      return this;
    }
  }

  public static final class ExprApp extends Expr implements IIsApplication<Expr, AlgorithmWType> {

    public final Expr func;
    public final List<Expr> args;
    private Optional<AlgorithmWType> inferredFunctionType;

    public ExprApp(
        Expr func,
        Expr arg) {
      this.func = func;
      this.args = List.of(arg);
      this.inferredFunctionType = Optional.empty();
    }

    public ExprApp(
        Expr func,
        List<Expr> args) {
      this.func = func;
      this.args = List.copyOf(args);
      this.inferredFunctionType = Optional.empty();
    }

    public ExprApp(
        ExprApp other) {
      super(other);
      this.func = other.func;
      this.args = List.copyOf(other.args);
      this.inferredFunctionType = other.inferredFunctionType;
    }

    public ExprApp(ExprApp other, Expr func,
        List<Expr> args) {
      super(other);
      this.func = func;
      this.args = List.copyOf(args);
      this.inferredFunctionType = other.inferredFunctionType;
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
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.func.containsSymbol(symbol) || this.args.stream().anyMatch(arg -> arg.containsSymbol(symbol));
    }

    @Override
    public List<Expr> getApplications() {
      return List.copyOf(this.args);
    }

    @Override
    public Expr getFunction() {
      return this.func;
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      this.inferredFunctionType = this.inferredFunctionType.map(fnTy -> solution.apply(fnTy));

      var funcExpr = engine.asExpression(this.func);
      if (this.inferredFunctionType.isPresent() && funcExpr.getInferredType().isPresent()) {
        UnifyResult res = engine.unify(this.inferredFunctionType.get(), funcExpr.getInferredType().get());
        Subst extendedSolution = res.subst().compose(solution);
        return new ExprApp(this, this.func.instantiate(engine, env, extendedSolution),
            this.args.stream().map(arg -> arg.instantiate(engine, env, solution)).toList());
      } else {
        return new ExprApp(this, this.func.instantiate(engine, env, solution),
            this.args.stream().map(arg -> arg.instantiate(engine, env, solution)).toList());
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
      return obj instanceof ExprApp other && this.func.equals(other.func) && this.args.equals(other.args)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.func, this.args, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      var newFunc = this.func.replaceSymbol(original, replacement);
      var newArgs = this.args.stream().map(arg -> arg.replaceSymbol(original, replacement)).toList();

      var newApp = new ExprApp(this, newFunc, newArgs);
      return newApp;
    }

    public Optional<AlgorithmWType> getInferredFunctionType() {
      return this.inferredFunctionType;
    }
  }

  public static final class ExprAbs extends Expr implements IIsAbstraction<Expr, AlgorithmWType> {

    public List<Symbol<Expr, AlgorithmWType>> params;
    public Expr body;

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
      this.body = other.body;
    }

    public ExprAbs(ExprAbs other, List<Symbol<Expr, AlgorithmWType>> params, Expr body) {
      super(other);
      this.params = List.copyOf(params);
      this.body = body;
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
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.params.stream().anyMatch(param -> param.equals(symbol)) || this.body.containsSymbol(symbol);
    }

    @Override
    public List<Symbol<Expr, AlgorithmWType>> getAbstractionsOverSymbols() {
      return List.copyOf(this.params);
    }

    @Override
    public Expr getAbstractionBody() {
      return this.body;
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
      return obj instanceof ExprAbs other && this.params.equals(other.params) && this.body.equals(other.body)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.params, this.body, super.hashCode());
    }

    @Override
    public void reinstantiateSymbols() {
      var inferredType = this.getInferredType();
      assert inferredType.isPresent() : "can only reinstante expression values if the expression is well typed!";

      // In cases where the type is not fully specified, the instantiation actually
      // failed!
      if (!inferredType.get().isFullySpecified()) {
        return;
      }

      ArrayList<Symbol<Expr, AlgorithmWType>> newParams = new ArrayList<>(this.params.size());
      var currentParamType = inferredType.get();
      for (int i = 0; i < this.params.size(); i++) {
        assert currentParamType instanceof AlgorithmWType.Arrow;
        var arrowType = (AlgorithmWType.Arrow) currentParamType;

        assert arrowType.from instanceof AlgorithmWType.LitType : "expected fully type literal, received " + arrowType;
        var litType = arrowType.from;

        var nominalType = litType.asTypeParameter().getConcrete();
        var irType = Type.fromGeneralParameterizedNominalType(nominalType);
        var debugInfo = params.get(i).getValue().getDebugInfo();

        newParams.add(Symbol.of(new Value(irType, debugInfo)));
      }

      var oldParams = List.copyOf(this.params);
      this.params = newParams;
      var bodyExpr = this.body;

      for (int i = 0; i < newParams.size(); i++) {
        bodyExpr = bodyExpr.replaceSymbol(newParams.get(i), oldParams.get(i));
      }

      this.body = bodyExpr;
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      // If the symbol is shadowed by one of the bound values, don't replace in the
      // body!
      if (this.params.stream().anyMatch(param -> param.equals(original))) {
        return this;
      }

      // Only replace in the body, as the functions parameters are always the same
      // value!
      return new ExprAbs(this, this.params, body.replaceSymbol(original, replacement));
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      return new ExprAbs(this, List.copyOf(this.params), this.body.instantiate(engine, env, solution));
    }
  }

  /**
   * ExprLetSeq is a multi let statement that infers multiple let
   * expressions at the
   * same time.
   *
   * <p>
   * This is mainly considered to be useful with sequential definitions, i.e.
   * normal blocks, and thus
   * should be expected to be only used with sequential block operations that may
   * even allow shadowing!
   *
   * <p>
   * As all expresions are considered local and are only accessible by following
   * expressions, to use this with global expressions, for example for recursion,
   * ExprLetRec is required!
   */
  public static final class ExprLetSeq extends Expr {

    private List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings;
    private Expr body;

    public ExprLetSeq(Symbol<Expr, AlgorithmWType> param, Expr value,
        Expr body) {
      this.bindings = List.of(Pair.of(param, value));
      this.body = body;
    }

    public ExprLetSeq(List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings,
        Expr body) {
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public ExprLetSeq(ExprLetSeq other) {
      super(other);
      this.bindings = List.copyOf(other.bindings);
      this.body = other.body;
    }

    public ExprLetSeq(ExprLetSeq other, List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings,
        Expr body) {
      super(other);
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings() {
      return List.copyOf(this.bindings);
    }

    public Expr body() {
      return this.body;
    }

    @Override
    public final String toString() {
      return "let (" + this.bindings.stream().map(Object::toString).collect(Collectors.joining(", ")) + ") in "
          + body;
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      var newLetExpr = new ExprLetSeq(this,
          List.copyOf(this.bindings),
          new Expr.ExprLit(new Literal.Unit()));

      // This line is key, as the defining scope expression, in this case `newLetExpr`
      // is bound to the scope
      var newEnv = new InstEnv<Expr, AlgorithmWType, Subst>(env, newLetExpr);
      for (int i = 0; i < this.bindings.size(); i++) {
        var bnd = this.bindings.get(i);
        newEnv.put(bnd.getLeft(), bnd.getRight(), i);
      }

      newLetExpr.body = this.body.instantiate(engine, newEnv, solution);

      return newLetExpr;
    }

    @Override
    public List<Expr> getChildren() {
      var list = new ArrayList<Expr>();
      this.bindings.forEach(bnd -> list.add(bnd.getRight().getExpr()));
      list.add(this.body.getExpr());
      return List.copyOf(list);
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.bindings.stream().anyMatch(bnd -> bnd.getLeft().equals(symbol)) || this.body.containsSymbol(symbol);
    }

    @Override
    public List<Expr> getInstantiableChildren() {
      return List.of(this.body);
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;

      Env newEnv = env.copy();

      Subst subst = Subst.newEmpty();
      ArrayList<InferenceTree> trees = new ArrayList<>();

      for (var binding : this.bindings) {
        var param = binding.getLeft();
        var value = binding.getRight();

        InferResult res1 = engine.infer(value, newEnv);
        subst = res1.subst().compose(subst);
        Env envSubst = newEnv.apply(subst);

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
      return obj instanceof ExprLetRec other && this.bindings.equals(other.bindings)
          && this.body.equals(other.body)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.bindings, this.body, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      // If symbol is shadowed by the env created by this let binding, do not
      // overwrite the value!
      if (this.bindings.stream().anyMatch(bnd -> bnd.getLeft().equals(original))) {
        return this;
      }

      var newBody = this.body.replaceSymbol(original, replacement);
      var newBindings = this.bindings.stream()
          .map(binding -> Pair.of(binding.getLeft(), binding.getRight().replaceSymbol(original, replacement))).toList();
      return new ExprLetSeq(this, newBindings, newBody);
    }
  }

  /**
   * ExprLetRec is a multi let statement that infers multiple let
   * expressions at the
   * same time.
   *
   * <p>
   * This is mainly considered to be useful with function definitions, and thus
   * should be expected to be only used with fn defs
   *
   * <p>
   * As all expresions are considered global, to use this with ordered expressions
   * and their values, and allowing shadowing, LetExprSeq are required!
   *
   * @implNote The lets are considered global scope: i.e. the let values are first
   *           assigned to a new TypeVar, that is then unified with the
   *           inferred assigned type.
   *
   *
   */
  public static final class ExprLetRec extends Expr {

    private List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings;
    private Expr body;

    public ExprLetRec(Symbol<Expr, AlgorithmWType> param, Expr value,
        Expr body) {
      this.bindings = List.of(Pair.of(param, value));
      this.body = body;
    }

    public ExprLetRec(List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings,
        Expr body) {
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public ExprLetRec(ExprLetRec other) {
      super(other);
      this.bindings = List.copyOf(other.bindings);
      this.body = other.body;
    }

    public ExprLetRec(ExprLetRec other, List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings,
        Expr body) {
      super(other);
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings() {
      return List.copyOf(this.bindings);
    }

    public Expr body() {
      return this.body;
    }

    @Override
    public final String toString() {
      return "let (" + this.bindings.stream().map(Object::toString).collect(Collectors.joining(", ")) + ") in "
          + body;
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      var newLetExpr = new ExprLetRec(this,
          List.copyOf(this.bindings),
          new Expr.ExprLit(new Literal.Unit()));

      // This line is key, as the defining scope expression, in this case `newLetExpr`
      // is bound to the scope
      var newEnv = new InstEnv<Expr, AlgorithmWType, Subst>(env, newLetExpr);
      for (int i = 0; i < this.bindings.size(); i++) {
        var bnd = this.bindings.get(i);
        newEnv.put(bnd.getLeft(), bnd.getRight(), i);
      }

      newLetExpr.body = this.body.instantiate(engine, newEnv, solution);

      return newLetExpr;
    }

    @Override
    public List<Expr> getChildren() {
      var list = new ArrayList<Expr>();
      this.bindings.forEach(bnd -> list.add(bnd.getRight().getExpr()));
      list.add(this.body.getExpr());
      return List.copyOf(list);
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.bindings.stream().anyMatch(bnd -> bnd.getLeft().equals(symbol)) || this.body.containsSymbol(symbol);
    }

    @Override
    public List<Expr> getInstantiableChildren() {
      return List.of(this.body);
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
      return obj instanceof ExprLetRec other && this.bindings.equals(other.bindings)
          && this.body.equals(other.body)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.bindings, this.body, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      // If symbol is shadowed by the env created by this let binding, do not
      // overwrite the value!
      if (this.bindings.stream().anyMatch(bnd -> bnd.getLeft().equals(original))) {
        return this;
      }

      var newBody = this.body.replaceSymbol(original, replacement);
      var newBindings = this.bindings.stream()
          .map(binding -> Pair.of(binding.getLeft(), binding.getRight().replaceSymbol(original, replacement))).toList();
      return new ExprLetRec(this, newBindings, newBody);
    }
  }

  public static class ExprReturn extends Expr {
    public Expr value;

    public ExprReturn(Expr value) {
      this.value = value;
    }

    public ExprReturn(ExprReturn other) {
      super(other);
      this.value = other.value;
    }

    public ExprReturn(ExprReturn other, Expr value) {
      super(other);
      this.value = value;
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
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.value.containsSymbol(symbol);
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
      return obj instanceof ExprReturn other && this.value.equals(other.value) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.value, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      return new ExprReturn(this, this.value.replaceSymbol(original, replacement));
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      return new ExprReturn(this, this.value.instantiate(engine, env, solution));
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
      Expr instantiate(ExprCustom<D> oldExpr, TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env,
          Subst solution, D data);
    }

    @FunctionalInterface
    public interface GetChildrenFunction<D> {
      List<Expr> getChildren(D data);
    }

    @FunctionalInterface
    public interface ReplaceSymbolFunction<D> {
      Expr replaceSymbol(ExprCustom<D> oldExpr, Symbol<Expr, AlgorithmWType> original,
          Symbol<Expr, AlgorithmWType> replacement, D data);
    }

    private D data;
    private InferFunction<D> inferFn;
    private Optional<InstantiateFunction<D>> instFn;
    private Optional<GetChildrenFunction<D>> getChildrenFn;
    private Optional<ReplaceSymbolFunction<D>> replaceSymbolFn;

    public ExprCustom(
        D data, InferFunction<D> inferFn) {
      this(data, inferFn, null, null, null);
    }

    public ExprCustom(
        D data, InferFunction<D> inferFn, InstantiateFunction<D> instFn) {
      this(data, inferFn, instFn, null, null);
    }

    public ExprCustom(
        D data, InferFunction<D> inferFn, InstantiateFunction<D> instFn, GetChildrenFunction<D> getChildrenFn) {
      this(data, inferFn, instFn, getChildrenFn, null);
    }

    public ExprCustom(
        D data, InferFunction<D> inferFn, InstantiateFunction<D> instFn, GetChildrenFunction<D> getChildrenFn,
        ReplaceSymbolFunction<D> replaceSymbolFn) {
      this.data = data;
      this.inferFn = inferFn;
      this.instFn = Optional.ofNullable(instFn);
      this.getChildrenFn = Optional.ofNullable(getChildrenFn);
      this.replaceSymbolFn = Optional.ofNullable(replaceSymbolFn);
    }

    public ExprCustom(ExprCustom<D> other) {
      super(other);
      this.data = other.data;
      this.inferFn = other.inferFn;
      this.instFn = other.instFn;
      this.getChildrenFn = other.getChildrenFn;
      this.replaceSymbolFn = other.replaceSymbolFn;
    }

    public ExprCustom(ExprCustom<D> other, D newData) {
      super(other);
      this.data = newData;
      this.inferFn = other.inferFn;
      this.instFn = other.instFn;
      this.getChildrenFn = other.getChildrenFn;
      this.replaceSymbolFn = other.replaceSymbolFn;
    }

    public D getData() {
      return this.data;
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

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      // NOTE: for the expr custom, this safety check may not work correctly, hence it
      // just returns false.
      // This also implies that this Expr requires careful handling!
      return false;
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      // TODO(jan): this contains logic bugs and there is no way to specify at what
      // point to call the super method! Additionally, no solution changes can be
      // forwarded
      if (this.instFn.isPresent()) {
        return this.instFn.get().instantiate(this, engine, env, solution, this.data);
      }
      return this;
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
          this.getChildrenFn.equals(other.getChildrenFn) &&
          this.replaceSymbolFn.equals(other.replaceSymbolFn)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.data, this.inferFn, this.instFn, this.getChildrenFn, this.replaceSymbolFn,
          super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      if (this.replaceSymbolFn.isPresent()) {
        return this.replaceSymbolFn.get().replaceSymbol(this, original, replacement, this.data);
      }
      return this;
    }
  }
}

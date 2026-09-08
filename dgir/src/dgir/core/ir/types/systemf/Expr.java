package dgir.core.ir.types.systemf;

import java.util.ArrayList;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.traits.IExpressionCell;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.InferenceTree;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.compatibility.Scope;
import dgir.core.ir.types.traits.IIsAbstraction;

/**
 * Expressions that are valid for the SytemF Type System. All needed methods for
 * inference and type checking are implemented here
 */
public abstract class Expr extends ExprOrOperator<Expr, SystemFType> implements Expression<Expr, SystemFType> {
  private Optional<SystemFType> inferredType;
  private Optional<Operation> underlyingOperation;
  private Optional<InstantiateOperation<Expr, SystemFType>> instOp;
  private Optional<Expr> parentScopeExpr;
  private Optional<Integer> parentScopePosition;

  public Expr() {
    this.inferredType = Optional.empty();
    this.underlyingOperation = Optional.empty();
    this.instOp = Optional.empty();
    this.parentScopeExpr = Optional.empty();
    this.parentScopePosition = Optional.empty();
  }

  public Expr(Expr other) {
    this.inferredType = Optional.ofNullable(other.inferredType.orElse(null));
    this.underlyingOperation = Optional.ofNullable(other.underlyingOperation.orElse(null));
    this.instOp = Optional.ofNullable(other.instOp.orElse(null));
    this.parentScopeExpr = other.parentScopeExpr;
    this.parentScopePosition = other.parentScopePosition;
  }

  @Override
  public void setInferredType(SystemFType inferredType) {
    this.inferredType = Optional.ofNullable(inferredType);
  }

  public void setInferredType(Optional<SystemFType> inferredType) {
    this.inferredType = Optional.ofNullable(inferredType.orElse(null));
  }

  @Override
  public Optional<SystemFType> getInferredType() {
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

  // Make sure, that exprs always equals via object reference (needed for in-set
  // storage!)
  @Override
  public boolean equals(Object obj) {
    return obj instanceof Expr expr && this.inferredType.equals(expr.inferredType)
        && this.parentScopeExpr.orElse(null) == expr.parentScopeExpr.orElse(null)
        && this.parentScopePosition.equals(expr.parentScopePosition);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.inferredType,
        this.parentScopeExpr.isPresent() ? System.identityHashCode(this.parentScopeExpr.get()) : 0,
        this.parentScopePosition);
  }

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
  public Optional<Symbol<Expr, SystemFType>> getReferencedVariable() {
    return Optional.empty();
  }

  @Override
  public void setUnderlyingOperation(Operation op) {
    this.underlyingOperation = Optional.ofNullable(op);
  }

  @Override
  public Optional<Operation> getUnderlyingOperation() {
    return Optional.ofNullable(this.underlyingOperation.orElse(null));
  }

  @Override
  public Optional<Integer> getParentScopePosition() {
    return this.parentScopePosition;
  }

  @Override
  public Optional<Expr> getParentScopeExpr() {
    return this.parentScopeExpr;
  }

  public void setParentScopeExpr(Expr parent, int position) {
    this.parentScopePosition = Optional.of(position);
    this.parentScopeExpr = Optional.ofNullable(parent);
  }

  @Override
  public void setInstantiateOperationCallback(InstantiateOperation<Expr, SystemFType> callback) {
    this.instOp = Optional.ofNullable(callback);
  }

  @Override
  public Optional<InstantiateOperation<Expr, SystemFType>> getInstantiateOperationCallback() {
    return Optional.ofNullable(this.instOp.orElse(null));
  }

  /**
   * Instantiate the full expression tree to find and store all instantiations.
   * Additionally, every expression and its inferred type (determined during type
   * inference) is substituted, resulting in a fully typed Expression tree.
   *
   * <p>
   * In addition to the instantiation, a simple form of variable resolution is
   * performed, by beta-reducing variables into the concrete expressions
   * referenced by the ExprVars. This is important for later stage code
   * generation.
   *
   * @param engine   the type inference engine used to infer all types
   * @param env      an env storing all in scope expressions
   * @param solution a partial or full solution that can be used to infer all
   *                 types and instantiations
   */
  public final Expr instantiate(TypeInference engine, InstEnv<Expr, SystemFType, Context> env, Context solution) {
    Expr expr = env.getConsed(this);
    // Variables must always be visited, while other expressions must be
    // instantiated, as long as its not a recursive instantiation.
    if (expr.getReferencedVariable().isEmpty() && env.isVisisted(expr, solution)) {
      // In sequential solutions, this call works, as the solution is already
      // registered! hash cons again, just in case!
      if (env.hasSolution(expr, solution)) {
        return env.getSolutionOrThrow(expr, solution);
      } else {
        var cell = new ExprCell(expr, expr);
        env.addCellForExpr(expr, cell);
        return cell;
      }
    }

    // FIXME: this actually will hog a lot of memory in the long term, depending on
    // the expression size!
    // A possible solution would be to filter the subst to only occuring type
    // variables! And removing all already applied solutions!
    env.visit(expr, solution);

    Expr instantiated;
    instantiated = expr.instantiateInner(engine, env, solution);
    instantiated.setInferredType(instantiated.getInferredType().map(ty -> solution.apply(ty)));

    env.getCellsForExpressions(expr).stream().forEach(e -> e.replaceIfMatches(expr, instantiated));
    var instantiatedTarget = env.getConsed(instantiated);

    // The beta-reduction for variables.
    // When the variable is in scope, actually replace the returned
    // expression with the referenced instantiated Expr instance.
    // This will not work for abstract Abs parameters,
    // as those are not bound to concrete expressions.
    //
    // NOTE: in contrast to Algorithm W, System F has no unification.
    // The variable lookup can never generalize anything, hence it can also not
    // contribute to the type solution application.
    var referencedExpr = instantiatedTarget.getReferencedVariable();
    if (referencedExpr.isPresent()) {
      var referencedFromEnv = env.getExprAndPosition(instantiatedTarget.getReferencedVariable().get());
      if (referencedFromEnv.isPresent()) {
        var scopeExpression = env.getScopeExpression(instantiatedTarget.getReferencedVariable().get());

        var referencedExprAsExpr = engine.asExpression(referencedFromEnv.get().getLeft());
        var referencedInferredType = referencedExprAsExpr.getInferredType();

        if (referencedInferredType.isPresent() && instantiatedTarget.getInferredType().isPresent()) {
          var copiedExpr = referencedExprAsExpr.copy();
          copiedExpr.setParentScopeExpr(scopeExpression.get(), referencedFromEnv.get().getRight());

          Expr instantiatedReferenced = copiedExpr.instantiate(engine, env, solution);

          // After instantiation, return the actual expression not the variable!
          // NOTE: the instantiatedReferenced is already hash-consed
          env.setSolution(instantiatedTarget, solution, instantiatedReferenced);
          env.setSolution(expr, solution, instantiatedReferenced);
          return instantiatedReferenced;
        }
      }
    }

    env.setSolution(expr, solution, instantiatedTarget);
    env.setSolution(instantiatedTarget, solution, instantiatedTarget);
    return instantiatedTarget;
  }

  /**
   * Instantiate the correct type instance for code generation.
   * This instance is stored within the expression.
   * A default implementation is not possible, as every expression decides which
   * children are instantiated and how the resulting tree node is built.
   *
   * <p>
   * The {@link InstEnv} will act as a scope-like env storing expressions.
   * Additionally, the {@link InstEnv} will store all visited expressions in
   * combination with the {@link Context} solution.
   *
   * @param engine   the inference engine that provides useful helper methods,
   *                 like `asExpression`
   * @param env      the instance env, collecting visited expressions and acting
   *                 as a scope-like env
   * @param solution a partial or full solution that can be used to infer all
   *                 types and instantiations
   */
  protected abstract Expr instantiateInner(
      TypeInference engine,
      InstEnv<Expr, SystemFType, Context> env,
      Context solution);

  /**
   * ExprCell is an inherently mutable cell, that just references a changeable
   * value in place! Overall, it will just copy the behaviour of the inner expr!
   *
   * <p>
   * No instantiation, replace and other operations are permitted.
   *
   * <p>
   * NOTE: it can be expected, that the cell may only ever occur in recursive
   * functions to trace back the solutions to the original expression! Hence, this
   * expression represents a dead end and is therefore a recursion breaker during
   * instantiation!
   */
  private static final class ExprCell extends Expr implements IExpressionCell<Expr, SystemFType> {
    private Expr reference;
    private Expr cellValue;

    public ExprCell(Expr reference, Expr cellValue) {
      this.reference = reference;
      this.cellValue = cellValue;
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
    public Optional<SystemFType> getInferredType() {
      return cellValue.getInferredType();
    }

    @Override
    public void setInferredType(SystemFType inferredType) {
      cellValue.setInferredType(inferredType);
    }

    @Override
    public void setInferredType(Optional<SystemFType> inferredType) {
      cellValue.setInferredType(inferredType);
    }

    @Override
    public Optional<Operation> getUnderlyingOperation() {
      return cellValue.getUnderlyingOperation();
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(cellValue);
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return this;
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return false;
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      throw new UnsupportedOperationException("The memory cell is expected to only exist after instantiation!");
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      throw new UnsupportedOperationException("The memory cell is expected to only exist after instantiation!");
    }

    @Override
    public Expr unwrap() {
      return this.cellValue;
    }

    @Override
    public void replaceIfMatches(Expr reference, Expr replacement) {
      if (reference == this.reference) {
        this.cellValue = replacement;
      }
    }

    @Override
    public Expr copy() {
      return this;
    }

  }

  public abstract TypeResult infer(
      TypeInference engine,
      Context ctx);

  public CheckResult check(
      TypeInference engine,
      Context ctx,
      SystemFType ty) {
    var input = ctx + " |- " + this + " <=" + ty;
    if (ty instanceof SystemFType.ForAll forall) {
      var newCtx = ctx.copy();
      var mark = new Entry.Mark();
      newCtx.push(mark);
      newCtx.push(new Entry.TVarBnd(forall.boundVar));

      CheckResult checkRes = engine.check(newCtx, this, forall.body);
      Break3Result breakRes = checkRes.ctx().break3(
          entry -> entry instanceof Entry.Mark m &&
              m.equals(mark));

      Context finalCtx = new Context(breakRes.left(), checkRes.ctx());
      return new CheckResult(
          finalCtx,
          new InferenceTree(
              "ChkAll",
              input,
              "" + finalCtx,
              List.of(checkRes.tree())));
    }

    var inferred = engine.infer(ctx, this);
    var inferredApplied = inferred.ctx().apply(inferred.type());
    var typeApplied = inferred.ctx().apply(ty);
    var subtyped = engine.subtype(inferred.ctx(), inferredApplied, typeApplied);
    return new CheckResult(
        subtyped.ctx(),
        new InferenceTree(
            "ChkSub",
            input,
            "" + inferred.ctx(),
            List.of(inferred.tree(), subtyped.tree())));
  }

  public static final class Var extends Expr {

    private final Symbol<Expr, SystemFType> name;

    public Var(Symbol<Expr, SystemFType> name) {
      this.name = name;
    }

    public Var(Var other) {
      super(other);
      this.name = other.name;
    }

    public Var(Var other, Symbol<Expr, SystemFType> name) {
      super(other);
      this.name = name;
    }

    @Override
    public final String toString() {
      return name + "";
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var input = ctx + " |- " + this;
      var boundVariable = ctx.find(
          entry -> entry instanceof Entry.VarBnd bnd && bnd.tmVar().equals(this.name));

      if (boundVariable.isPresent()) {
        var varBnd = (Entry.VarBnd) boundVariable.get();
        return new TypeResult(
            varBnd.type(),
            ctx.copy(),
            new InferenceTree(
                "InfVar",
                ctx + " |- " + this,
                input + " => " + varBnd.type() + " -| " + ctx,
                List.of()));
      }

      throw new TypingException.UnboundVariable(this.name);
    }

    @Override
    public List<Expr> getChildren() {
      return List.of();
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return this.name.equals(symbol);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Var other && this.name.equals(other.name) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.name, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      if (this.name.equals(original)) {
        return new Var(this, replacement);
      }

      return this;
    }

    @Override
    public Expr copy() {
      return new Var(this);
    }

    @Override
    public Optional<Symbol<Expr, SystemFType>> getReferencedVariable() {
      return Optional.of(this.name);
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      // Nothing to instantiate;
      return this;
    }
  }

  public static final class App extends Expr {

    private final Expr fun;
    private final Expr arg;

    public App(Expr fun, Expr arg) {
      this.fun = fun;
      this.arg = arg;
    }

    public App(App other) {
      super(other);
      this.fun = other.fun;
      this.arg = other.arg;
    }

    public App(App other, Expr fun, Expr arg) {
      super(other);
      this.fun = fun;
      this.arg = arg;
    }

    public Expr fun() {
      return this.fun.unwrapOrThis();
    }

    public Expr arg() {
      return this.arg.unwrapOrThis();
    }

    @Override
    public final String toString() {
      return fun + " " + arg;
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var input = ctx + " |- " + this;
      var funcInferred = engine.infer(ctx, this.fun);
      var funcTypeApplied = funcInferred.ctx().apply(funcInferred.type());

      TypeResult result;
      if (funcTypeApplied instanceof SystemFType.Arrow arrow) {
        var paramTy = arrow.from;
        var resultTy = arrow.to;

        var paramCheck = engine.check(funcInferred.ctx(), this.arg, paramTy);
        result = new TypeResult(
            resultTy,
            paramCheck.ctx(),
            new InferenceTree(
                "InfAppArr",
                input,
                input + " =>=> " + resultTy + paramCheck.ctx(),
                List.of(paramCheck.tree())));
      } else if (funcTypeApplied instanceof SystemFType.EtVar etvar) {
        var a = etvar.tyVar;

        var a1 = new TypeVar();
        var a2 = new TypeVar();

        var breakRes = funcInferred.ctx().break3(
            entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(a));
        var arrowType = new SystemFType.Arrow(
            new SystemFType.EtVar(a1),
            new SystemFType.EtVar(a2));

        var newCtx = new Context(breakRes.left(), funcInferred.ctx());
        newCtx.push(new Entry.SETVarBnd(a, arrowType));
        newCtx.push(new Entry.ETVarBnd(a1));
        newCtx.push(new Entry.ETVarBnd(a2));
        newCtx.extend(breakRes.right());

        var checkRes = engine.check(
            newCtx,
            this.arg,
            new SystemFType.EtVar(a1));

        var output = input + " =>=> ^" + a2 + " -| " + checkRes.ctx();
        result = new TypeResult(
            new SystemFType.EtVar(a2),
            checkRes.ctx(),
            new InferenceTree(
                "InfAppETVar",
                input,
                output,
                List.of(checkRes.tree())));
      } else {
        throw new TypingException.ApplicationTypeError();
      }

      var output = input + " => " + result.type() + " -| " + result.ctx();
      return new TypeResult(
          result.type(),
          result.ctx(),
          new InferenceTree(
              "InfApp",
              input,
              output,
              List.of(funcInferred.tree(), result.tree())));
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(this.arg, this.fun);
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return this.fun.containsSymbol(symbol) || this.arg.containsSymbol(symbol);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof App other && this.fun.equals(other.fun) && this.arg.equals(other.arg)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.fun, this.arg, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new App(this.fun.replaceSymbol(original, replacement), this.arg.replaceSymbol(original, replacement));
    }

    @Override
    public Expr copy() {
      return new App(this);
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      return new App(this, this.fun.instantiate(engine, env, solution),
          this.arg.instantiate(engine, env, solution));
    }
  }

  public static final class Abs extends Expr implements IIsAbstraction<Expr, SystemFType> {

    private final Symbol<Expr, SystemFType> name;
    private final SystemFType type;
    private final Expr body;

    public Abs(Symbol<Expr, SystemFType> name, SystemFType type, Expr body) {
      this.name = name;
      this.type = type;
      this.body = body;
    }

    public Abs(Abs other) {
      super(other);
      this.name = other.name;
      this.type = other.type;
      this.body = other.body;
    }

    public Abs(Abs other, Symbol<Expr, SystemFType> name, SystemFType type, Expr body) {
      super(other);
      this.name = name;
      this.type = type;
      this.body = body;
    }

    @Override
    public final String toString() {
      return "λ" + name + ": " + type + ". " + body;
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var input = ctx + " |- " + this;
      var b = new TypeVar();
      var newCtx = ctx.copy();
      Scope<SystemFType> scope = newCtx.addScope();

      var mark = new Entry.Mark();
      newCtx.push(mark);

      newCtx.push(new Entry.VarBnd(this.name, this.type));
      newCtx.push(new Entry.ETVarBnd(b));

      var c1 = engine.check(newCtx, this.body, new SystemFType.EtVar(b));

      Context furtherCtx = c1.ctx().copy();
      SystemFType resultType = new SystemFType.EtVar(b);
      ArrayList<InferenceTree> trees = new ArrayList<>();

      for (var retType : scope.getAllReturnTypesInScope()) {
        SubtypeResult subTypeRes = engine.subtype(furtherCtx, retType, resultType);
        furtherCtx = subTypeRes.ctx();
        trees.add(subTypeRes.tree());
        resultType = furtherCtx.apply(resultType);
      }

      var breakRes = furtherCtx.break3(
          entry -> entry instanceof Entry.Mark m && m.equals(mark));

      var solvedFinalCtxEntries = new ArrayList<>(breakRes.left());
      solvedFinalCtxEntries.addAll(breakRes.right()
          .stream()
          .filter(entry -> entry instanceof Entry.SETVarBnd)
          .collect(Collectors.toCollection(() -> new ArrayList<Entry>())));

      var finalCtx = new Context(solvedFinalCtxEntries, furtherCtx);
      var resType = new SystemFType.Arrow(
          this.type,
          resultType);

      return new TypeResult(
          resType,
          finalCtx,
          new InferenceTree(
              "InfLam",
              input,
              input + " => " + resType + " -| " + finalCtx,
              List.of(c1.tree())));
    }

    @Override
    public CheckResult check(
        TypeInference engine,
        Context ctx,
        SystemFType ty) {
      var input = ctx + " |- " + this + " <=" + ty;
      if (ty instanceof SystemFType.Arrow arrow) {
        var newCtx = ctx.copy();
        var mark = new Entry.Mark();
        newCtx.push(mark);
        newCtx.push(new Entry.VarBnd(this.name, arrow.from));

        var bodyCheck = engine.check(newCtx, this.body, arrow.to);
        var break3Result = bodyCheck.ctx().break3(
            entry -> entry instanceof Entry.Mark m && m.equals(mark));

        var finalCtx = new Context(break3Result.left(), bodyCheck.ctx());

        return new CheckResult(
            finalCtx,
            new InferenceTree(
                "ChkLam",
                input,
                "" + finalCtx,
                List.of(bodyCheck.tree())));
      } else {
        return super.check(engine, ctx, ty);
      }
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(this.body);
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return this.name.equals(symbol) || this.body.containsSymbol(symbol);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Abs other && this.name.equals(other.name) && this.type.equals(other.type)
          && this.body.equals(other.body) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.name, this.type, this.body, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new Abs(this, this.name, this.type, this.body.replaceSymbol(original, replacement));
    }

    @Override
    public List<Symbol<Expr, SystemFType>> getAbstractionsOverSymbols() {
      return List.of(this.name);
    }

    @Override
    public Expr getAbstractionBody() {
      return this.body;
    }

    @Override
    public Expr copy() {
      return new Abs(this);
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      return new Abs(this, this.name, this.type, this.body.instantiate(engine, env, solution));
    }
  }

  public static final class TApp extends Expr {

    private final Expr func;
    private final SystemFType type;

    public TApp(Expr func, SystemFType type) {
      this.func = func;
      this.type = type;
    }

    public TApp(TApp other) {
      super(other);
      this.func = other.func;
      this.type = other.type;
    }

    public TApp(TApp other, Expr func, SystemFType type) {
      super(other);
      this.func = func;
      this.type = type;
    }

    @Override
    public final String toString() {
      return func + " " + type;
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var input = ctx + " |- " + this;
      var funcInferred = engine.infer(ctx, this.func);
      if (funcInferred.type() instanceof SystemFType.ForAll forall) {
        var resultType = engine.substType(
            forall.boundVar,
            this.type,
            forall.body);
        var output = input + " => " + resultType + " -| " + funcInferred.ctx();
        return new TypeResult(
            resultType,
            funcInferred.ctx(),
            new InferenceTree(
                "InfTApp",
                input,
                output,
                List.of(funcInferred.tree())));
      } else {
        throw new TypingException.ExpectedForAllType();
      }
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(this.func);
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return this.func.containsSymbol(symbol);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof TApp other && this.func.equals(other.func) && this.type.equals(other.type)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.func, this.type, super.hashCode());
    }

    @Override
    public Expr copy() {
      return new TApp(this);
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new TApp(this, this.func.replaceSymbol(original, replacement), this.type);
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      assert this.func.getInferredType().isPresent();
      assert this.func.getInferredType().get() instanceof SystemFType.ForAll;

      var forAll = (SystemFType.ForAll) this.func.getInferredType().get();

      var newCtx = solution.copy();
      newCtx.push(new Entry.SVarBnd(forAll.boundVar, this.type));

      return this.func.instantiate(engine, env, newCtx);
    }
  }

  public static final class Ann extends Expr {

    private final Expr expr;
    private final SystemFType type;

    public Ann(Expr expr, SystemFType type) {
      this.expr = expr;
      this.type = type;
    }

    public Ann(Ann other) {
      super(other);
      this.expr = other.expr;
      this.type = other.type;
    }

    public Ann(Ann other, Expr expr, SystemFType type) {
      super(other);
      this.expr = expr;
      this.type = type;
    }

    @Override
    public final String toString() {
      return expr + " : " + type;
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var input = ctx + " |- " + this;
      var checked = engine.check(ctx, this.expr, this.type);

      return new TypeResult(
          this.type,
          checked.ctx(),
          new InferenceTree(
              "InfAnn",
              input,
              input + " => " + this.type + " -| " + checked.ctx(),
              List.of(checked.tree())));
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(this.expr);
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return this.expr.containsSymbol(symbol);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Ann other && this.expr.equals(other.expr) && this.type.equals(other.type)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.expr, this.type, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new Ann(this, this.expr.replaceSymbol(original, replacement), this.type);
    }

    @Override
    public Expr copy() {
      return new Ann(this);
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      // Simply return the inner as fully instantiated!
      return this.expr.instantiate(engine, env, solution);
    }
  }

  public static final class TAbs extends Expr {

    private final TypeVar variable;
    private final Expr body;

    public TAbs(TypeVar variable, Expr body) {
      this.variable = variable;
      this.body = body;
    }

    public TAbs(TAbs other) {
      super(other);
      this.variable = other.variable;
      this.body = other.body;
    }

    public TAbs(TAbs other, TypeVar variable, Expr body) {
      super(other);
      this.variable = variable;
      this.body = body;
    }

    @Override
    public final String toString() {
      return "∀" + variable + ". " + body;
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var input = ctx + " |- " + this;
      var newCtx = ctx.copy();
      var mark = new Entry.Mark();
      newCtx.push(mark);
      newCtx.push(new Entry.TVarBnd(this.variable));

      var bodyInferred = engine.infer(newCtx, this.body);

      var resolvedBodyType = bodyInferred.ctx().apply(bodyInferred.type());

      var break3Result = bodyInferred.ctx().break3(
          entry -> entry instanceof Entry.Mark m &&
              m.equals(mark));

      var solvedFinalCtxEntries = new ArrayList<>(break3Result.left());

      solvedFinalCtxEntries.addAll(break3Result.right()
          .stream()
          .filter(entry -> entry instanceof Entry.SETVarBnd)
          .collect(Collectors.toCollection(() -> new ArrayList<Entry>())));
      var finalCtx = new Context(solvedFinalCtxEntries, bodyInferred.ctx());
      var resType = new SystemFType.ForAll(this.variable, resolvedBodyType);

      var output = input + " => " + resType + " -| " + finalCtx;

      return new TypeResult(
          resType,
          finalCtx,
          new InferenceTree(
              "InfTAbs",
              input,
              output,
              List.of(bodyInferred.tree())));
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(this.body);
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return this.body.containsSymbol(symbol);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof TAbs other && this.variable.equals(other.variable) && this.body.equals(other.body)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.variable, this.body, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new TAbs(this, this.variable, this.body.replaceSymbol(original, replacement));
    }

    @Override
    public Expr copy() {
      return new TAbs(this);
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      return this.body.instantiate(engine, env, solution);
    }
  }

  public static final class LitExpr extends Expr {

    private final Literal lit;

    public LitExpr(Literal lit) {
      this.lit = lit;
    }

    public LitExpr(LitExpr other) {
      super(other);
      this.lit = other.lit;
    }

    @Override
    public final String toString() {
      return lit.toString();
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var input = ctx + " |- " + this;
      var res = engine.generalNominalTypeToInferenceType(lit.toParameterizedNominalType(), Optional.of(ctx));
      return new TypeResult(
          res.getLeft(),
          ((Context) res.getRight().get()).copy(), // This is safe, as the ctx is provided as a Some(ctx)
          new InferenceTree(
              "InfLit" + res,
              input,
              input + " => " + res + " -| " + ctx,
              List.of()));
    }

    @Override
    public List<Expr> getChildren() {
      return List.of();
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return false;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof LitExpr other && this.lit.equals(other.lit) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.lit, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return this;
    }

    @Override
    public Expr copy() {
      return new LitExpr(this);
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      return this;
    }
  }

  public static final class Tuple extends Expr {

    private final List<Expr> elements;

    public Tuple(List<Expr> elements) {
      this.elements = List.copyOf(elements);
    }

    public Tuple(Expr... elements) {
      this.elements = List.of(elements);
    }

    public Tuple(Tuple other) {
      super(other);
      this.elements = List.copyOf(other.elements);
    }

    public Tuple(Tuple other, List<Expr> elements) {
      super(other);
      this.elements = List.copyOf(elements);
    }

    public List<Expr> elements() {
      return this.elements.stream().map(Expr::unwrapOrThis).toList();
    }

    @Override
    public final String toString() {
      return "(" +
          this.elements.stream().map(Object::toString).collect(Collectors.joining(", ")) +
          ")";
    }

    @Override
    public List<Expr> getChildren() {
      return this.elements;
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return this.elements.stream().anyMatch(elem -> elem.containsSymbol(symbol));
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var input = ctx + " |- " + this;
      var trees = new ArrayList<InferenceTree>();
      var currentCtx = ctx.copy();
      var types = new ArrayList<SystemFType>();

      for (var elem : this.elements) {
        var res = engine.infer(currentCtx, elem);
        currentCtx = res.ctx();
        types.add(currentCtx.apply(res.type()));
        trees.add(res.tree());
      }

      var resultType = new SystemFType.Tuple(List.copyOf(types));
      return new TypeResult(
          resultType,
          currentCtx,
          new InferenceTree(
              "InfTuple",
              input,
              resultType.toString(),
              List.copyOf(trees)));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Tuple other && this.elements.equals(other.elements) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.elements, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new Tuple(this,
          this.elements.stream().map(elem -> elem.replaceSymbol(original, replacement)).toList());
    }

    @Override
    public Expr copy() {
      return new Tuple(this);
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      return new Tuple(this, this.elements.stream().map(elem -> elem.instantiate(engine, env, solution)).toList());
    }
  }

  public static final class Let extends Expr {

    private final List<Pair<Symbol<Expr, SystemFType>, Expr>> bindings;
    private Expr body;

    public Let(Symbol<Expr, SystemFType> name, Expr value,
        Expr body) {
      this.bindings = List.of(Pair.of(name, value));
      this.body = body;
    }

    public Let(List<Pair<Symbol<Expr, SystemFType>, Expr>> bindings,
        Expr body) {
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public Let(Let other) {
      super(other);
      this.bindings = List.copyOf(other.bindings);
      this.body = other.body;
    }

    public Let(Let other, List<Pair<Symbol<Expr, SystemFType>, Expr>> bindings,
        Expr body) {
      super(other);
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    @Override
    public final String toString() {
      return "let (" + this.bindings.stream().map(Object::toString).collect(Collectors.joining(", ")) + ") in "
          + body;
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var input = ctx + " |- " + this;
      Context newCtx = ctx.copy();
      ArrayList<InferenceTree> trees = new ArrayList<>();
      ArrayList<Triple<Symbol<Expr, SystemFType>, TypeVar, ExprOrOperator<Expr, SystemFType>>> nonUnified = new ArrayList<>(
          this.bindings.size());

      var mark = new Entry.Mark();
      newCtx.push(mark);

      for (var binding : this.bindings) {
        var typeVar = new TypeVar();
        nonUnified.add(Triple.of(binding.getLeft(), typeVar, binding.getRight()));
        newCtx.push(new Entry.ETVarBnd(typeVar));
        newCtx.push(new Entry.VarBnd(binding.getLeft(), new SystemFType.EtVar(typeVar)));
      }

      for (var binding : nonUnified) {
        var typeVar = binding.getMiddle();
        var expr = binding.getRight();
        var valueInferred = engine.check(newCtx, expr, newCtx.apply(new SystemFType.EtVar(typeVar)));
        // NOTE: this is a workaround. Normally, the infer method sets the inferred
        // type. In this case however, as the correct type for `typeVar` is just stored
        // within the context and no direct inference call is performed, the inference
        // result must be stored here!
        engine.asExpression(expr).setInferredType(new SystemFType.EtVar(typeVar));
        newCtx = valueInferred.ctx().copy();

        trees.add(valueInferred.tree());
      }

      var bodyInferred = engine.infer(newCtx, this.body);
      trees.add(bodyInferred.tree());

      var break3Result = bodyInferred.ctx().break3(
          entry -> entry instanceof Entry.Mark m && m.equals(mark));

      var solvedFinalCtxEntries = new ArrayList<>(break3Result.left());

      solvedFinalCtxEntries.addAll(break3Result.right()
          .stream()
          .filter(entry -> entry instanceof Entry.SETVarBnd)
          .collect(Collectors.toCollection(() -> new ArrayList<Entry>())));
      var finalCtx = new Context(solvedFinalCtxEntries, bodyInferred.ctx());

      return new TypeResult(
          bodyInferred.type(),
          finalCtx,
          new InferenceTree(
              "InfLet*",
              input,
              input + " => " + bodyInferred.type() + " -| " + finalCtx,
              List.copyOf(trees)));
    }

    @Override
    public List<Expr> getChildren() {
      ArrayList<Expr> list = new ArrayList<>();
      list.addAll(this.bindings.stream().map(elem -> elem.getRight()).toList());
      list.add(this.body);
      return List.copyOf(list);
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return this.body.containsSymbol(symbol) || this.bindings.stream()
          .anyMatch(bnd -> bnd.getLeft().equals(symbol) || bnd.getRight().containsSymbol(symbol));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Let other && this.bindings.equals(other.bindings) && this.body.equals(other.body)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.bindings, this.body, super.hashCode());
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      var newLetExpr = new Let(this, List.copyOf(this.bindings), new LitExpr(new Literal.Unit()));

      // This line is key, as the defining scope expression, in this case `newLetExpr`
      // is bound to the scope
      var newEnv = new InstEnv<Expr, SystemFType, Context>(env, newLetExpr);
      for (int i = 0; i < this.bindings.size(); i++) {
        var bnd = this.bindings.get(i);
        newEnv.put(bnd.getLeft(), bnd.getRight(), i);
      }

      newLetExpr.body = this.body.instantiate(engine, newEnv, solution);

      return newLetExpr;
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new Let(this,
          this.bindings.stream()
              .map(bnd -> Pair.of(bnd.getLeft(), bnd.getRight().replaceSymbol(original, replacement))).toList(),
          this.body.replaceSymbol(original, replacement));
    }

    @Override
    public Expr copy() {
      return new Let(this);
    }
  }

  public final class Return extends Expr {

    private Expr value;

    public Return(Expr value) {
      this.value = value;
    }

    public Return(Return other) {
      super(other);
      this.value = other.value;
    }

    public Return(Return other, Expr value) {
      super(other);
      this.value = value;
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var input = ctx + " |- " + this;

      TypeResult res = engine.infer(ctx, this.value);
      SystemFType resultType = res.ctx().apply(res.type());

      var output = input + " => Bool -| " + res.ctx();
      return new TypeResult(resultType, res.ctx(), new InferenceTree("InfRet", input, output, List.of(res.tree())));
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(this.value);
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return this.value.containsSymbol(symbol);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Return other && this.value.equals(other.value) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.value, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new Return(this, this.value.replaceSymbol(original, replacement));
    }

    @Override
    public Expr copy() {
      return new Return(this);
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      return new Return(this, this.value.instantiate(engine, env, solution));
    }
  }

  /**
   * Seq is a sequence of expressions that infers all expressions sequentially,
   * where the type of the last expression is the type of the whole sequence.
   *
   * <p>
   * This is mainly considered to be useful with sequential definitions, i.e.
   * normal blocks, mirroring the {@code ExprSeq} of the Algorithm W dialect.
   */
  public static final class Seq extends Expr {

    private List<Expr> expressions;

    public Seq(Expr expr) {
      this.expressions = List.of(expr);
    }

    public Seq(List<Expr> exprs) {
      this.expressions = List.copyOf(exprs);
    }

    public Seq(Expr... exprs) {
      this.expressions = List.of(exprs);
    }

    public Seq(Seq other) {
      super(other);
      this.expressions = List.copyOf(other.expressions);
    }

    public Seq(Seq other, List<Expr> exprs) {
      super(other);
      this.expressions = List.copyOf(exprs);
    }

    public List<Expr> expressions() {
      return this.expressions.stream().map(Expr::unwrapOrThis).toList();
    }

    @Override
    public final String toString() {
      return "(" +
          this.expressions.stream().map(Object::toString).collect(Collectors.joining("; ")) +
          ")";
    }

    @Override
    public List<Expr> getChildren() {
      return List.copyOf(this.expressions);
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return this.expressions.stream().anyMatch(e -> e.containsSymbol(symbol));
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var input = ctx + " |- " + this;

      var trees = new ArrayList<InferenceTree>();
      var currentCtx = ctx.copy();
      SystemFType lastType = new SystemFType.Lit(TypeIdent.TYPE_IDENT_UNIT);

      for (var expr : this.expressions) {
        var infRes = engine.infer(currentCtx, expr);
        currentCtx = infRes.ctx();
        lastType = currentCtx.apply(infRes.type());
        trees.add(infRes.tree());
      }

      return new TypeResult(
          lastType,
          currentCtx,
          new InferenceTree(
              "InfSeq",
              input,
              lastType.toString(),
              List.copyOf(trees)));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Seq other && this.expressions.equals(other.expressions) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.expressions, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new Seq(this, this.expressions.stream().map(e -> e.replaceSymbol(original, replacement)).toList());
    }

    @Override
    public Expr copy() {
      return new Seq(this);
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      return new Seq(this, this.expressions.stream().map(e -> e.instantiate(engine, env, solution)).toList());
    }
  }

  public final class Custom<D> extends Expr {

    @FunctionalInterface
    public interface InferFunction<D> {
      TypeResult infer(TypeInference engine, Context ctx, D data);
    }

    @FunctionalInterface
    public interface CheckFunction<D> {
      CheckResult check(TypeInference engine, Context ctx, SystemFType ty, D data);
    }

    @FunctionalInterface
    public interface InstantiateFunction<D> {
      Expr instantiate(TypeInference engine, Context solution, D data);
    }

    @FunctionalInterface
    public interface GetChildrenFunction<D> {
      List<Expr> getChildren(D data);
    }

    private D data;
    private InferFunction<D> inferFn;
    private Optional<CheckFunction<D>> checkFn;
    private Optional<InstantiateFunction<D>> instFn;
    private Optional<GetChildrenFunction<D>> getChildrenFn;

    public Custom(D data, InferFunction<D> inferFn) {
      this(data, inferFn, null, null);
    }

    public Custom(D data, InferFunction<D> inferFn, CheckFunction<D> checkFn) {
      this(data, inferFn, checkFn, null);
    }

    public Custom(D data, InferFunction<D> inferFn, CheckFunction<D> checkFn, GetChildrenFunction<D> getChildrenFn) {
      this.data = data;
      this.inferFn = inferFn;
      this.checkFn = Optional.ofNullable(checkFn);
      this.getChildrenFn = Optional.ofNullable(getChildrenFn);
    }

    public Custom(Custom<D> other) {
      super(other);
      this.data = other.data;
      this.inferFn = other.inferFn;
      this.checkFn = other.checkFn;
      this.instFn = other.instFn;
      this.getChildrenFn = other.getChildrenFn;
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      return this.inferFn.infer(engine, ctx, data);
    }

    @Override
    public CheckResult check(TypeInference engine, Context ctx, SystemFType ty) {
      if (this.checkFn.isPresent()) {
        return this.checkFn.get().check(engine, ctx, ty, data);
      } else {
        return super.check(engine, ctx, ty);
      }
    }

    @Override
    public List<Expr> getChildren() {
      if (this.getChildrenFn.isPresent()) {
        return this.getChildrenFn.get().getChildren(this.data);
      }

      return List.of();
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return false;
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      // TODO Auto-generated method stub
      throw new UnsupportedOperationException("Unimplemented method 'replaceSymbol'");
    }

    @Override
    public Expr copy() {
      return new Custom<>(this);
    }

    @Override
    protected Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      if (this.instFn != null && this.instFn.isPresent()) {
        return this.instFn.get().instantiate(engine, solution, this.data);
      }
      return this;
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.data, this.inferFn, this.instFn, this.getChildrenFn, super.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Custom<?> other &&
          this.data.equals(other.data) &&
          this.inferFn.equals(other.inferFn) &&
          this.instFn != null && this.instFn.equals(other.instFn) &&
          this.getChildrenFn != null && this.getChildrenFn.equals(other.getChildrenFn)
          && super.equals(obj);
    }

  }

}

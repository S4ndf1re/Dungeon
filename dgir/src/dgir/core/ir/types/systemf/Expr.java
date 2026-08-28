package dgir.core.ir.types.systemf;

import java.util.ArrayList;
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

/**
 * Expressions that are valid for the SytemF Type System. All needed methods for
 * inference and type checking are implemented here
 */
public abstract class Expr extends ExprOrOperator<Expr, SystemFType> implements Expression<Expr, SystemFType> {
  private Optional<SystemFType> inferredType;
  private Optional<Operation> underlyingOperation;
  private Optional<InstantiateOperation<Expr, SystemFType>> instOp;

  public Expr() {
    this.inferredType = Optional.empty();
    this.underlyingOperation = Optional.empty();
    this.instOp = Optional.empty();
  }

  public Expr(Expr other) {
    this.inferredType = Optional.ofNullable(other.inferredType.orElse(null));
    this.underlyingOperation = Optional.ofNullable(other.underlyingOperation.orElse(null));
    this.instOp = Optional.ofNullable(this.instOp.orElse(null));
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

  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object obj);

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
    return Optional.empty();
  }

  @Override
  public Optional<Expr> getParentScopeExpr() {
    return Optional.empty();
  }

  @Override
  public void setInstantiateOperationCallback(InstantiateOperation<Expr, SystemFType> callback) {
    this.instOp = Optional.ofNullable(callback);
  }

  @Override
  public Optional<InstantiateOperation<Expr, SystemFType>> getInstantiateOperationCallback() {
    return Optional.ofNullable(this.instOp.orElse(null));
  }

  protected void instantiateInner(TypeInference engine, Context solution) {
    this.setInferredType(this.getInferredType().map(ty -> solution.apply(ty)));
    this.getChildren().forEach(child -> engine.asExpression(child).instantiate(engine, solution));
  }

  public final void instantiate(TypeInference engine, Context solution) {
    if (solution.isVisited(this)) {
      return;
    }

    solution.visit(this);

    this.instantiateInner(engine, solution);

    var referencedVariable = this.getReferencedVariable();
    if (referencedVariable.isPresent()) {
      var boundExpr = solution
          .find(entry -> entry instanceof Entry.VarExpr vexpr && vexpr.symbol().equals(referencedVariable.get()));
      if (boundExpr.isPresent()) {
        var exprOrOp = ((Entry.VarExpr) boundExpr.get()).expr();
        var expr = engine.asExpression(exprOrOp);
        expr.instantiate(engine, solution);
      }
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

    public Var(Var other, Symbol<Expr, SystemFType> name) {
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
    public boolean equals(Object obj) {
      return obj instanceof Var other && this.name.equals(other.name);
    }

    @Override
    public int hashCode() {
      return this.name.hashCode();
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      if (this.name.equals(original)) {
        return new Var(this, replacement);
      }

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

    public App(App other, Expr fun, Expr arg) {
      this.fun = fun;
      this.arg = arg;
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
    public boolean equals(Object obj) {
      return obj instanceof App other && this.fun.equals(other.fun) && this.arg.equals(other.arg);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.fun, this.arg);
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new App(this.fun.replaceSymbol(original, replacement), this.arg.replaceSymbol(original, replacement));
    }
  }

  public static final class Abs extends Expr {

    private final Symbol<Expr, SystemFType> name;
    private final SystemFType type;
    private final Expr body;

    public Abs(Symbol<Expr, SystemFType> name, SystemFType type, Expr body) {
      this.name = name;
      this.type = type;
      this.body = body;
    }

    public Abs(Abs other, Symbol<Expr, SystemFType> name, SystemFType type, Expr body) {
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
    public boolean equals(Object obj) {
      return obj instanceof Abs other && this.name.equals(other.name) && this.type.equals(other.type)
          && this.body.equals(other.body);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.name, this.type, this.body);
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new Abs(this, this.name, this.type, this.body.replaceSymbol(original, replacement));
    }
  }

  public static final class TApp extends Expr {

    private final Expr func;
    private final SystemFType type;

    public TApp(Expr func, SystemFType type) {
      this.func = func;
      this.type = type;
    }

    public TApp(TApp other, Expr func, SystemFType type) {
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
    public boolean equals(Object obj) {
      return obj instanceof TApp other && this.func.equals(other.func) && this.type.equals(other.type);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.func, this.type);
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new TApp(this, this.func.replaceSymbol(original, replacement), this.type);
    }
  }

  public static final class Ann extends Expr {

    private final Expr expr;
    private final SystemFType type;

    public Ann(Expr expr, SystemFType type) {
      this.expr = expr;
      this.type = type;
    }

    public Ann(Ann other, Expr expr, SystemFType type) {
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
    public boolean equals(Object obj) {
      return obj instanceof Ann other && this.expr.equals(other.expr) && this.type.equals(other.type);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.expr, this.type);
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new Ann(this, this.expr.replaceSymbol(original, replacement), this.type);
    }
  }

  public static final class TAbs extends Expr {

    private final TypeVar variable;
    private final Expr body;

    public TAbs(TypeVar variable, Expr body) {
      this.variable = variable;
      this.body = body;
    }

    public TAbs(TAbs other, TypeVar variable, Expr body) {
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
    public boolean equals(Object obj) {
      return obj instanceof TAbs other && this.variable.equals(other.variable) && this.body.equals(other.body);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.variable, this.body);
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new TAbs(this, this.variable, this.body.replaceSymbol(original, replacement));
    }
  }

  public static final class LitExpr extends Expr {

    private final Literal lit;

    public LitExpr(Literal lit) {
      this.lit = lit;
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
    public boolean equals(Object obj) {
      return obj instanceof LitExpr other && this.lit.equals(other.lit);
    }

    @Override
    public int hashCode() {
      return this.lit.hashCode();
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return this;
    }
  }

  public static final class Let extends Expr {

    private final List<Pair<Symbol<Expr, SystemFType>, Expr>> bindings;
    private final Expr body;

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

    public Let(Let other, List<Pair<Symbol<Expr, SystemFType>, Expr>> bindings,
        Expr body) {
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
    public boolean equals(Object obj) {
      return obj instanceof Let other && this.bindings.equals(other.bindings) && this.body.equals(other.body);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.bindings, this.body);
    }

    @Override
    protected void instantiateInner(TypeInference engine, Context solution) {
      var newCtx = solution.copy();
      var mark = new Entry.Mark();
      newCtx.push(mark);

      for (var bnd : this.bindings) {
        newCtx.push(new Entry.VarExpr(bnd.getLeft(), bnd.getRight()));
      }

      super.instantiateInner(engine, newCtx);
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new Let(this,
          this.bindings.stream()
              .map(bnd -> Pair.of(bnd.getLeft(), bnd.getRight().replaceSymbol(original, replacement))).toList(),
          this.body.replaceSymbol(original, replacement));
    }
  }

  public final class Return extends Expr {

    private Expr value;

    public Return(Expr value) {
      this.value = value;
    }

    public Return(Return other, Expr value) {
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
    public boolean equals(Object obj) {
      return obj instanceof Return other && this.value.equals(other.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.value);
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return new Return(this.value.replaceSymbol(original, replacement));
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
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      // TODO Auto-generated method stub
      throw new UnsupportedOperationException("Unimplemented method 'replaceSymbol'");
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.data, this.inferFn, this.instFn, this.getChildrenFn);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Custom<?> other &&
          this.data.equals(other.data) &&
          this.inferFn.equals(other.inferFn) &&
          this.instFn.equals(other.instFn) &&
          this.getChildrenFn.equals(other.getChildrenFn);
    }

  }

}

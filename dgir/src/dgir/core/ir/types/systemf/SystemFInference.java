package dgir.core.ir.types.systemf;

import dgir.core.ir.types.systemf.SystemFInference.Context.Break3Result;
import dgir.core.ir.types.systemf.SystemFInference.SystemFType.NumericType;
import dgir.core.ir.types.systemf.SystemFInference.TypeInference.CheckResult;
import dgir.core.ir.types.systemf.SystemFInference.TypeInference.SubtypeResult;
import dgir.core.ir.types.systemf.SystemFInference.TypeInference.TypeResult;
import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.GeneralFunctionType;
import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.InferenceTree;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeDialect;
import dgir.core.ir.types.TypeDialect.TypeInferenceSolver.ConversionContext;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;
import dgir.core.ir.types.TypeVar.TypeVarScope;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;
import dgir.core.ir.types.compatibility.Scope.ScopeLike;
import dgir.core.ir.types.compatibility.ConvertedOperationBuffer;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.compatibility.InferOrTransformResult;
import dgir.core.ir.types.compatibility.InferResultMarker;
import dgir.core.ir.types.compatibility.Scope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

public final class SystemFInference
    extends
    TypeDialect<InferOrTransformResult<SystemFInference.TypeInference.TypeResult, SystemFInference.Expr, SystemFInference.SystemFType>, ExprOrOperator<dgir.core.ir.types.systemf.SystemFInference.Expr>, dgir.core.ir.types.systemf.SystemFInference.Expr, dgir.core.ir.types.systemf.SystemFInference.SystemFType> {

  private static Optional<TypeInference> solver = Optional.empty();

  @Override
  public List<Class<? extends Type>> getAllowedTypes() {
    return TypeDialect.extractTypesFromAbstract(SystemFType.class);
  }

  @Override
  public List<Class<? extends Expression<SystemFType>>> getAllowedExpressions() {
    return TypeDialect.extractExpressionsFromAbstract(Expr.class);
  }

  @Override
  public TypeInferenceSolver<ExprOrOperator<Expr>, Expr, SystemFType> getSolverInstance() {
    if (SystemFInference.solver.isPresent()) {
      return SystemFInference.solver.get();
    } else {
      TypeInference solverInstance = null;
      var converterRegistry = ConverterRegistry.getConverterForDialect(SystemFInference.class);
      if (converterRegistry.isPresent()) {
        solverInstance = new TypeInference(converterRegistry.get());
      } else {
        solverInstance = new TypeInference();
      }
      SystemFInference.solver = Optional.of(solverInstance);
      return solverInstance;
    }
  }

  public abstract static sealed class SystemFType extends Type {

    public abstract boolean isMono();

    public abstract Set<TypeVar> freeVariables();

    public boolean occursCheck(TypeVar varName) {
      return this.freeVariables().contains(varName);
    }

    public abstract SystemFType substType(
        TypeVar tyVar,
        SystemFType replacement);

    public static final class Var extends SystemFType {

      public final TypeVar tyVar;

      public Var(TypeVar name) {
        this.tyVar = name;
      }

      @Override
      public String toString() {
        return tyVar.toString();
      }

      @Override
      public boolean equals(Object obj) {
        return obj instanceof Var other && this.tyVar.equals(other.tyVar);
      }

      @Override
      public int hashCode() {
        return super.hashCode();
      }

      @Override
      public boolean isMono() {
        return true;
      }

      @Override
      public Set<TypeVar> freeVariables() {
        return Set.of(this.tyVar);
      }

      @Override
      public SystemFType substType(TypeVar tyVar, SystemFType replacement) {
        if (this.tyVar.equals(tyVar)) {
          return replacement;
        } else {
          return this;
        }
      }
    }

    public static final class EtVar extends SystemFType {

      public final TypeVar tyVar;

      public EtVar(TypeVar tyVar) {
        this.tyVar = tyVar;
      }

      @Override
      public String toString() {
        return tyVar.toString();
      }

      @Override
      public boolean equals(Object obj) {
        return obj instanceof EtVar other && this.tyVar.equals(other.tyVar);
      }

      @Override
      public int hashCode() {
        return super.hashCode();
      }

      @Override
      public boolean isMono() {
        return true;
      }

      @Override
      public Set<TypeVar> freeVariables() {
        return Set.of(this.tyVar);
      }

      @Override
      public SystemFType substType(TypeVar tyVar, SystemFType replacement) {
        if (this.tyVar.equals(tyVar)) {
          return replacement;
        } else {
          return this;
        }
      }
    }

    public static final class Arrow extends SystemFType {

      public final SystemFType from;
      public final SystemFType to;

      public Arrow(SystemFType from, SystemFType to) {
        this.from = from;
        this.to = to;
      }

      @Override
      public String toString() {
        return from + " -> " + to;
      }

      @Override
      public boolean equals(Object obj) {
        return (obj instanceof Arrow other &&
            this.from.equals(other.from) &&
            this.to.equals(other.to));
      }

      @Override
      public int hashCode() {
        return super.hashCode();
      }

      @Override
      public boolean isMono() {
        return this.from.isMono() && this.to.isMono();
      }

      @Override
      public Set<TypeVar> freeVariables() {
        var fromVars = this.from.freeVariables();
        var toVars = this.to.freeVariables();
        var set = new HashSet<TypeVar>();
        set.addAll(fromVars);
        set.addAll(toVars);
        return Set.copyOf(set);
      }

      @Override
      public SystemFType substType(TypeVar tyVar, SystemFType replacement) {
        return new SystemFType.Arrow(
            this.from.substType(tyVar, replacement),
            this.to.substType(tyVar, replacement));
      }
    }

    public static final class ForAll extends SystemFType {

      public final TypeVar boundVar;
      public final SystemFType body;

      public ForAll(TypeVar name, SystemFType type) {
        this.boundVar = name;
        this.body = type;
      }

      @Override
      public String toString() {
        return "∀" + boundVar + ". " + body;
      }

      @Override
      public boolean equals(Object obj) {
        return (obj instanceof ForAll other &&
            this.boundVar.equals(other.boundVar) &&
            this.body.equals(other.body));
      }

      @Override
      public int hashCode() {
        return super.hashCode();
      }

      @Override
      public boolean isMono() {
        return false;
      }

      @Override
      public Set<TypeVar> freeVariables() {
        var set = new HashSet<TypeVar>();
        set.addAll(this.body.freeVariables());
        set.remove(this.boundVar);
        return Set.copyOf(set);
      }

      @Override
      public SystemFType substType(TypeVar tyVar, SystemFType replacement) {
        if (this.boundVar.equals(tyVar)) {
          // The type variable is shadowed by the ForAll binder
          return this;
        } else {
          return new SystemFType.ForAll(
              this.boundVar,
              this.body.substType(tyVar, replacement));
        }
      }
    }

    public static final class Lit extends SystemFType {
      public final TypeIdent ident;
      public final List<SystemFType> parameters;

      public Lit(TypeIdent ident, List<SystemFType> params) {
        this.ident = ident;
        this.parameters = params;
      }

      public Lit(TypeIdent ident) {
        this.ident = ident;
        this.parameters = List.of();
      }

      @Override
      public String toString() {
        return this.ident + (this.parameters.isEmpty() ? ""
            : "<" + this.parameters.stream().map(Object::toString).collect(Collectors.joining(", ")) + ">");
      }

      @Override
      public boolean equals(Object obj) {
        return obj instanceof Lit other && other.ident.equals(this.ident) && other.parameters.equals(this.parameters);
      }

      @Override
      public int hashCode() {
        return super.hashCode();
      }

      @Override
      public boolean isMono() {
        return true;
      }

      @Override
      public Set<TypeVar> freeVariables() {
        HashSet<TypeVar> freeVars = new HashSet<>();

        for (var param : parameters) {
          freeVars.addAll(param.freeVariables());
        }

        return Set.copyOf(freeVars);
      }

      @Override
      public SystemFType substType(TypeVar tyVar, SystemFType replacement) {
        return new Lit(this.ident, this.parameters.stream().map(param -> param.substType(tyVar, replacement)).toList());
      }
    }

    public static final class NumericType extends SystemFType {
      public long size;

      public NumericType(long size) {
        this.size = size;
      }

      @Override
      public boolean isMono() {
        return true;
      }

      @Override
      public Set<TypeVar> freeVariables() {
        return Set.of();
      }

      @Override
      public SystemFType substType(TypeVar tyVar, SystemFType replacement) {
        return new NumericType(this.size);
      }
    }

  }

  /**
   * Expressions that are valid for the SytemF Type System. All needed methods for
   * inference and type checking are implemented here
   */
  public static abstract class Expr implements Expression<SystemFType>, ExprOrOperator<Expr> {
    private Optional<SystemFType> inferredType;

    @Override
    public void setInferredType(SystemFType inferredType) {
      this.inferredType = Optional.ofNullable(inferredType);
    }

    @Override
    public Optional<SystemFType> getInferredType() {
      return this.inferredType;
    }

    @Override
    public void setSolver(SolutionRelayFunction<SystemFType> solutionRelay) {
    }

    public void resolveUsingContext(Expression.SolutionContext<SystemFType> ctx) {
      // TODO: resolve the types back to exprs
    };

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

    public abstract TypeInference.TypeResult infer(
        TypeInference engine,
        Context ctx);

    public TypeInference.CheckResult check(
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
        Break3Result breakRes = checkRes.ctx.break3(
            entry -> entry instanceof Entry.Mark m &&
                m.equals(mark));

        Context finalCtx = new Context(breakRes.left, checkRes.ctx);
        return new CheckResult(
            finalCtx,
            new InferenceTree(
                "ChkAll",
                input,
                "" + finalCtx,
                List.of(checkRes.tree)));
      }

      var inferred = engine.infer(ctx, this);
      var inferredApplied = inferred.ctx.apply(inferred.type);
      var typeApplied = inferred.ctx.apply(ty);
      var subtyped = engine.subtype(inferred.ctx, inferredApplied, typeApplied);
      return new CheckResult(
          subtyped.ctx,
          new InferenceTree(
              "ChkSub",
              input,
              "" + inferred.ctx,
              List.of(inferred.tree, subtyped.tree)));
    }

    public static final class Var extends Expr {

      private final Symbol name;

      public Var(Symbol name) {
        this.name = name;
      }

      @Override
      public final String toString() {
        return name + "";
      }

      @Override
      public TypeInference.TypeResult infer(TypeInference engine, Context ctx) {
        var input = ctx + " |- " + this;
        var boundVariable = ctx.find(
            entry -> entry instanceof Entry.VarBnd bnd && bnd.tmVar.equals(this.name));

        if (boundVariable.isPresent()) {
          var varBnd = (Entry.VarBnd) boundVariable.get();
          return new TypeInference.TypeResult(
              varBnd.type,
              ctx.copy(),
              new InferenceTree(
                  "InfVar",
                  ctx + " |- " + this,
                  input + " => " + varBnd.type + " -| " + ctx,
                  List.of()));
        }

        throw new TypingException.UnboundVariable(this.name);
      }
    }

    public static final class App extends Expr {

      private final ExprOrOperator<Expr> fun;
      private final ExprOrOperator<Expr> arg;

      public App(ExprOrOperator<Expr> fun, ExprOrOperator<Expr> arg) {
        this.fun = fun;
        this.arg = arg;
      }

      @Override
      public final String toString() {
        return fun + " " + arg;
      }

      @Override
      public TypeInference.TypeResult infer(TypeInference engine, Context ctx) {
        var input = ctx + " |- " + this;
        var funcInferred = engine.infer(ctx, this.fun);
        var funcTypeApplied = funcInferred.ctx.apply(funcInferred.type);

        TypeInference.TypeResult result;
        if (funcTypeApplied instanceof SystemFType.Arrow arrow) {
          var paramTy = arrow.from;
          var resultTy = arrow.to;

          var paramCheck = engine.check(funcInferred.ctx, this.arg, paramTy);
          result = new TypeInference.TypeResult(
              resultTy,
              paramCheck.ctx,
              new InferenceTree(
                  "InfAppArr",
                  input,
                  input + " =>=> " + resultTy + paramCheck.ctx,
                  List.of(paramCheck.tree)));
        } else if (funcTypeApplied instanceof SystemFType.EtVar etvar) {
          var a = etvar.tyVar;

          var a1 = new TypeVar();
          var a2 = new TypeVar();

          var breakRes = funcInferred.ctx.break3(
              entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar.equals(a));
          var arrowType = new SystemFType.Arrow(
              new SystemFType.EtVar(a1),
              new SystemFType.EtVar(a2));

          var newCtx = new Context(breakRes.left, funcInferred.ctx);
          newCtx.push(new Entry.SETVarBnd(a, arrowType));
          newCtx.push(new Entry.ETVarBnd(a1));
          newCtx.push(new Entry.ETVarBnd(a2));
          newCtx.extend(breakRes.right);

          var checkRes = engine.check(
              newCtx,
              this.arg,
              new SystemFType.EtVar(a1));

          var output = input + " =>=> ^" + a2 + " -| " + checkRes.ctx;
          result = new TypeInference.TypeResult(
              new SystemFType.EtVar(a2),
              checkRes.ctx,
              new InferenceTree(
                  "InfAppETVar",
                  input,
                  output,
                  List.of(checkRes.tree)));
        } else {
          throw new TypingException.ApplicationTypeError();
        }

        var output = input + " => " + result.type() + " -| " + result.ctx();
        return new TypeInference.TypeResult(
            result.type(),
            result.ctx(),
            new InferenceTree(
                "InfApp",
                input,
                output,
                List.of(funcInferred.tree, result.tree())));
      }
    }

    public static final class Abs extends Expr {

      private final Symbol name;
      private final SystemFType type;
      private final ExprOrOperator<Expr> body;

      public Abs(Symbol name, SystemFType type, ExprOrOperator<Expr> body) {
        this.name = name;
        this.type = type;
        this.body = body;
      }

      @Override
      public final String toString() {
        return "λ" + name + ": " + type + ". " + body;
      }

      @Override
      public TypeInference.TypeResult infer(TypeInference engine, Context ctx) {
        var input = ctx + " |- " + this;
        var b = new TypeVar();
        var newCtx = ctx.copy();
        Scope<SystemFType> scope = newCtx.addScope();

        var mark = new Entry.Mark();
        newCtx.push(mark);

        newCtx.push(new Entry.VarBnd(this.name, this.type));
        // TODO(jan): provide solution here to name == type
        newCtx.push(new Entry.ETVarBnd(b));

        var c1 = engine.check(newCtx, this.body, new SystemFType.EtVar(b));

        Context furtherCtx = c1.ctx.copy();
        SystemFType resultType = new SystemFType.EtVar(b);
        ArrayList<InferenceTree> trees = new ArrayList<>();

        for (var retType : scope.getAllReturnTypesInScope()) {
          SubtypeResult subTypeRes = engine.subtype(furtherCtx, retType, resultType);
          furtherCtx = subTypeRes.ctx;
          trees.add(subTypeRes.tree);
          resultType = furtherCtx.apply(resultType);
        }

        var breakRes = furtherCtx.break3(
            entry -> entry instanceof Entry.Mark m && m.equals(mark));

        var solvedFinalCtxEntries = new ArrayList<>(breakRes.left);
        solvedFinalCtxEntries.addAll(breakRes.right
            .stream()
            .filter(entry -> entry instanceof Entry.SETVarBnd)
            .collect(Collectors.toCollection(() -> new ArrayList<Entry>())));

        var finalCtx = new Context(solvedFinalCtxEntries, furtherCtx);
        var resType = new SystemFType.Arrow(
            this.type,
            resultType);

        return new TypeInference.TypeResult(
            resType,
            finalCtx,
            new InferenceTree(
                "InfLam",
                input,
                input + " => " + resType + " -| " + finalCtx,
                List.of(c1.tree)));
      }

      @Override
      public TypeInference.CheckResult check(
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
          var break3Result = bodyCheck.ctx.break3(
              entry -> entry instanceof Entry.Mark m && m.equals(mark));

          var finalCtx = new Context(break3Result.left, bodyCheck.ctx);

          return new TypeInference.CheckResult(
              finalCtx,
              new InferenceTree(
                  "ChkLam",
                  input,
                  "" + finalCtx,
                  List.of(bodyCheck.tree)));
        } else {
          return super.check(engine, ctx, ty);
        }
      }
    }

    public static final class TApp extends Expr {

      private final ExprOrOperator<Expr> func;
      private final SystemFType type;

      public TApp(ExprOrOperator<Expr> func, SystemFType type) {
        this.func = func;
        this.type = type;
      }

      @Override
      public final String toString() {
        return func + " " + type;
      }

      @Override
      public TypeInference.TypeResult infer(TypeInference engine, Context ctx) {
        var input = ctx + " |- " + this;
        var funcInferred = engine.infer(ctx, this.func);
        if (funcInferred.type instanceof SystemFType.ForAll forall) {
          var resultType = engine.substType(
              forall.boundVar,
              this.type,
              forall.body);
          var output = input + " => " + resultType + " -| " + funcInferred.ctx;
          return new TypeInference.TypeResult(
              resultType,
              funcInferred.ctx,
              new InferenceTree(
                  "InfTApp",
                  input,
                  output,
                  List.of(funcInferred.tree)));
        } else {
          throw new TypingException.ExpectedForAllType();
        }
      }
    }

    public static final class Ann extends Expr {

      private final ExprOrOperator<Expr> expr;
      private final SystemFType type;

      public Ann(ExprOrOperator<Expr> expr, SystemFType type) {
        this.expr = expr;
        this.type = type;
      }

      @Override
      public final String toString() {
        return expr + " : " + type;
      }

      @Override
      public TypeInference.TypeResult infer(TypeInference engine, Context ctx) {
        var input = ctx + " |- " + this;
        var checked = engine.check(ctx, this.expr, this.type);

        return new TypeInference.TypeResult(
            this.type,
            checked.ctx,
            new InferenceTree(
                "InfAnn",
                input,
                input + " => " + this.type + " -| " + checked.ctx,
                List.of(checked.tree)));
      }
    }

    public static final class TAbs extends Expr {

      private final TypeVar variable;
      private final ExprOrOperator<Expr> body;

      public TAbs(TypeVar variable, ExprOrOperator<Expr> body) {
        this.variable = variable;
        this.body = body;
      }

      @Override
      public final String toString() {
        return "∀" + variable + ". " + body;
      }

      @Override
      public TypeInference.TypeResult infer(TypeInference engine, Context ctx) {
        var input = ctx + " |- " + this;
        var newCtx = ctx.copy();
        var mark = new Entry.Mark();
        newCtx.push(mark);
        newCtx.push(new Entry.TVarBnd(this.variable));

        var bodyInferred = engine.infer(newCtx, this.body);

        var resolvedBodyType = bodyInferred.ctx.apply(bodyInferred.type);

        var break3Result = bodyInferred.ctx.break3(
            entry -> entry instanceof Entry.Mark m &&
                m.equals(mark));

        var solvedFinalCtxEntries = new ArrayList<>(break3Result.left);

        solvedFinalCtxEntries.addAll(break3Result.right
            .stream()
            .filter(entry -> entry instanceof Entry.SETVarBnd)
            .collect(Collectors.toCollection(() -> new ArrayList<Entry>())));
        var finalCtx = new Context(solvedFinalCtxEntries, bodyInferred.ctx);
        var resType = new SystemFType.ForAll(this.variable, resolvedBodyType);

        var output = input + " => " + resType + " -| " + finalCtx;

        return new TypeInference.TypeResult(
            resType,
            finalCtx,
            new InferenceTree(
                "InfTAbs",
                input,
                output,
                List.of(bodyInferred.tree)));
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
      public TypeInference.TypeResult infer(TypeInference engine, Context ctx) {
        var input = ctx + " |- " + this;
        var res = engine.generalNominalTypeToInferenceType(lit.toParameterizedNominalType(), Optional.of(ctx));
        return new TypeInference.TypeResult(
            res.getLeft(),
            ((Context) res.getRight().get()).copy(), // This is safe, as the ctx is provided as a Some(ctx)
            new InferenceTree(
                "InfLit" + res,
                input,
                input + " => " + res + " -| " + ctx,
                List.of()));
      }

      @Override
      public TypeInference.CheckResult check(
          TypeInference engine,
          Context ctx,
          SystemFType ty) {
        var input = ctx + " |- " + this + " <=" + ty;
        var thisResult = engine.generalNominalTypeToInferenceType(lit.toParameterizedNominalType(),
            Optional.empty());
        if (thisResult.getLeft().equals(ty)) {
          return new TypeInference.CheckResult(
              ctx.copy(),
              new InferenceTree("ChkLit" + thisResult.getLeft(), input, "" + ctx, List.of()));
        } else {
          return super.check(engine, ctx, ty);
        }
      }
    }

    public static final class Let extends Expr {

      private final List<Pair<Symbol, ExprOrOperator<Expr>>> bindings;
      private final ExprOrOperator<Expr> body;

      public Let(Symbol name, ExprOrOperator<Expr> value, ExprOrOperator<Expr> body) {
        this.bindings = List.of(Pair.of(name, value));
        this.body = body;
      }

      public Let(List<Pair<Symbol, ExprOrOperator<Expr>>> bindings, ExprOrOperator<Expr> body) {
        this.bindings = List.copyOf(bindings);
        this.body = body;
      }

      @Override
      public final String toString() {
        return "let (" + this.bindings.stream().map(Object::toString).collect(Collectors.joining(", ")) + ") in "
            + body;
      }

      @Override
      public TypeInference.TypeResult infer(TypeInference engine, Context ctx) {
        var input = ctx + " |- " + this;
        Context newCtx = ctx.copy();
        ArrayList<InferenceTree> trees = new ArrayList<>();
        ArrayList<Triple<Symbol, TypeVar, ExprOrOperator<Expr>>> nonUnified = new ArrayList<>(this.bindings.size());

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
          newCtx = valueInferred.ctx.copy();

          // TODO(jan): set name == type here!
          trees.add(valueInferred.tree);
        }

        var bodyInferred = engine.infer(newCtx, this.body);
        trees.add(bodyInferred.tree);

        var break3Result = bodyInferred.ctx.break3(
            entry -> entry instanceof Entry.Mark m && m.equals(mark));

        var solvedFinalCtxEntries = new ArrayList<>(break3Result.left);

        solvedFinalCtxEntries.addAll(break3Result.right
            .stream()
            .filter(entry -> entry instanceof Entry.SETVarBnd)
            .collect(Collectors.toCollection(() -> new ArrayList<Entry>())));
        var finalCtx = new Context(solvedFinalCtxEntries, bodyInferred.ctx);

        return new TypeInference.TypeResult(
            bodyInferred.type,
            finalCtx,
            new InferenceTree(
                "InfLet*",
                input,
                input + " => " + bodyInferred.type + " -| " + finalCtx,
                List.copyOf(trees)));
      }
    }

    public final class Return extends Expr {

      private ExprOrOperator<Expr> value;

      public Return(ExprOrOperator<Expr> value) {
        this.value = value;
      }

      @Override
      public TypeResult infer(TypeInference engine, Context ctx) {
        var input = ctx + " |- " + this;

        TypeResult res = engine.infer(ctx, this.value);
        SystemFType resultType = res.ctx().apply(res.type);

        var output = input + " => Bool -| " + res.ctx;
        return new TypeResult(resultType, res.ctx, new InferenceTree("InfRet", input, output, List.of(res.tree)));
      }

    }

    public final class Custom extends Expr {

      @FunctionalInterface
      public interface InferFunction {
        TypeResult infer(TypeInference engine, Context ctx, Object data);
      }

      @FunctionalInterface
      public interface CheckFunction {
        CheckResult check(TypeInference engine, Context ctx, SystemFType ty, Object data);
      }

      private Object data;
      private InferFunction inferFn;
      private Optional<CheckFunction> checkFn;

      public Custom(Object data, InferFunction inferFn) {
        this.data = data;
        this.inferFn = inferFn;
        this.checkFn = Optional.empty();
      }

      public Custom(Object data, InferFunction inferFn, CheckFunction checkFn) {
        this.data = data;
        this.inferFn = inferFn;
        this.checkFn = Optional.ofNullable(checkFn);
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

    }

  }

  /** Context entry for type checking and inference */
  public sealed interface Entry {
    public final record VarBnd(
        Symbol tmVar,
        SystemFType type) implements Entry {
      @Override
      public final String toString() {
        return tmVar + " : " + type;
      }
    }

    public final record TVarBnd(TypeVar tyVar) implements Entry {
      @Override
      public final String toString() {
        return tyVar.toString();
      }
    }

    public final record ETVarBnd(TypeVar tyVar) implements Entry {
      @Override
      public final String toString() {
        return tyVar.toString();
      }
    }

    public final record SETVarBnd(
        TypeVar tyVar,
        SystemFType type) implements Entry {
      @Override
      public final String toString() {
        return tyVar + " : " + type;
      }
    }

    /**
     * NOTE: this MUST be a class and not a record, as the equality to identify
     * marks is based on reference equality, as is default with class objects
     */
    public final class Mark implements Entry {
      @Override
      public final String toString() {
        return "MARK";
      }
    }
  }

  public static class Context extends ScopeLike<SystemFType>
      implements ConversionContext<Expr, SystemFType> {

    private List<Entry> entries;

    public Context() {
      super();
      this.entries = new ArrayList<>();
    }

    public Context(List<Entry> entries, Context other) {
      super(other);
      this.entries = new ArrayList<>(entries);
    }

    public Context(Context other) {
      super(other);
      this.entries = new ArrayList<>(other.entries);
    }

    @Override
    public String toString() {
      return ("{" +
          this.entries
              .stream()
              .map(Object::toString)
              .collect(Collectors.joining(", "))
          +
          "}");
    }

    public final record Break3Result(
        List<Entry> left,
        Optional<Entry> target,
        List<Entry> right) {
    }

    public void push(Entry entry) {
      this.entries.add(entry);
    }

    public void extend(Collection<? extends Entry> entries) {
      this.entries.addAll(entries);
    }

    public Optional<Entry> find(Predicate<? super Entry> filterFunc) {
      return this.entries.stream().filter(filterFunc).findFirst();
    }

    public Context copy() {
      return new Context(this);
    }

    /**
     * break the context into possibly three parts, if the predicate was found to be
     * in the context.
     *
     * @param pred the predicate to find an entry in the context
     * @return the three parts broken up into left half (up to, but excluding, the
     *         found entry), the entry for which the pred is true, and the right
     *         half (everything after the found entry, exlucing the entry)
     */
    public Break3Result break3(Predicate<? super Entry> pred) {
      var position = IntStream.range(0, this.entries.size())
          .filter(i -> pred.test(this.entries.get(i)))
          .findFirst();

      if (position.isPresent()) {
        var firstPart = this.entries.subList(0, position.getAsInt());
        var target = this.entries.get(position.getAsInt());
        var secondPart = this.entries.subList(
            position.getAsInt() + 1,
            this.entries.size());
        return new Break3Result(
            List.copyOf(firstPart),
            Optional.of(target),
            secondPart);
      } else {
        return new Break3Result(
            List.copyOf(this.entries.subList(0, this.entries.size())),
            Optional.empty(),
            List.of());
      }
    }

    public static Context fromParts(
        List<Entry> left,
        Entry middle,
        List<Entry> right,
        Context oldContext) {
      var list = new ArrayList<Entry>();
      list.addAll(left);
      list.add(middle);
      list.addAll(right);

      return new Context(list, oldContext);
    }

    public SystemFType applyOnce(SystemFType type) {
      if (type instanceof SystemFType.EtVar etVar) {
        var filterRes = this.find(
            entry -> entry instanceof Entry.SETVarBnd bnd &&
                bnd.tyVar.equals(etVar.tyVar));
        if (filterRes.isPresent()) {
          var solvedEtVar = (Entry.SETVarBnd) filterRes.get();
          solvedEtVar.tyVar.provideSolution(solvedEtVar.type);
          return this.applyOnce(solvedEtVar.type);
        } else {
          return type;
        }
      } else if (type instanceof SystemFType.Arrow arrow) {
        return new SystemFType.Arrow(
            this.applyOnce(arrow.from),
            this.applyOnce(arrow.to));
      } else if (type instanceof SystemFType.ForAll forAll) {
        return new SystemFType.ForAll(
            forAll.boundVar,
            this.applyOnce(forAll.body));
      }
      return type;
    }

    public SystemFType apply(SystemFType type) {
      var current = type;
      var changed = true;

      while (changed) {
        changed = false;
        var newType = this.applyOnce(current);
        if (!newType.equals(current)) {
          current = newType;
          changed = true;
        }
      }

      return current;
    }

    /**
     * Test wheather type Variable A appears before type Variable B in the context.
     * In this case, appearing before means that type Variable A appears later in
     * the context
     */
    public boolean before(TypeVar tyVarA, TypeVar tyVarB) {
      var posA = IntStream.range(0, this.entries.size())
          .filter(
              i -> this.entries.get(i) instanceof Entry.ETVarBnd bnd &&
                  bnd.tyVar.equals(tyVarA))
          .findFirst();

      var posB = IntStream.range(0, this.entries.size())
          .filter(
              i -> this.entries.get(i) instanceof Entry.ETVarBnd bnd &&
                  bnd.tyVar.equals(tyVarB))
          .findFirst();

      if (posA.isPresent() && posB.isPresent()) {
        return posA.getAsInt() > posB.getAsInt();
      }
      return false;
    }
  }

  public static final class TypeInference
      extends TypeDialect.TypeInferenceSolver<ExprOrOperator<Expr>, Expr, SystemFType> {

    private ConvertedOperationBuffer<Expr, SystemFType> operationToExprBuffer;

    public TypeInference() {
      this(new TypeDialectConverterRegistry());
    }

    public TypeInference(TypeDialectConverterRegistry registry) {
      super(registry);
      operationToExprBuffer = new ConvertedOperationBuffer<>();
    }

    private static SystemFType convertInnerGeneralParameterized(GeneralParameterizedNominalType type) {
      List<SystemFType> paramTypes = type.getTypedParameters().stream().map(param -> switch (param) {
        case GeneralTypeParameter.Concrete con -> convertInnerGeneralParameterized(con.ty());
        case GeneralTypeParameter.Unknown unk -> new SystemFType.EtVar(new TypeVar());
        case GeneralTypeParameter.Numeric num -> new SystemFType.NumericType(num.number());
      }).toList();

      return new SystemFType.Lit(type.getIdent(), paramTypes);
    }

    @Override
    public Pair<SystemFType, Optional<ConversionContext<Expr, SystemFType>>> generalNominalTypeToInferenceType(
        GeneralParameterizedNominalType type,
        Optional<ConversionContext<Expr, SystemFType>> ctx) {

      var mappedCtx = ctx.map(c -> {
        if (!(c instanceof Context)) {
          throw new IllegalArgumentException("ctx must be of instance Context");
        }

        return (Context) c;
      });

      try (TypeVarScope scope = TypeVar.addScope()) {
        var resultType = convertInnerGeneralParameterized(type);
        var newCtx = mappedCtx.map(c -> {
          var newC = c.copy();
          for (var etvar : scope.createdVars()) {
            newC.push(new Entry.ETVarBnd(etvar));
          }

          return newC;
        });

        return Pair.of(resultType, newCtx.map(c -> (ConversionContext<Expr, SystemFType>) c));
      } catch (Exception e) {
        throw new IllegalArgumentException("This will never happen!");
      }
    }

    @Override
    public Expr generalBlockToInferenceExpr(GeneralBlock block) {
      ArrayList<Pair<Symbol, ExprOrOperator<Expr>>> bindings = new ArrayList<>();
      Optional<Symbol> lastValue = Optional.empty();

      for (var op : block.getOperations()) {
        var opOutput = op.getOutput();
        if (opOutput.isPresent()) {
          bindings.add(Pair.of(Symbol.of(opOutput.get().getValue()), ExprOrOperator.of(op)));
          lastValue = Optional.of(Symbol.of(opOutput.get().getValue()));
        } else {
          /*
           * NOTE: handle everything as a returnable value, even though something like a
           * function is not actually a expression! This is done to correctly typecheck
           * each function and their parameters!
           */
          var val = Symbol.of(new Value());
          bindings.add(Pair.of(val, ExprOrOperator.of(op)));
          lastValue = Optional.of(val);
        }
      }

      if (lastValue.isPresent()) {
        return new Expr.Let(bindings, new Expr.Var(lastValue.get()));
      } else {
        return new Expr.Let(bindings, new Expr.LitExpr(new Literal.Unit()));
      }
    }

    @Override
    public Pair<Symbol, Expr> generalFunctionToInferenceExpr(GeneralFunctionType fn) {

      Expr abs = this.generalBlockToInferenceExpr(fn.body);

      for (var param : fn.parameters.reversed()) {
        if (!param.getRight().isPresent() || !(param.getRight().get() instanceof SystemFType)) {
          throw new IllegalArgumentException(
              "param type must be specified for " + param.getLeft() + " and of instance SystemFType");
        }

        abs = new Expr.Abs(param.getLeft(), (SystemFType) param.getRight().get(), abs);
      }

      return Pair.of(Symbol.of(fn.name), abs);
    }

    @Override
    public Type solve(ExprOrOperator<Expr> expr) {
      if (expr.isExpr()) {
        var res = this.infer(new Context(), expr);
        return (Type) res.ctx.apply(res.type);
      } else {
        throw new TypingException.UnsupportedExpression(
            TypingException.UnsupportedExpression.AlgorithmType.SystemF,
            expr);
      }
    }

    SystemFType substType(
        TypeVar tyVar,
        SystemFType replacement,
        SystemFType target) {
      return target.substType(tyVar, replacement);
    }

    public final record TypeResult(
        SystemFType type,
        Context ctx,
        InferenceTree tree) implements InferResultMarker<SystemFType> {
    }

    TypeResult infer(Context ctx, ExprOrOperator<Expr> expr) {
      if (expr.isExpr()) {
        return expr.getExpr().infer(this, ctx);
      } else if (expr.isOperator()) {
        Operation op = expr.getOp();
        return this.operationToExprBuffer.operationToExpr(this, op, this.registry, Expr.class).infer(this, ctx);
      }
      throw new RuntimeException("unimplemented for OPs");
    }

    public final record CheckResult(Context ctx, InferenceTree tree) {
    }

    public CheckResult check(Context ctx, ExprOrOperator<Expr> exprParam, SystemFType ty) {
      Expr expr = null;
      if (exprParam.isExpr()) {
        expr = exprParam.getExpr();
      } else if (exprParam.isOperator()) {
        expr = this.operationToExprBuffer.operationToExpr(this, exprParam.getOp(), this.registry, Expr.class);
      } else {
        throw new RuntimeException("Can never happen, due to exhaustive If");
      }

      var input = ctx + " |- " + expr + " <=" + ty;

      if (ty instanceof SystemFType.ForAll forall) {
        var newCtx = ctx.copy();
        newCtx.push(new Entry.TVarBnd(forall.boundVar));
        var bodyCheck = this.check(newCtx, expr, forall.body);

        var break3Result = bodyCheck.ctx.break3(
            entry -> entry instanceof Entry.TVarBnd bnd &&
                bnd.tyVar.equals(forall.boundVar));

        var finalCtx = new Context(break3Result.left, bodyCheck.ctx);

        return new CheckResult(
            finalCtx,
            new InferenceTree(
                "ChkAll",
                input,
                "" + finalCtx,
                List.of(bodyCheck.tree)));
      } else {
        return expr.check(this, ctx, ty);
      }
    }

    public final record SubtypeResult(Context ctx, InferenceTree tree) {
    }

    public SubtypeResult subtype(
        Context ctx,
        SystemFType ty1,
        SystemFType ty2) {
      var input = ctx + " |- " + ty1 + " <: " + ty2;

      if (ty1 instanceof SystemFType.Lit lit1 && ty2 instanceof SystemFType.Lit lit2) {
        if (lit1.parameters.size() != lit2.parameters.size()) {
          throw new TypingException.SubtypingFailed(ty1, ty2);
        } else if (!lit1.ident.equals(lit2.ident)) {
          throw new TypingException.SubtypingFailed(ty1, ty2);
        }
        var context = ctx.copy();
        for (int i = 0; i < lit1.parameters.size(); i++) {
          var param1 = lit1.parameters.get(i);
          var param2 = lit2.parameters.get(i);
          var subRes = this.subtype(context, param1, param2);
          context = subRes.ctx;
        }
        if (lit1.ident.equals(lit2.ident)) {

          return new SubtypeResult(
              ctx.copy(),
              new InferenceTree("SubRefl", input, "" + ctx, List.of()));
        } else {
          throw new RuntimeException("Error");
        }
      } else if (ty1 instanceof NumericType n1 && ty2 instanceof NumericType n2) {
        if (n1.size != n2.size) {
          throw new TypingException.SubtypingFailed(ty1, ty2);
        }
        return new SubtypeResult(ctx.copy(), new InferenceTree("SubReflNum", input, "" + ctx, List.of()));
      } else if (ty1 instanceof SystemFType.Var v1 &&
          ty2 instanceof SystemFType.Var v2 &&
          v1.tyVar.equals(v2.tyVar)) {
        return new SubtypeResult(
            ctx.copy(),
            new InferenceTree("SubReflTVar", input, "" + ctx, List.of()));
      } else if (ty1 instanceof SystemFType.EtVar v1 &&
          ty2 instanceof SystemFType.EtVar v2 &&
          v1.tyVar.equals(v2.tyVar)) {
        return new SubtypeResult(
            ctx.copy(),
            new InferenceTree("SubReflETVar", input, "" + ctx, List.of()));
      } else if (ty1 instanceof SystemFType.Arrow a1 &&
          ty2 instanceof SystemFType.Arrow a2) {
        var covArg = this.subtype(ctx, a1.from, a2.from);
        var covRes = this.subtype(covArg.ctx, a1.to, a2.to);

        return new SubtypeResult(
            covRes.ctx,
            new InferenceTree(
                "SubArr",
                input,
                "" + covRes.ctx,
                List.of(covArg.tree, covRes.tree)));
      } else if (ty2 instanceof SystemFType.ForAll forall) {
        Context newCtx = ctx.copy();
        newCtx.push(new Entry.TVarBnd(forall.boundVar));
        SubtypeResult subtypeRes = this.subtype(newCtx, ty1, forall.body);
        Break3Result breakRes = subtypeRes.ctx.break3(
            entry -> entry instanceof Entry.TVarBnd bnd &&
                bnd.tyVar.equals(forall.boundVar));
        Context finalCtx = new Context(breakRes.left, subtypeRes.ctx);

        return new SubtypeResult(
            finalCtx,
            new InferenceTree(
                "SubAllR",
                input,
                "" + finalCtx,
                List.of(subtypeRes.tree)));
      } else if (ty1 instanceof SystemFType.ForAll forall) {
        var substT1 = this.substType(
            forall.boundVar,
            new SystemFType.EtVar(forall.boundVar),
            forall.body);

        var newCtx = ctx.copy();
        newCtx.push(new Entry.ETVarBnd(forall.boundVar));
        var mark = new Entry.Mark();
        newCtx.push(mark);

        var subtypeRes = this.subtype(newCtx, substT1, ty2);
        var breakRes = subtypeRes.ctx.break3(
            entry -> entry instanceof Entry.Mark m && m.equals(mark));
        var finalCtx = new Context(breakRes.left, subtypeRes.ctx);
        return new SubtypeResult(
            finalCtx,
            new InferenceTree(
                "SubAllL",
                input,
                "" + finalCtx,
                List.of(subtypeRes.tree)));
      } else if (ty1 instanceof SystemFType.EtVar etvar && !ty2.occursCheck(etvar.tyVar)) {
        var instLRes = this.instL(ctx, etvar.tyVar, ty2);
        var output = "" + instLRes.ctx;
        return new SubtypeResult(
            instLRes.ctx,
            new InferenceTree("SubInstL", input, output, List.of(instLRes.tree)));
      } else if (ty2 instanceof SystemFType.EtVar etvar && !ty1.occursCheck(etvar.tyVar)) {
        var instRRes = this.instR(ctx, ty1, etvar.tyVar);
        var output = "" + instRRes.ctx;
        return new SubtypeResult(
            instRRes.ctx,
            new InferenceTree("SubInstR", input, output, List.of(instRRes.tree)));
      } else {
        throw new TypingException.SubtypingFailed(ty1, ty2);
      }
    }

    final record InstResult(Context ctx, InferenceTree tree) {
    }

    InstResult instL(Context ctx, TypeVar a, SystemFType ty) {
      var input = ctx + " |- ^" + a + " :=< " + ty;

      if (ty instanceof SystemFType.EtVar etvar && ctx.before(a, etvar.tyVar)) {
        var breakRes = ctx.break3(
            entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar.equals(etvar.tyVar));
        var newCtx = Context.fromParts(
            breakRes.left,
            new Entry.SETVarBnd(etvar.tyVar, new SystemFType.EtVar(a)),
            breakRes.right, ctx);
        return new InstResult(
            newCtx,
            new InferenceTree("InstLReach", input, "" + newCtx, List.of()));
      } else if (ty instanceof SystemFType.Lit lit) {
        try (var scope = TypeVar.addScope()) {
          var litType = new SystemFType.Lit(lit.ident,
              lit.parameters.stream().map(p -> (SystemFType) new SystemFType.EtVar(new TypeVar())).toList());

          List<TypeVar> existentials = scope.createdVars();

          var breakRes = ctx.break3(entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar.equals(a));

          Context newCtx = new Context(breakRes.left, ctx);
          newCtx.push(new Entry.SETVarBnd(a, litType));
          for (var ext : existentials) {
            newCtx.push(new Entry.ETVarBnd(ext));
          }
          newCtx.extend(breakRes.right);

          ArrayList<InferenceTree> trees = new ArrayList<>();

          for (int i = 0; i < existentials.size(); i++) {
            var ext = existentials.get(i);
            var param = lit.parameters.get(i);
            var paramApplied = newCtx.apply(param);
            var instLRes = this.instL(newCtx, ext, paramApplied);
            newCtx = instLRes.ctx;
            trees.add(instLRes.tree);
          }

          return new InstResult(newCtx, new InferenceTree("InstLLit", input, "" + newCtx, List.copyOf(trees)));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      } else if (ty instanceof SystemFType.Arrow arrow) {
        var a1 = new TypeVar();
        var a2 = new TypeVar();
        var breakRes = ctx.break3(
            entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar.equals(a));
        var arrowType = new SystemFType.Arrow(
            new SystemFType.EtVar(a1),
            new SystemFType.EtVar(a2));

        var newCtx = new Context(breakRes.left, ctx);
        newCtx.push(new Entry.SETVarBnd(a, arrowType));
        newCtx.push(new Entry.ETVarBnd(a1));
        newCtx.push(new Entry.ETVarBnd(a2));
        newCtx.extend(breakRes.right);

        var instRRes = this.instR(newCtx, arrow.from, a1);
        var t2Applied = instRRes.ctx.apply(arrow.to);
        var instLRes = this.instL(instRRes.ctx, a2, t2Applied);

        return new InstResult(
            instLRes.ctx,
            new InferenceTree(
                "InstLArr",
                input,
                "" + instLRes.ctx,
                List.of(instRRes.tree, instLRes.tree)));
      } else if (ty instanceof SystemFType.ForAll forall) {
        var newCtx = ctx.copy();
        newCtx.push(new Entry.TVarBnd(forall.boundVar));
        var instLRes = this.instL(newCtx, a, forall.body);
        var breakRes = instLRes.ctx.break3(
            entry -> entry instanceof Entry.TVarBnd bnd &&
                bnd.tyVar.equals(forall.boundVar));
        var finalCtx = new Context(breakRes.left, instLRes.ctx);
        return new InstResult(
            finalCtx,
            new InferenceTree(
                "InstLAllR",
                input,
                "" + finalCtx,
                List.of(instLRes.tree)));
      } else if (ty.isMono()) {
        if (ty.occursCheck(a)) {
          throw new TypingException.OccursCheckFailed(ty, a);
        }

        var breakRes = ctx.break3(
            entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar.equals(a));
        var newCtx = Context.fromParts(
            breakRes.left,
            new Entry.SETVarBnd(a, ty),
            breakRes.right, ctx);
        return new InstResult(
            newCtx,
            new InferenceTree("InstLSolve", input, "" + newCtx, List.of()));
      } else {
        throw new TypingException.InstantiationError(
            "InstL Instantiation error " + ctx + " |- " + ty);
      }
    }

    InstResult instR(Context ctx, SystemFType ty, TypeVar a) {
      var input = ctx + " |- " + ty + " :=< ^" + a;
      if (ty instanceof SystemFType.EtVar etvar) {
        var breakRes = ctx.break3(
            entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar.equals(etvar.tyVar));
        var newCtx = Context.fromParts(
            breakRes.left,
            new Entry.SETVarBnd(etvar.tyVar, new SystemFType.EtVar(a)),
            breakRes.right, ctx);

        return new InstResult(
            newCtx,
            new InferenceTree("InstRReach", input, "" + newCtx, List.of()));
      } else if (ty instanceof SystemFType.Lit lit) {
        try (var scope = TypeVar.addScope()) {
          var litType = new SystemFType.Lit(lit.ident,
              lit.parameters.stream().map(p -> (SystemFType) new SystemFType.EtVar(new TypeVar())).toList());

          List<TypeVar> existentials = scope.createdVars();

          var breakRes = ctx.break3(entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar.equals(a));

          Context newCtx = new Context(breakRes.left, ctx);
          newCtx.push(new Entry.SETVarBnd(a, litType));
          for (var ext : existentials) {
            newCtx.push(new Entry.ETVarBnd(ext));
          }
          newCtx.extend(breakRes.right);

          ArrayList<InferenceTree> trees = new ArrayList<>();

          for (int i = 0; i < existentials.size(); i++) {
            var ext = existentials.get(i);
            var param = lit.parameters.get(i);
            var paramApplied = newCtx.apply(param);
            var instRRes = this.instR(newCtx, paramApplied, ext);
            newCtx = instRRes.ctx;
            trees.add(instRRes.tree);
          }

          return new InstResult(newCtx, new InferenceTree("InstRLit", input, "" + newCtx, List.copyOf(trees)));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }

      } else if (ty instanceof SystemFType.Arrow arrow) {
        var a1 = new TypeVar();
        var a2 = new TypeVar();
        var breakRes = ctx.break3(
            entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar.equals(a));
        var arrowType = new SystemFType.Arrow(
            new SystemFType.EtVar(a1),
            new SystemFType.EtVar(a2));

        var newCtx = new Context(breakRes.left, ctx);
        newCtx.push(new Entry.SETVarBnd(a, arrowType));
        newCtx.push(new Entry.ETVarBnd(a1));
        newCtx.push(new Entry.ETVarBnd(a2));
        newCtx.extend(breakRes.right);

        var instLRes = this.instL(newCtx, a1, arrow.from);
        var t2Applied = instLRes.ctx.apply(arrow.to);
        var instRRes = this.instR(instLRes.ctx, t2Applied, a2);

        return new InstResult(
            instRRes.ctx,
            new InferenceTree(
                "InstRArr",
                input,
                "" + instLRes.ctx,
                List.of(instRRes.tree, instLRes.tree)));
      } else if (ty instanceof SystemFType.ForAll forall) {
        var substT = this.substType(
            forall.boundVar,
            new SystemFType.EtVar(forall.boundVar),
            forall.body);

        var newCtx = ctx.copy();
        newCtx.push(new Entry.ETVarBnd(forall.boundVar));
        var mark = new Entry.Mark();
        newCtx.push(mark);

        var instRRes = this.instR(newCtx, substT, a);
        var breakRes = instRRes.ctx.break3(
            entry -> entry instanceof Entry.Mark m && m.equals(mark));
        var finalCtx = new Context(breakRes.left, instRRes.ctx);
        return new InstResult(
            finalCtx,
            new InferenceTree(
                "InstRAllL",
                input,
                "" + instRRes.ctx,
                List.of(instRRes.tree)));
      } else if (ty.isMono()) {
        if (ty.occursCheck(a)) {
          throw new TypingException.OccursCheckFailed(ty, a);
        }

        var breakRes = ctx.break3(
            entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar.equals(a));
        var newCtx = Context.fromParts(
            breakRes.left,
            new Entry.SETVarBnd(a, ty),
            breakRes.right, ctx);
        return new InstResult(
            newCtx,
            new InferenceTree("InstRSolve", input, "" + ctx, List.of()));
      } else {
        throw new TypingException.InstantiationError(
            "InstR Instantiation error " + ctx + " |- " + ty);
      }
    }

  }
}

package dgir.core.ir.types;

import dgir.core.ir.types.SystemFInference.Context.Break3Result;
import dgir.core.ir.types.SystemFInference.TypeInference.CheckResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class SystemFInference extends TypeDialect {

  private static Optional<TypeInference> solver = Optional.empty();

  @Override
  public List<Class<? extends Type>> getAllowedTypes() {
    return TypeDialect.extractTypesFromAbstract(SystemFType.class);
  }

  @Override
  public List<Class<? extends Expression>> getAllowedExpressions() {
    return TypeDialect.extractExpressionsFromAbstract(Expr.class);
  }

  @Override
  public TypeInferenceSolver getSolverInstance() {
    if (solver.isPresent()) {
      return solver.get();
    } else {
      solver = Optional.of(new TypeInference());
      return solver.get();
    }
  }

  public abstract static sealed class SystemFType extends Type {

    public abstract boolean isMono();

    public abstract Set<String> freeVariables();

    public boolean occursCheck(String varName) {
      return this.freeVariables().contains(varName);
    }

    public abstract SystemFType substType(
      String tyVar,
      SystemFType replacement
    );

    public static final class Var extends SystemFType {

      public final String name;

      public Var(String name) {
        this.name = name;
      }

      @Override
      public String toString() {
        return name;
      }

      @Override
      public boolean equals(Object obj) {
        return obj instanceof Var other && this.name.equals(other.name);
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
      public Set<String> freeVariables() {
        return Set.of(this.name);
      }

      @Override
      public SystemFType substType(String tyVar, SystemFType replacement) {
        if (this.name.equals(tyVar)) {
          return replacement;
        } else {
          return this;
        }
      }
    }

    public static final class EtVar extends SystemFType {

      public final String name;

      public EtVar(String name) {
        this.name = name;
      }

      @Override
      public String toString() {
        return name;
      }

      @Override
      public boolean equals(Object obj) {
        return obj instanceof EtVar other && this.name.equals(other.name);
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
      public Set<String> freeVariables() {
        return Set.of(this.name);
      }

      @Override
      public SystemFType substType(String tyVar, SystemFType replacement) {
        if (this.name.equals(tyVar)) {
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
        return (
          obj instanceof Arrow other &&
          this.from.equals(other.from) &&
          this.to.equals(other.to)
        );
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
      public Set<String> freeVariables() {
        var fromVars = this.from.freeVariables();
        var toVars = this.to.freeVariables();
        var set = new HashSet<String>();
        set.addAll(fromVars);
        set.addAll(toVars);
        return Set.copyOf(set);
      }

      @Override
      public SystemFType substType(String tyVar, SystemFType replacement) {
        return new SystemFType.Arrow(
          this.from.substType(tyVar, replacement),
          this.to.substType(tyVar, replacement)
        );
      }
    }

    public static final class ForAll extends SystemFType {

      public final String boundVar;
      public final SystemFType body;

      public ForAll(String name, SystemFType type) {
        this.boundVar = name;
        this.body = type;
      }

      @Override
      public String toString() {
        return "∀" + boundVar + ". " + body;
      }

      @Override
      public boolean equals(Object obj) {
        return (
          obj instanceof ForAll other &&
          this.boundVar.equals(other.boundVar) &&
          this.body.equals(other.body)
        );
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
      public Set<String> freeVariables() {
        var set = new HashSet<String>();
        set.addAll(this.body.freeVariables());
        set.remove(this.boundVar);
        return Set.copyOf(set);
      }

      @Override
      public SystemFType substType(String tyVar, SystemFType replacement) {
        if (this.boundVar.equals(tyVar)) {
          // The type variable is shadowed by the ForAll binder
          return this;
        } else {
          return new SystemFType.ForAll(
            this.boundVar,
            this.body.substType(tyVar, replacement)
          );
        }
      }
    }

    public static final class Int extends SystemFType {

      @Override
      public String toString() {
        return "Int";
      }

      @Override
      public boolean equals(Object obj) {
        return obj instanceof Int;
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
      public Set<String> freeVariables() {
        return Set.of();
      }

      @Override
      public SystemFType substType(String tyVar, SystemFType replacement) {
        return this;
      }
    }

    public static final class Bool extends SystemFType {

      @Override
      public String toString() {
        return "Bool";
      }

      @Override
      public boolean equals(Object obj) {
        return obj instanceof Bool;
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
      public Set<String> freeVariables() {
        return Set.of();
      }

      @Override
      public SystemFType substType(String tyVar, SystemFType replacement) {
        return this;
      }
    }
  }

  public abstract static sealed class Expr extends Expression {

    public abstract TypeInference.TypeResult infer(
      TypeInference engine,
      Context ctx
    );

    public TypeInference.CheckResult check(
      TypeInference engine,
      Context ctx,
      SystemFType ty
    ) {
      var input = ctx + " |- " + this + " <=" + ty;
      if (ty instanceof SystemFType.ForAll forall) {
        var newCtx = ctx.copy();
        newCtx.push(new Entry.TVarBnd(forall.boundVar));
        CheckResult checkRes = engine.check(newCtx, this, forall.body);
        Break3Result breakRes = checkRes.ctx.break3(
          entry ->
            entry instanceof Entry.TVarBnd bnd &&
            bnd.tyVar.equals(forall.boundVar)
        );
        Context finalCtx = new Context(breakRes.right);
        return new CheckResult(
          finalCtx,
          new InferenceTree(
            "ChkAll",
            input,
            "" + finalCtx,
            List.of(checkRes.tree)
          )
        );
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
          List.of(inferred.tree, subtyped.tree)
        )
      );
    }

    public static final class Var extends Expr {

      public final String name;

      public Var(String name) {
        this.name = name;
      }

      @Override
      public final String toString() {
        return name;
      }

      @Override
      public TypeInference.TypeResult infer(TypeInference engine, Context ctx) {
        var input = ctx + " |- " + this;
        var boundVariable = ctx.find(
          entry ->
            entry instanceof Entry.VarBnd bnd && bnd.tmVar.equals(this.name)
        );

        if (boundVariable.isPresent()) {
          var varBnd = (Entry.VarBnd) boundVariable.get();
          return new TypeInference.TypeResult(
            varBnd.type,
            ctx.copy(),
            new InferenceTree(
              "InfVar",
              ctx + " |- " + this,
              input + " => " + varBnd.type + " -| " + ctx,
              List.of()
            )
          );
        }

        throw new TypingException.UnboundVariable(this.name);
      }
    }

    public static final class App extends Expr {

      public final Expr fun;
      public final Expr arg;

      public App(Expr fun, Expr arg) {
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
              List.of(paramCheck.tree)
            )
          );
        } else if (funcTypeApplied instanceof SystemFType.EtVar etvar) {
          var a = etvar.name;

          var a1 = engine.freshTypeVar();
          var a2 = engine.freshTypeVar();

          var breakRes = funcInferred.ctx.break3(
            entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar.equals(a)
          );
          var arrowType = new SystemFType.Arrow(
            new SystemFType.EtVar(a1),
            new SystemFType.EtVar(a2)
          );

          var newCtx = new Context(breakRes.left);
          newCtx.push(new Entry.SETVarBnd(a, arrowType));
          newCtx.push(new Entry.ETVarBnd(a1));
          newCtx.push(new Entry.ETVarBnd(a2));
          newCtx.extend(breakRes.right);

          var checkRes = engine.check(
            newCtx,
            this.arg,
            new SystemFType.EtVar(a1)
          );

          var output = input + " =>=> ^" + a2 + " -| " + checkRes.ctx;
          result = new TypeInference.TypeResult(
            new SystemFType.EtVar(a2),
            checkRes.ctx,
            new InferenceTree(
              "InfAppETVar",
              input,
              output,
              List.of(checkRes.tree)
            )
          );
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
            List.of(funcInferred.tree, result.tree())
          )
        );
      }
    }

    public static final class Abs extends Expr {

      public final String name;
      public final SystemFType type;
      public final Expr body;

      public Abs(String name, SystemFType type, Expr body) {
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
        var b = engine.freshTypeVar();
        var newCtx = ctx.copy();

        newCtx.push(new Entry.VarBnd(this.name, this.type));
        newCtx.push(new Entry.ETVarBnd(b));

        var c1 = engine.check(newCtx, this.body, new SystemFType.EtVar(b));
        var breakRes = c1.ctx.break3(
          entry ->
            entry instanceof Entry.VarBnd bnd && bnd.tmVar.equals(this.name)
        );

        var solvedFinalCtxEntries = breakRes.left
          .stream()
          .filter(entry -> entry instanceof Entry.SETVarBnd)
          .collect(Collectors.toCollection(() -> new ArrayList<Entry>()));

        solvedFinalCtxEntries.addAll(breakRes.right);
        var finalCtx = new Context(solvedFinalCtxEntries);
        var resType = new SystemFType.Arrow(
          this.type,
          new SystemFType.EtVar(b)
        );

        return new TypeInference.TypeResult(
          resType,
          finalCtx,
          new InferenceTree(
            "InfLam",
            input,
            input + " => " + resType + " -| " + finalCtx,
            List.of(c1.tree)
          )
        );
      }

      @Override
      public TypeInference.CheckResult check(
        TypeInference engine,
        Context ctx,
        SystemFType ty
      ) {
        var input = ctx + " |- " + this + " <=" + ty;
        if (ty instanceof SystemFType.Arrow arrow) {
          var newCtx = ctx.copy();
          newCtx.push(new Entry.VarBnd(this.name, arrow.from));

          var bodyCheck = engine.check(newCtx, this.body, arrow.to);
          var break3Result = bodyCheck.ctx.break3(
            entry ->
              entry instanceof Entry.VarBnd bnd && bnd.tmVar.equals(this.name)
          );

          var finalCtx = new Context(break3Result.right);

          return new TypeInference.CheckResult(
            finalCtx,
            new InferenceTree(
              "ChkLam",
              input,
              "" + finalCtx,
              List.of(bodyCheck.tree)
            )
          );
        } else {
          return super.check(engine, ctx, ty);
        }
      }
    }

    public static final class TApp extends Expr {

      public final Expr func;
      public final SystemFType type;

      public TApp(Expr func, SystemFType type) {
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
            forall.body
          );
          var output = input + " => " + resultType + " -| " + funcInferred.ctx;
          return new TypeInference.TypeResult(
            resultType,
            funcInferred.ctx,
            new InferenceTree(
              "InfTApp",
              input,
              output,
              List.of(funcInferred.tree)
            )
          );
        } else {
          throw new TypingException.ExpectedForAllType();
        }
      }
    }

    public static final class Ann extends Expr {

      public final Expr expr;
      public final SystemFType type;

      public Ann(Expr expr, SystemFType type) {
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
            List.of(checked.tree)
          )
        );
      }
    }

    public static final class TAbs extends Expr {

      public final String variable;
      public final Expr body;

      public TAbs(String variable, Expr body) {
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
        newCtx.push(new Entry.TVarBnd(this.variable));
        var bodyInferred = engine.infer(newCtx, this.body);

        var resolvedBodyType = bodyInferred.ctx.apply(bodyInferred.type);

        var break3Result = bodyInferred.ctx.break3(
          entry ->
            entry instanceof Entry.TVarBnd bnd &&
            bnd.tyVar.equals(this.variable)
        );

        var solvedFinalCtxEntries = break3Result.left
          .stream()
          .filter(entry -> entry instanceof Entry.SETVarBnd)
          .collect(Collectors.toCollection(() -> new ArrayList<Entry>()));

        solvedFinalCtxEntries.addAll(break3Result.right);
        var finalCtx = new Context(solvedFinalCtxEntries);
        var resType = new SystemFType.ForAll(this.variable, resolvedBodyType);

        var output = input + " => " + resType + " -| " + finalCtx;

        return new TypeInference.TypeResult(
          resType,
          finalCtx,
          new InferenceTree(
            "InfTAbs",
            input,
            output,
            List.of(bodyInferred.tree)
          )
        );
      }
    }

    // TODO(jan): for this, a marker interface might be needed, to mark overall primitives as literals
    public sealed interface Lit {
      public final record Int(int value) implements Lit {
        @Override
        public final String toString() {
          return Integer.toString(value);
        }
      }

      public final record Bool(boolean value) implements Lit {
        @Override
        public final String toString() {
          return Boolean.toString(value);
        }
      }
    }

    public static final class LitExpr extends Expr {

      public final Lit lit;

      public LitExpr(Lit lit) {
        this.lit = lit;
      }

      @Override
      public final String toString() {
        return lit.toString();
      }

      @Override
      public TypeInference.TypeResult infer(TypeInference engine, Context ctx) {
        var input = ctx + " |- " + this;
        if (this.lit instanceof Lit.Bool) {
          return new TypeInference.TypeResult(
            new SystemFType.Bool(),
            ctx.copy(),
            new InferenceTree(
              "InfLitBool",
              input,
              input + " => Bool -| " + ctx,
              List.of()
            )
          );
        } else if (this.lit instanceof Lit.Int) {
          return new TypeInference.TypeResult(
            new SystemFType.Int(),
            ctx.copy(),
            new InferenceTree(
              "InfLitInt",
              input,
              input + " => Int-| " + ctx,
              List.of()
            )
          );
        } else {
          throw new TypingException.InvalidLiteral(this.lit);
        }
      }

      @Override
      public TypeInference.CheckResult check(
        TypeInference engine,
        Context ctx,
        SystemFType ty
      ) {
        var input = ctx + " |- " + this + " <=" + ty;
        if (this.lit instanceof Lit.Int && ty instanceof SystemFType.Int) {
          return new TypeInference.CheckResult(
            ctx.copy(),
            new InferenceTree("ChkLitInt", input, "" + ctx, List.of())
          );
        } else if (
          this.lit instanceof Lit.Bool && ty instanceof SystemFType.Bool
        ) {
          return new TypeInference.CheckResult(
            ctx.copy(),
            new InferenceTree("ChkLitBool", input, "" + ctx, List.of())
          );
        } else {
          return super.check(engine, ctx, ty);
        }
      }
    }

    public static final class Let extends Expr {

      public final String name;
      public final Expr value;
      public final Expr body;

      public Let(String name, Expr value, Expr body) {
        this.name = name;
        this.value = value;
        this.body = body;
      }

      @Override
      public final String toString() {
        return "let " + name + " = " + value + " in " + body;
      }

      @Override
      public TypeInference.TypeResult infer(TypeInference engine, Context ctx) {
        var input = ctx + " |- " + this;
        var valueInferred = engine.infer(ctx, this.value);
        var newCtx = valueInferred.ctx.copy();

        newCtx.push(new Entry.VarBnd(this.name, valueInferred.type));

        var bodyInferred = engine.infer(newCtx, this.body);

        var break3Result = bodyInferred.ctx.break3(
          entry ->
            entry instanceof Entry.VarBnd bnd && bnd.tmVar.equals(this.name)
        );

        var solvedFinalCtxEntries = break3Result.left
          .stream()
          .filter(entry -> entry instanceof Entry.SETVarBnd)
          .collect(Collectors.toCollection(() -> new ArrayList<Entry>()));

        solvedFinalCtxEntries.addAll(break3Result.right);
        var finalCtx = new Context(solvedFinalCtxEntries);

        return new TypeInference.TypeResult(
          bodyInferred.type,
          finalCtx,
          new InferenceTree(
            "InfLet",
            input,
            input + " => " + bodyInferred.type + " -| " + finalCtx,
            List.of(valueInferred.tree, bodyInferred.tree)
          )
        );
      }
    }

    public static final class IfThenElse extends Expr {

      public final Expr cond;
      public final Expr then;
      public final Expr else_;

      public IfThenElse(Expr cond, Expr then, Expr else_) {
        this.cond = cond;
        this.then = then;
        this.else_ = else_;
      }

      @Override
      public final String toString() {
        return "if " + cond + " then " + then + " else " + else_;
      }

      @Override
      public TypeInference.TypeResult infer(TypeInference engine, Context ctx) {
        var input = ctx + " |- " + this;
        var condCheck = engine.check(ctx, this.cond, new SystemFType.Bool());
        var thenInferred = engine.infer(condCheck.ctx, this.then);
        var elseInferred = engine.infer(thenInferred.ctx, this.else_);

        var unified = engine.subtype(
          elseInferred.ctx,
          thenInferred.type,
          elseInferred.type
        );

        var output = input + " => " + thenInferred.type + " -| " + unified.ctx;

        return new TypeInference.TypeResult(
          thenInferred.type,
          unified.ctx,
          new InferenceTree(
            "InfIf",
            input,
            output,
            List.of(
              condCheck.tree,
              thenInferred.tree,
              elseInferred.tree,
              unified.tree
            )
          )
        );
      }
    }

    public static final class BinOp extends Expr {

      public final BinOpKind kind;
      public final Expr left;
      public final Expr right;

      public BinOp(BinOpKind kind, Expr left, Expr right) {
        this.kind = kind;
        this.left = left;
        this.right = right;
      }

      @Override
      public final String toString() {
        return left + " " + kind + " " + right;
      }

      @Override
      public TypeInference.TypeResult infer(TypeInference engine, Context ctx) {
        var input = ctx + " |- " + this;
        if (
          this.kind == BinOpKind.ADD ||
          this.kind == BinOpKind.SUB ||
          this.kind == BinOpKind.MUL ||
          this.kind == BinOpKind.DIV
        ) {
          var res1 = engine.check(ctx, this.left, new SystemFType.Int());
          var res2 = engine.check(res1.ctx, this.right, new SystemFType.Int());
          var output = input + " => Int -| " + res2.ctx;
          return new TypeInference.TypeResult(
            new SystemFType.Int(),
            res2.ctx,
            new InferenceTree(
              "InfArith",
              input,
              output,
              List.of(res1.tree, res2.tree)
            )
          );
        } else if (this.kind == BinOpKind.EQ || this.kind == BinOpKind.NEQ) {
          var infRes = engine.infer(ctx, this.left);
          var checkRes = engine.check(infRes.ctx, this.right, infRes.type);

          var output = input + " => Bool -| " + checkRes.ctx;
          return new TypeInference.TypeResult(
            new SystemFType.Bool(),
            checkRes.ctx,
            new InferenceTree(
              "InfEq",
              input,
              output,
              List.of(infRes.tree, checkRes.tree)
            )
          );
        }

        return null;
      }

      public enum BinOpKind {
        ADD,
        SUB,
        MUL,
        DIV,
        EQ,
        NEQ;

        @Override
        public String toString() {
          return switch (this) {
            case ADD -> "+";
            case SUB -> "-";
            case MUL -> "*";
            case DIV -> "/";
            case EQ -> "==";
            case NEQ -> "!=";
          };
        }
      }
    }
  }

  /** Context entry for type checking and inference */
  public sealed interface Entry {
    public final record VarBnd(
      String tmVar,
      SystemFType type
    ) implements Entry {
      @Override
      public final String toString() {
        return tmVar + " : " + type;
      }
    }

    public final record TVarBnd(String tyVar) implements Entry {
      @Override
      public final String toString() {
        return tyVar;
      }
    }

    public final record ETVarBnd(String tyVar) implements Entry {
      @Override
      public final String toString() {
        return tyVar;
      }
    }

    public final record SETVarBnd(
      String tyVar,
      SystemFType type
    ) implements Entry {
      @Override
      public final String toString() {
        return tyVar + " : " + type;
      }
    }

    public final record Mark(String tyVar) implements Entry {
      @Override
      public final String toString() {
        return "MARK " + tyVar;
      }
    }
  }

  static class Context {

    private List<Entry> entries;

    public Context() {
      this.entries = new ArrayList<>();
    }

    public Context(List<Entry> entries) {
      this.entries = new ArrayList<>(entries);
    }

    @Override
    public String toString() {
      return (
        "{" +
        this.entries
          .stream()
          .map(Object::toString)
          .collect(Collectors.joining(", ")) +
        "}"
      );
    }

    public final record Break3Result(
      List<Entry> left,
      Optional<Entry> target,
      List<Entry> right
    ) {}

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
      var list = new ArrayList<Entry>();
      list.addAll(this.entries);
      return new Context(list);
    }

    /**
     * break the context into possibly three parts, if the predicate was found to be in the context.
     *
     * @param pred the predicate to find an entry in the context
     * @return the three parts broken up into left half (up to, but excluding, the found entry), the entry for which the pred is true, and the right half (everything after the found entry, exlucing the entry)
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
          this.entries.size()
        );
        return new Break3Result(
          List.copyOf(firstPart),
          Optional.of(target),
          secondPart
        );
      } else {
        return new Break3Result(
          List.copyOf(this.entries.subList(0, this.entries.size())),
          Optional.empty(),
          List.of()
        );
      }
    }

    public static Context fromParts(
      List<Entry> left,
      Entry middle,
      List<Entry> right
    ) {
      var list = new ArrayList<Entry>();
      list.addAll(left);
      list.add(middle);
      list.addAll(right);

      return new Context(list);
    }

    public SystemFType applyOnce(SystemFType type) {
      if (type instanceof SystemFType.EtVar etVar) {
        var filterRes = this.find(
          entry ->
            entry instanceof Entry.SETVarBnd bnd && bnd.tyVar.equals(etVar.name)
        );
        if (filterRes.isPresent()) {
          return this.applyOnce(((Entry.SETVarBnd) filterRes.get()).type);
        } else {
          return type;
        }
      } else if (type instanceof SystemFType.Arrow arrow) {
        return new SystemFType.Arrow(
          this.applyOnce(arrow.from),
          this.applyOnce(arrow.to)
        );
      } else if (type instanceof SystemFType.ForAll forAll) {
        return new SystemFType.ForAll(
          forAll.boundVar,
          this.applyOnce(forAll.body)
        );
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

    /** Test wheather type Variable A appears before type Variable B in the context.
     * In this case, appearing before means that type Variable A appears later in the context
     */
    public boolean before(String tyVarA, String tyVarB) {
      var posA = IntStream.range(0, this.entries.size())
        .filter(
          i ->
            this.entries.get(i) instanceof Entry.ETVarBnd bnd &&
            bnd.tyVar.equals(tyVarA)
        )
        .findFirst();

      var posB = IntStream.range(0, this.entries.size())
        .filter(
          i ->
            this.entries.get(i) instanceof Entry.ETVarBnd bnd &&
            bnd.tyVar.equals(tyVarB)
        )
        .findFirst();

      if (posA.isPresent() && posB.isPresent()) {
        return posA.getAsInt() > posB.getAsInt();
      }
      return false;
    }
  }

  public static final class TypeInference
    extends TypeDialect.TypeInferenceSolver
  {

    private int counter;

    public TypeInference() {
      this.counter = 0;
    }

    public String freshTypeVar() {
      return "t" + this.counter++;
    }

    @Override
    public Type solve(Expression expr) {
      if (expr instanceof Expr exp) {
        return (Type) this.inferType(exp);
      } else {
        throw new TypingException.UnsupportedExpression(
          TypingException.UnsupportedExpression.AlgorithmType.SystemF,
          expr
        );
      }
    }

    SystemFType inferType(Expr expr) {
      var res = this.infer(new Context(), expr);
      return res.ctx.apply(res.type);
    }

    SystemFType substType(
      String tyVar,
      SystemFType replacement,
      SystemFType target
    ) {
      return target.substType(tyVar, replacement);
    }

    public final record TypeResult(
      SystemFType type,
      Context ctx,
      InferenceTree tree
    ) {}

    TypeResult infer(Context ctx, Expr expr) {
      return expr.infer(this, ctx);
    }

    public final record CheckResult(Context ctx, InferenceTree tree) {}

    public CheckResult check(Context ctx, Expr expr, SystemFType ty) {
      var input = ctx + " |- " + expr + " <=" + ty;

      if (ty instanceof SystemFType.ForAll forall) {
        var newCtx = ctx.copy();
        newCtx.push(new Entry.TVarBnd(forall.boundVar));
        var bodyCheck = this.check(newCtx, expr, forall.body);

        var break3Result = bodyCheck.ctx.break3(
          entry ->
            entry instanceof Entry.TVarBnd bnd &&
            bnd.tyVar.equals(forall.boundVar)
        );

        var finalCtx = new Context(break3Result.right);

        return new CheckResult(
          finalCtx,
          new InferenceTree(
            "ChkAll",
            input,
            "" + finalCtx,
            List.of(bodyCheck.tree)
          )
        );
      } else {
        return expr.check(this, ctx, ty);
      }
    }

    public final record SubtypeResult(Context ctx, InferenceTree tree) {}

    public SubtypeResult subtype(
      Context ctx,
      SystemFType ty1,
      SystemFType ty2
    ) {
      var input = ctx + " |- " + ty1 + " <: " + ty2;

      if (ty1 instanceof SystemFType.Int && ty2 instanceof SystemFType.Int) {
        return new SubtypeResult(
          ctx.copy(),
          new InferenceTree("SubRefl", input, "" + ctx, List.of())
        );
      } else if (
        ty1 instanceof SystemFType.Bool && ty2 instanceof SystemFType.Bool
      ) {
        return new SubtypeResult(
          ctx.copy(),
          new InferenceTree("SubRefl", input, "" + ctx, List.of())
        );
      } else if (
        ty1 instanceof SystemFType.Var v1 &&
        ty2 instanceof SystemFType.Var v2 &&
        v1.name.equals(v2.name)
      ) {
        return new SubtypeResult(
          ctx.copy(),
          new InferenceTree("SubReflTVar", input, "" + ctx, List.of())
        );
      } else if (
        ty1 instanceof SystemFType.EtVar v1 &&
        ty2 instanceof SystemFType.EtVar v2 &&
        v1.name.equals(v2.name)
      ) {
        return new SubtypeResult(
          ctx.copy(),
          new InferenceTree("SubReflETVar", input, "" + ctx, List.of())
        );
      } else if (
        ty1 instanceof SystemFType.Arrow a1 &&
        ty2 instanceof SystemFType.Arrow a2
      ) {
        var covArg = this.subtype(ctx, a1.from, a2.from);
        var covRes = this.subtype(covArg.ctx, a1.to, a2.to);

        return new SubtypeResult(
          covRes.ctx,
          new InferenceTree(
            "SubArr",
            input,
            "" + covRes.ctx,
            List.of(covArg.tree, covRes.tree)
          )
        );
      } else if (ty2 instanceof SystemFType.ForAll forall) {
        Context newCtx = ctx.copy();
        newCtx.push(new Entry.TVarBnd(forall.boundVar));
        SubtypeResult subtypeRes = this.subtype(newCtx, ty1, forall.body);
        Break3Result breakRes = subtypeRes.ctx.break3(
          entry ->
            entry instanceof Entry.TVarBnd bnd &&
            bnd.tyVar.equals(forall.boundVar)
        );
        Context finalCtx = new Context(breakRes.right);

        return new SubtypeResult(
          finalCtx,
          new InferenceTree(
            "SubAllR",
            input,
            "" + finalCtx,
            List.of(subtypeRes.tree)
          )
        );
      } else if (ty1 instanceof SystemFType.ForAll forall) {
        var substT1 = this.substType(
          forall.boundVar,
          new SystemFType.EtVar(forall.boundVar),
          forall.body
        );

        var newCtx = ctx.copy();
        newCtx.push(new Entry.ETVarBnd(forall.boundVar));
        newCtx.push(new Entry.Mark(forall.boundVar));

        var subtypeRes = this.subtype(newCtx, substT1, ty2);
        var breakRes = subtypeRes.ctx.break3(
          entry ->
            entry instanceof Entry.Mark m && m.tyVar.equals(forall.boundVar)
        );
        var finalCtx = new Context(breakRes.right);
        return new SubtypeResult(
          finalCtx,
          new InferenceTree(
            "SubAllL",
            input,
            "" + finalCtx,
            List.of(subtypeRes.tree)
          )
        );
      } else if (
        ty1 instanceof SystemFType.EtVar etvar && !ty2.occursCheck(etvar.name)
      ) {
        var instLRes = this.instL(ctx, etvar.name, ty2);
        var output = "" + instLRes.ctx;
        return new SubtypeResult(
          instLRes.ctx,
          new InferenceTree("SubInstL", input, output, List.of(instLRes.tree))
        );
      } else if (
        ty2 instanceof SystemFType.EtVar etvar && !ty1.occursCheck(etvar.name)
      ) {
        var instRRes = this.instR(ctx, ty1, etvar.name);
        var output = "" + instRRes.ctx;
        return new SubtypeResult(
          instRRes.ctx,
          new InferenceTree("SubInstR", input, output, List.of(instRRes.tree))
        );
      } else {
        throw new TypingException.SubtypingFailed(ty1, ty2);
      }
    }

    final record InstResult(Context ctx, InferenceTree tree) {}

    InstResult instL(Context ctx, String a, SystemFType ty) {
      var input = ctx + " |- ^" + a + " :=< " + ty;

      if (ty instanceof SystemFType.EtVar etvar && ctx.before(a, etvar.name)) {
        var breakRes = ctx.break3(
          entry ->
            entry instanceof Entry.ETVarBnd bnd && bnd.tyVar.equals(etvar.name)
        );
        var newCtx = Context.fromParts(
          breakRes.left,
          new Entry.SETVarBnd(etvar.name, new SystemFType.EtVar(a)),
          breakRes.right
        );
        return new InstResult(
          newCtx,
          new InferenceTree("InstLReach", input, "" + newCtx, List.of())
        );
      } else if (ty instanceof SystemFType.Arrow arrow) {
        var a1 = this.freshTypeVar();
        var a2 = this.freshTypeVar();
        var breakRes = ctx.break3(
          entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar.equals(a)
        );
        var arrowType = new SystemFType.Arrow(
          new SystemFType.EtVar(a1),
          new SystemFType.EtVar(a2)
        );

        var newCtx = new Context(breakRes.left);
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
            List.of(instRRes.tree, instLRes.tree)
          )
        );
      } else if (ty instanceof SystemFType.ForAll forall) {
        var newCtx = ctx.copy();
        newCtx.push(new Entry.TVarBnd(forall.boundVar));
        var instLRes = this.instL(newCtx, a, forall.body);
        var breakRes = instLRes.ctx.break3(
          entry ->
            entry instanceof Entry.TVarBnd bnd &&
            bnd.tyVar.equals(forall.boundVar)
        );
        var finalCtx = new Context(breakRes.right);
        return new InstResult(
          finalCtx,
          new InferenceTree(
            "InstLAllR",
            input,
            "" + finalCtx,
            List.of(instLRes.tree)
          )
        );
      } else if (ty.isMono()) {
        if (ty.occursCheck(a)) {
          throw new TypingException.OccursCheckFailed(ty, a);
        }

        var breakRes = ctx.break3(
          entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar.equals(a)
        );
        var newCtx = Context.fromParts(
          breakRes.left,
          new Entry.SETVarBnd(a, ty),
          breakRes.right
        );
        return new InstResult(
          newCtx,
          new InferenceTree("InstLSolve", input, "" + newCtx, List.of())
        );
      } else {
        throw new TypingException.InstantiationError(
          "InstL Instantiation error " + ctx + " |- " + ty
        );
      }
    }

    InstResult instR(Context ctx, SystemFType ty, String a) {
      var input = ctx + " |- " + ty + " :=< ^" + a;
      if (ty instanceof SystemFType.EtVar etvar) {
        var breakRes = ctx.break3(
          entry ->
            entry instanceof Entry.ETVarBnd bnd && bnd.tyVar.equals(etvar.name)
        );
        var newCtx = Context.fromParts(
          breakRes.left,
          new Entry.SETVarBnd(etvar.name, new SystemFType.EtVar(a)),
          breakRes.right
        );

        return new InstResult(
          newCtx,
          new InferenceTree("InstRReach", input, "" + newCtx, List.of())
        );
      } else if (ty instanceof SystemFType.Arrow arrow) {
        var a1 = this.freshTypeVar();
        var a2 = this.freshTypeVar();
        var breakRes = ctx.break3(
          entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar.equals(a)
        );
        var arrowType = new SystemFType.Arrow(
          new SystemFType.EtVar(a1),
          new SystemFType.EtVar(a2)
        );

        var newCtx = new Context(breakRes.left);
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
            List.of(instRRes.tree, instLRes.tree)
          )
        );
      } else if (ty instanceof SystemFType.ForAll forall) {
        var substT = this.substType(
          forall.boundVar,
          new SystemFType.EtVar(forall.boundVar),
          forall.body
        );

        var newCtx = ctx.copy();
        newCtx.push(new Entry.ETVarBnd(forall.boundVar));
        newCtx.push(new Entry.Mark(forall.boundVar));

        var instRRes = this.instR(newCtx, substT, a);
        var breakRes = instRRes.ctx.break3(
          entry ->
            entry instanceof Entry.Mark m && m.tyVar.equals(forall.boundVar)
        );
        var finalCtx = new Context(breakRes.right);
        return new InstResult(
          finalCtx,
          new InferenceTree(
            "InstRAllL",
            input,
            "" + instRRes.ctx,
            List.of(instRRes.tree)
          )
        );
      } else if (ty.isMono()) {
        if (ty.occursCheck(a)) {
          throw new TypingException.OccursCheckFailed(ty, a);
        }

        var breakRes = ctx.break3(
          entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar.equals(a)
        );
        var newCtx = Context.fromParts(
          breakRes.left,
          new Entry.SETVarBnd(a, ty),
          breakRes.right
        );
        return new InstResult(
          newCtx,
          new InferenceTree("InstRSolve", input, "" + ctx, List.of())
        );
      } else {
        throw new TypingException.InstantiationError(
          "InstR Instantiation error " + ctx + " |- " + ty
        );
      }
    }
  }
}

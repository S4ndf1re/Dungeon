package dgir.core.ir.types;

import dgir.core.ir.types.SystemFInference.Expr.Lit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public sealed interface SystemFInference {
  public abstract sealed class SystemFType {

    public abstract boolean isMono();

    public abstract Set<String> freeVariables();

    public boolean occursCheck(String varName) {
      return this.freeVariables().contains(varName);
    }

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
    }
  }

  public sealed interface Expr {
    public final record Var(String name) implements Expr {
      @Override
      public final String toString() {
        return name;
      }
    }

    public final record App(Expr fun, Expr arg) implements Expr {
      @Override
      public final String toString() {
        return fun + " " + arg;
      }
    }

    public final record Abs(
      String name,
      SystemFType type,
      Expr body
    ) implements Expr {
      @Override
      public final String toString() {
        return "λ" + name + ": " + type + ". " + body;
      }
    }

    public final record TApp(Expr func, SystemFType type) implements Expr {
      @Override
      public final String toString() {
        return func + " " + type;
      }
    }

    public final record Ann(Expr expr, SystemFType type) implements Expr {
      @Override
      public final String toString() {
        return expr + " : " + type;
      }
    }

    public final record TAbs(String variable, Expr body) implements Expr {
      @Override
      public final String toString() {
        return "∀" + variable + ". " + body;
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

    public final record LitExpr(Lit lit) implements Expr {
      @Override
      public final String toString() {
        return lit.toString();
      }
    }

    public final record Let(
      String name,
      Expr value,
      Expr body
    ) implements Expr {
      @Override
      public final String toString() {
        return "let " + name + " = " + value + " in " + body;
      }
    }

    public final record IfThenElse(
      Expr cond,
      Expr then,
      Expr else_
    ) implements Expr {
      @Override
      public final String toString() {
        return "if " + cond + " then " + then + " else " + else_;
      }
    }

    public final record BinOp(
      BinOpKind kind,
      Expr left,
      Expr right
    ) implements Expr {
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

      @Override
      public final String toString() {
        return left + " " + kind + " " + right;
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

  class Context {

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

  public final class TypeInference implements SystemFInference {

    private int counter;

    public TypeInference() {
      this.counter = 0;
    }

    public String freshTypeVar() {
      return "t" + this.counter++;
    }

    public SystemFType inferType(Expr expr) {
      var res = this.infer(new Context(), expr);
      System.out.println(res.tree);
      return res.ctx.apply(res.type);
    }

    SystemFType substType(
      String tyVar,
      SystemFType replacement,
      SystemFType target
    ) {
      if (target instanceof SystemFType.Var var) {
        if (var.name.equals(tyVar)) {
          return replacement;
        } else {
          return target;
        }
      } else if (target instanceof SystemFType.EtVar var) {
        if (var.name.equals(tyVar)) {
          return replacement;
        } else {
          return target;
        }
      } else if (
        target instanceof SystemFType.Int || target instanceof SystemFType.Bool
      ) {
        return target;
      } else if (target instanceof SystemFType.Arrow arrow) {
        return new SystemFType.Arrow(
          this.substType(tyVar, replacement, arrow.from),
          this.substType(tyVar, replacement, arrow.to)
        );
      } else if (target instanceof SystemFType.ForAll forAll) {
        if (forAll.boundVar.equals(tyVar)) {
          // The type variable is shadowed by the ForAll binder
          return target;
        } else {
          return new SystemFType.ForAll(
            forAll.boundVar,
            this.substType(tyVar, replacement, forAll.body)
          );
        }
      } else {
        throw new IllegalArgumentException("Unexpected Type Parameter");
      }
    }

    public final record TypeResult(
      SystemFType type,
      Context ctx,
      InferenceTree tree
    ) {}

    TypeResult infer(Context ctx, Expr expr) {
      var input = ctx + " |- " + expr;
      if (expr instanceof Expr.Var var) {
        return this.inferVar(ctx, var, input);
      } else if (expr instanceof Expr.Ann ann) {
        return this.inferAnn(ctx, ann, input);
      } else if (expr instanceof Expr.LitExpr lit) {
        return this.inferLit(ctx, lit, input);
      } else if (expr instanceof Expr.Abs abs) {
        return this.inferAbs(ctx, abs, input);
      } else if (expr instanceof Expr.App app) {
        return this.inferApp(ctx, app, input);
      } else if (expr instanceof Expr.TAbs tabs) {
        return this.inferTAbs(ctx, tabs, input);
      } else if (expr instanceof Expr.TApp tapp) {
        return this.inferTApp(ctx, tapp, input);
      } else if (expr instanceof Expr.Let let) {
        return this.inferLet(ctx, let, input);
      } else if (expr instanceof Expr.IfThenElse ifthenelse) {
        return this.inferIfThenElse(ctx, ifthenelse, input);
      } else if (expr instanceof Expr.BinOp binop) {
        return this.inferBinOp(ctx, binop, input);
      } else {
        throw new IllegalArgumentException("Unexpected Expression");
      }
    }

    TypeResult inferVar(Context ctx, Expr.Var var, String input) {
      var boundVariable = ctx.find(
        entry -> entry instanceof Entry.VarBnd bnd && bnd.tmVar.equals(var.name)
      );

      if (boundVariable.isPresent()) {
        var varBnd = (Entry.VarBnd) boundVariable.get();
        return new TypeResult(
          varBnd.type,
          ctx.copy(),
          new InferenceTree(
            "InfVar",
            ctx + " |- " + var,
            input + " => " + varBnd.type + " -| " + ctx,
            List.of()
          )
        );
      }

      return null;
    }

    TypeResult inferAnn(Context ctx, Expr.Ann ann, String input) {
      var checked = this.check(ctx, ann.expr, ann.type);

      return new TypeResult(
        ann.type,
        checked.ctx,
        new InferenceTree(
          "InfAnn",
          input,
          input + " => " + ann.type + " -| " + checked.ctx,
          List.of(checked.tree)
        )
      );
    }

    TypeResult inferLit(Context ctx, Expr.LitExpr lit, String input) {
      if (lit.lit instanceof Lit.Bool) {
        return new TypeResult(
          new SystemFType.Bool(),
          ctx.copy(),
          new InferenceTree(
            "InfLitBool",
            input,
            input + " => Bool -| " + ctx,
            List.of()
          )
        );
      } else if (lit.lit instanceof Lit.Int) {
        return new TypeResult(
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
        // TODO(jan): extend with all primitives unified for all type systems
        throw new IllegalArgumentException(
          "Only int and bool are supported for literals for now"
        );
      }
    }

    TypeResult inferApp(Context ctx, Expr.App app, String input) {
      var funcInferred = this.infer(ctx, app.fun);
      var funcTypeApplied = funcInferred.ctx.apply(funcInferred.type);

      Supplier<TypeResult> application = () -> {
        if (funcTypeApplied instanceof SystemFType.Arrow arrow) {
          var paramTy = arrow.from;
          var resultTy = arrow.to;

          var paramCheck = this.check(funcInferred.ctx, app.arg, paramTy);
          return new TypeResult(
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

          var a1 = this.freshTypeVar();
          var a2 = this.freshTypeVar();

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

          var checkRes = this.check(newCtx, app.arg, new SystemFType.EtVar(a1));

          var output = input + " =>=> ^" + a2 + " -| " + checkRes.ctx;
          return new TypeResult(
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
          throw new RuntimeException("Application Type Error");
        }
      };

      var result = application.get();
      var output = input + " => " + result.type + " -| " + result.ctx;
      return new TypeResult(
        result.type,
        result.ctx,
        new InferenceTree(
          "InfApp",
          input,
          output,
          List.of(funcInferred.tree, result.tree)
        )
      );
    }

    TypeResult inferAbs(Context ctx, Expr.Abs abs, String input) {
      var b = this.freshTypeVar();
      var newCtx = ctx.copy();

      newCtx.push(new Entry.VarBnd(abs.name, abs.type));
      newCtx.push(new Entry.ETVarBnd(b));

      var c1 = this.check(newCtx, abs.body, new SystemFType.EtVar(b));
      var breakRes = c1.ctx.break3(
        entry -> entry instanceof Entry.VarBnd bnd && bnd.tmVar.equals(abs.name)
      );

      // Only preserve all solved existential variables from the left context
      var solvedFinalCtxEntries = breakRes.left
        .stream()
        .filter(entry -> entry instanceof Entry.SETVarBnd)
        .collect(Collectors.toCollection(() -> new ArrayList<Entry>()));

      solvedFinalCtxEntries.addAll(breakRes.right);
      var finalCtx = new Context(solvedFinalCtxEntries);
      var resType = new SystemFType.Arrow(abs.type, new SystemFType.EtVar(b));

      return new TypeResult(
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

    TypeResult inferTAbs(Context ctx, Expr.TAbs tabs, String input) {
      var newCtx = ctx.copy();
      newCtx.push(new Entry.TVarBnd(tabs.variable));
      var bodyInferred = this.infer(newCtx, tabs.body);

      var resolvedBodyType = bodyInferred.ctx.apply(bodyInferred.type);

      var break3Result = bodyInferred.ctx.break3(
        entry ->
          entry instanceof Entry.TVarBnd bnd && bnd.tyVar.equals(tabs.variable)
      );
      // Only preserve all solved existential variables from the left context
      var solvedFinalCtxEntries = break3Result.left
        .stream()
        .filter(entry -> entry instanceof Entry.SETVarBnd)
        .collect(Collectors.toCollection(() -> new ArrayList<Entry>()));

      solvedFinalCtxEntries.addAll(break3Result.right);
      var finalCtx = new Context(solvedFinalCtxEntries);
      var resType = new SystemFType.ForAll(tabs.variable, resolvedBodyType);

      var output = input + " => " + resType + " -| " + finalCtx;

      return new TypeResult(
        resType,
        finalCtx,
        new InferenceTree("InfTAbs", input, output, List.of(bodyInferred.tree))
      );
    }

    TypeResult inferTApp(Context ctx, Expr.TApp tapp, String input) {
      var funcInferred = this.infer(ctx, tapp.func);
      if (funcInferred.type instanceof SystemFType.ForAll forall) {
        var resultType = this.substType(
          forall.boundVar,
          tapp.type,
          forall.body
        );
        var output = input + " => " + resultType + " -| " + funcInferred.ctx;
        return new TypeResult(
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
        throw new RuntimeException("Not implemented for non ForAll types");
      }
    }

    TypeResult inferLet(Context ctx, Expr.Let let, String input) {
      var valueInferred = this.infer(ctx, let.value);
      var newCtx = valueInferred.ctx.copy();

      newCtx.push(new Entry.VarBnd(let.name, valueInferred.type));

      var bodyInferred = this.infer(newCtx, let.body);

      var break3Result = bodyInferred.ctx.break3(
        entry -> entry instanceof Entry.VarBnd bnd && bnd.tmVar.equals(let.name)
      );

      // Only preserve all solved existential variables from the left context
      var solvedFinalCtxEntries = break3Result.left
        .stream()
        .filter(entry -> entry instanceof Entry.SETVarBnd)
        .collect(Collectors.toCollection(() -> new ArrayList<Entry>()));

      solvedFinalCtxEntries.addAll(break3Result.right);
      var finalCtx = new Context(solvedFinalCtxEntries);

      return new TypeResult(
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

    TypeResult inferIfThenElse(
      Context ctx,
      Expr.IfThenElse ifthenelse,
      String input
    ) {
      var condCheck = this.check(ctx, ifthenelse.cond, new SystemFType.Bool());
      var thenInferred = this.infer(condCheck.ctx, ifthenelse.then);
      var elseInferred = this.infer(thenInferred.ctx, ifthenelse.else_);

      var unified = this.subtype(
        elseInferred.ctx,
        thenInferred.type,
        elseInferred.type
      );

      var output = input + " => " + thenInferred.type + " -| " + unified.ctx;

      return new TypeResult(
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

    TypeResult inferBinOp(Context ctx, Expr.BinOp binop, String input) {
      if (
        binop.kind == Expr.BinOp.BinOpKind.ADD ||
        binop.kind == Expr.BinOp.BinOpKind.SUB ||
        binop.kind == Expr.BinOp.BinOpKind.MUL ||
        binop.kind == Expr.BinOp.BinOpKind.DIV
      ) {
        var res1 = this.check(ctx, binop.left, new SystemFType.Int());
        var res2 = this.check(res1.ctx, binop.right, new SystemFType.Int());
        var output = input + " => Int -| " + res2.ctx;
        return new TypeResult(
          new SystemFType.Int(),
          res2.ctx,
          new InferenceTree(
            "InfArith",
            input,
            output,
            List.of(res1.tree, res2.tree)
          )
        );
      } else if (
        binop.kind == Expr.BinOp.BinOpKind.EQ ||
        binop.kind == Expr.BinOp.BinOpKind.NEQ
      ) {
        var infRes = this.infer(ctx, binop.left);
        var checkRes = this.check(infRes.ctx, binop.right, infRes.type);

        var output = input + " => Bool -| " + checkRes.ctx;
        return new TypeResult(
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

    public final record CheckResult(Context ctx, InferenceTree tree) {}

    public CheckResult check(Context ctx, Expr expr, SystemFType ty) {
      var input = ctx + " |- " + expr + " <=" + ty;

      if (
        expr instanceof Expr.LitExpr lit &&
        lit.lit instanceof Lit.Int &&
        ty instanceof SystemFType.Int
      ) {
        return new CheckResult(
          ctx.copy(),
          new InferenceTree("ChkLitInt", input, "" + ctx, List.of())
        );
      } else if (
        expr instanceof Expr.LitExpr lit &&
        lit.lit instanceof Lit.Bool &&
        ty instanceof SystemFType.Bool
      ) {
        return new CheckResult(
          ctx.copy(),
          new InferenceTree("ChkLitBool", input, "" + ctx, List.of())
        );
      } else if (
        expr instanceof Expr.Abs abs && ty instanceof SystemFType.Arrow arrow
      ) {
        var newCtx = ctx.copy();
        newCtx.push(new Entry.VarBnd(abs.name, arrow.from));

        var bodyCheck = this.check(newCtx, abs.body, arrow.to);
        var break3Result = bodyCheck.ctx.break3(
          entry ->
            entry instanceof Entry.VarBnd bnd && bnd.tmVar.equals(abs.name)
        );

        var finalCtx = new Context(break3Result.right);

        return new CheckResult(
          finalCtx,
          new InferenceTree(
            "ChkLam",
            input,
            "" + finalCtx,
            List.of(bodyCheck.tree)
          )
        );
      } else if (ty instanceof SystemFType.ForAll forall) {
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
        var inferred = this.infer(ctx, expr);
        var inferredApplied = inferred.ctx.apply(inferred.type);
        var typeApplied = inferred.ctx.apply(ty);
        var subtyped = this.subtype(inferred.ctx, inferredApplied, typeApplied);
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
    }

    public final record SubtypeResult(Context ctx, InferenceTree tree) {}

    public SubtypeResult subtype(
      Context ctx,
      SystemFType ty1,
      SystemFType ty2
    ) {
      var input = ctx + " |- " + ty1 + " <: " + ty2;

      if (
        (ty1 instanceof SystemFType.Int && ty2 instanceof SystemFType.Int) ||
        (ty1 instanceof SystemFType.Bool && ty2 instanceof SystemFType.Bool)
      ) {
        return new SubtypeResult(
          ctx.copy(),
          new InferenceTree("SubRefl", input, "" + ctx, List.of())
        );
      } else if (
        ty1 instanceof SystemFType.Var a &&
        ty2 instanceof SystemFType.Var b &&
        a.name.equals(b.name)
      ) {
        return new SubtypeResult(
          ctx.copy(),
          new InferenceTree("SubReflTVar", input, "" + ctx, List.of())
        );
      } else if (
        ty1 instanceof SystemFType.EtVar a &&
        ty2 instanceof SystemFType.EtVar b &&
        a.name.equals(b.name)
      ) {
        return new SubtypeResult(
          ctx.copy(),
          new InferenceTree("SubReflETVar", input, "" + ctx, List.of())
        );
      } else if (
        ty1 instanceof SystemFType.Arrow a && ty2 instanceof SystemFType.Arrow b
      ) {
        var covArg = this.subtype(ctx, a.from, b.from);
        var covRes = this.subtype(covArg.ctx, a.to, b.to);

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
        var newCtx = ctx.copy();
        newCtx.push(new Entry.TVarBnd(forall.boundVar));
        var bodySubtype = this.subtype(newCtx, ty1, forall.body);
        var breakRes = bodySubtype.ctx.break3(
          entry ->
            entry instanceof Entry.TVarBnd bnd &&
            bnd.tyVar.equals(forall.boundVar)
        );
        var finalCtx = new Context(breakRes.right);
        return new SubtypeResult(
          finalCtx,
          new InferenceTree(
            "SubAllR",
            input,
            "" + finalCtx,
            List.of(bodySubtype.tree)
          )
        );
      } else if (ty1 instanceof SystemFType.ForAll forall) {
        var substTy1 = this.substType(
          forall.boundVar,
          new SystemFType.EtVar(forall.boundVar),
          forall.body
        );
        var newCtx = ctx.copy();
        newCtx.push(new Entry.ETVarBnd(forall.boundVar));
        newCtx.push(new Entry.Mark(forall.boundVar));

        var bodySubtype = this.subtype(newCtx, substTy1, ty2);
        var breakRes = bodySubtype.ctx.break3(
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
            List.of(bodySubtype.tree)
          )
        );
      } else if (
        ty1 instanceof SystemFType.EtVar etvar && !ty2.occursCheck(etvar.name)
      ) {
        var res = this.instL(ctx, etvar.name, ty2);
        return new SubtypeResult(
          res.ctx,
          new InferenceTree("SubInstL", input, "" + res.ctx, List.of(res.tree))
        );
      } else if (
        ty2 instanceof SystemFType.EtVar etvar && !ty1.occursCheck(etvar.name)
      ) {
        var res = this.instR(ctx, ty1, etvar.name);
        return new SubtypeResult(
          res.ctx,
          new InferenceTree("SubInstL", input, "" + res.ctx, List.of(res.tree))
        );
      } else {
        throw new RuntimeException("Subtyping error, check failed");
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
          throw new RuntimeException(
            "Occur check failed, recursive type detected"
          );
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
        throw new RuntimeException("InstL Instantiation error " + ctx + " |- " + ty);
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
          throw new RuntimeException(
            "Occur check failed, recursive type detected"
          );
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
        throw new RuntimeException("InstR Instantiation error " + ctx + " |- " + ty);
      }
    }
  }
}

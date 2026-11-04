package dgir.core.ir.types.builtin.systemf.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.traits.IApplication;
import dgir.core.ir.types.builtin.systemf.CheckResult;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Entry;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.TypeResult;

public final class App extends Expr implements IApplication<Expr, SystemFType> {

  private final Expr fun;
  private final Optional<Expr> arg;
  // The arrow type of the function this application applies to
  private Optional<SystemFType> inferredFunctionType;
  // The type abstractions of the function, that are implicitly instantiated by
  // this application: each bound variable with the existential (hole) standing
  // in for its type argument
  private List<Pair<TypeVar<SystemFType>, TypeVar<SystemFType>>> implicitTypeArgs;

  public App(Expr fun) {
    this.fun = fun;
    this.arg = Optional.empty();
    this.inferredFunctionType = Optional.empty();
    this.implicitTypeArgs = List.of();
  }

  public App(Location loc, Expr fun) {
    super(loc);
    this.fun = fun;
    this.arg = Optional.empty();
    this.inferredFunctionType = Optional.empty();
    this.implicitTypeArgs = List.of();
  }

  public App(Expr fun, Expr arg) {
    this.fun = fun;
    this.arg = Optional.of(arg);
    this.inferredFunctionType = Optional.empty();
    this.implicitTypeArgs = List.of();
  }

  public App(Location loc, Expr fun, Expr arg) {
    super(loc);
    this.fun = fun;
    this.arg = Optional.of(arg);
    this.inferredFunctionType = Optional.empty();
    this.implicitTypeArgs = List.of();
  }

  public App(App other) {
    super(other);
    this.fun = other.fun;
    this.arg = other.arg;
    this.inferredFunctionType = other.inferredFunctionType;
    this.implicitTypeArgs = other.implicitTypeArgs;
  }

  public App(App other, Expr fun, Optional<Expr> arg) {
    super(other);
    this.fun = fun;
    this.arg = arg;
    this.inferredFunctionType = other.inferredFunctionType;
    this.implicitTypeArgs = other.implicitTypeArgs;
  }

  public Expr fun() {
    return this.fun.unwrapOrThis();
  }

  public Optional<Expr> arg() {
    return this.arg.map(Expr::unwrapOrThis);
  }

  @Override
  public final String toString() {
    return this.arg.isPresent() ? fun + " " + this.arg.get() : fun + " ()";
  }

  @Override
  public TypeResult infer(TypeInference engine, Context ctx) {
    var funcInferred = engine.infer(ctx, this.fun);
    var funcTypeApplied = funcInferred.ctx().apply(funcInferred.type());
    var funcCtx = funcInferred.ctx();

    // Implicit instantiation: the type abstractions of the function are
    // instantiated with fresh existentials (holes), which are solved by the
    // argument checks of this and the following applications of the call
    var typeArgs = new ArrayList<Pair<TypeVar<SystemFType>, TypeVar<SystemFType>>>();
    if (funcTypeApplied instanceof SystemFType.ForAll) {
      funcCtx = funcCtx.copy();
      while (funcTypeApplied instanceof SystemFType.ForAll forall) {
        var hole = new TypeVar<SystemFType>();
        funcCtx.push(new Entry.ETVarBnd(hole));
        typeArgs.add(Pair.of(forall.boundVar, hole));
        funcTypeApplied = engine.substType(forall.boundVar,
            new SystemFType.EtVar(this.getLocation(), hole), forall.body);
      }
    }
    this.implicitTypeArgs = List.copyOf(typeArgs);

    TypeResult result;
    SystemFType functionType;
    if (funcTypeApplied instanceof SystemFType.Arrow arrow) {
      functionType = arrow;
      var resultTy = arrow.to;
      var currentCtx = funcCtx;
      if (this.arg.isPresent()) {
        var paramCheck = engine.check(currentCtx, this.arg.get(), arrow.from);
        currentCtx = paramCheck.ctx();
      } else {
        // Zero argument application: the unit input placeholder is supplied
        // implicitly!
        var placeholderCheck = engine.subtype(currentCtx,
            new SystemFType.Lit(this.getLocation(), TypeIdent.TYPE_IDENT_UNIT), arrow.from);
        currentCtx = placeholderCheck.ctx();
      }

      result = new TypeResult(resultTy, currentCtx);
    } else if (funcTypeApplied instanceof SystemFType.EtVar etvar) {
      var a = etvar.tyVar;

      var a2 = new TypeVar<SystemFType>();

      var breakRes = funcCtx.break3(
          entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(a));

      var newCtx = new Context(breakRes.left(), funcCtx);
      CheckResult checkRes;
      if (this.arg.isPresent()) {
        var a1 = new TypeVar<SystemFType>();
        var arrowType = new SystemFType.Arrow(this.getLocation(),
            new SystemFType.EtVar(this.getLocation(), a1),
            new SystemFType.EtVar(this.getLocation(), a2));

        newCtx.push(new Entry.SETVarBnd(a, arrowType));
        newCtx.push(new Entry.ETVarBnd(a1));
        newCtx.push(new Entry.ETVarBnd(a2));
        newCtx.extend(breakRes.right());
        functionType = arrowType;

        checkRes = engine.check(
            newCtx,
            this.arg.get(),
            new SystemFType.EtVar(this.getLocation(), a1));
      } else {
        // Zero argument application: the unit input placeholder is supplied
        // implicitly!
        var arrowType = new SystemFType.Arrow(this.getLocation(),
            new SystemFType.Lit(this.getLocation(), TypeIdent.TYPE_IDENT_UNIT),
            new SystemFType.EtVar(this.getLocation(), a2));

        newCtx.push(new Entry.SETVarBnd(a, arrowType));
        newCtx.push(new Entry.ETVarBnd(a2));
        newCtx.extend(breakRes.right());
        functionType = arrowType;

        checkRes = new CheckResult(newCtx);
      }

      result = new TypeResult(new SystemFType.EtVar(this.getLocation(), a2), checkRes.ctx());
    } else {
      throw new TypingException.ApplicationTypeError(this.getLocation());
    }

    // Applied to the context after checking the argument, as the check may have
    // solved existentials of the function type
    this.inferredFunctionType = Optional.of(result.ctx().apply(functionType));

    return new TypeResult(result.type(), result.ctx());
  }

  public List<Expr> getChildren() {
    var children = new ArrayList<Expr>();
    children.add(this.fun);
    this.arg.ifPresent(children::add);
    return List.copyOf(children);
  }

  public boolean equals(Object obj) {
    return obj instanceof App other && this.fun.equals(other.fun) && this.arg.equals(other.arg)
        && super.equals(obj);
  }

  public int hashCode() {
    return Objects.hash(this.fun, this.arg, super.hashCode());
  }

  @Override
  public void replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
    this.fun.replaceSymbol(original, replacement);
    this.arg.ifPresent(arg -> arg.replaceSymbol(original, replacement));
  }

  public Expr copy() {
    return new App(this);
  }

  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
      Context solution) {
    // Like for a TApp, the implicitly instantiated type abstractions take the
    // types their holes were solved to, but only for the function: the
    // argument is instantiated under the plain solution
    var funSolution = solution;
    if (!this.implicitTypeArgs.isEmpty()) {
      funSolution = solution.copy();
      for (var typeArg : this.implicitTypeArgs) {
        var solvedType = solution.apply(new SystemFType.EtVar(this.getLocation(), typeArg.getRight()));
        assert solvedType.isFullySpecified() : "the implicit type argument of " + this + " could not be inferred";
        funSolution.push(new Entry.SVarBnd(typeArg.getLeft(), solvedType));
      }
    }

    var instantiated = new App(this, this.fun.instantiate(engine, env, funSolution),
        this.arg.map(arg -> arg.instantiate(engine, env, solution)));
    // Like the inferred type of any expression, the function type is applied to
    // the final solution: in a chain of applications, existentials may only be
    // solved by an outer application, after this one was inferred
    instantiated.inferredFunctionType = this.inferredFunctionType.map(solution::apply);
    return instantiated;
  }

  @Override
  public List<Expr> getApplications() {
    if (this.arg.isPresent()) {
      return List.of(this.arg.get());
    }
    return List.of();
  }

  @Override
  public Expr getFunction() {
    return this.fun;
  }

  /**
   * A chain of curried applications is a single call: the function type is
   * resolved to the innermost application, which applies the actual function.
   * A zero argument application is a complete call though, applying its result
   * is a new call.
   */
  @Override
  public Optional<SystemFType> getInferredFunctionType() {
    if (this.fun() instanceof App funApp && funApp.arg().isPresent()) {
      return funApp.getInferredFunctionType();
    }
    return this.inferredFunctionType;
  }
}

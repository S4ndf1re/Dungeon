package dgir.core.ir.types.builtin.systemf.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    public App(Expr fun) {
      this.fun = fun;
      this.arg = Optional.empty();
    }

    public App(Location loc, Expr fun) {
      super(loc);
      this.fun = fun;
      this.arg = Optional.empty();
    }

    public App(Expr fun, Expr arg) {
      this.fun = fun;
      this.arg = Optional.of(arg);
    }

    public App(Location loc, Expr fun, Expr arg) {
      super(loc);
      this.fun = fun;
      this.arg = Optional.of(arg);
    }

    public App(App other) {
      super(other);
      this.fun = other.fun;
      this.arg = other.arg;
    }

    public App(App other, Expr fun, Optional<Expr> arg) {
      super(other);
      this.fun = fun;
      this.arg = arg;
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

      TypeResult result;
      if (funcTypeApplied instanceof SystemFType.Arrow arrow) {
        var resultTy = arrow.to;
        var currentCtx = funcInferred.ctx();
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

        var breakRes = funcInferred.ctx().break3(
            entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(a));

        var newCtx = new Context(breakRes.left(), funcInferred.ctx());
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

          checkRes = new CheckResult(newCtx);
        }

        result = new TypeResult(new SystemFType.EtVar(this.getLocation(), a2), checkRes.ctx());
      } else {
        throw new TypingException.ApplicationTypeError(this.getLocation());
      }
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
      return new App(this, this.fun.instantiate(engine, env, solution),
          this.arg.map(arg -> arg.instantiate(engine, env, solution)));
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
  }

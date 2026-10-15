package dgir.core.ir.types.builtin.systemf.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.compatibility.Scope;
import dgir.core.ir.types.traits.IAbstraction;
import dgir.core.ir.types.builtin.systemf.CheckResult;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Entry;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SubtypeResult;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.TypeResult;

public final class Abs extends Expr implements IAbstraction<Expr, SystemFType> {

    private final Optional<Symbol<Expr, SystemFType>> name;
    private final Optional<SystemFType> type;
    private final Expr body;

    public Abs(Symbol<Expr, SystemFType> name, SystemFType type, Expr body) {
      this(Location.UNKNOWN, Optional.of(name), Optional.of(type), body);
    }

    public Abs(Expr body) {
      this(Location.UNKNOWN, Optional.empty(), Optional.empty(), body);
    }

    public Abs(Location loc, Symbol<Expr, SystemFType> name, SystemFType type, Expr body) {
      this(loc, Optional.of(name), Optional.of(type), body);
    }

    public Abs(Location loc, Expr body) {
      this(loc, Optional.empty(), Optional.empty(), body);
    }

    private Abs(Location loc, Optional<Symbol<Expr, SystemFType>> name, Optional<SystemFType> type, Expr body) {
      super(loc);
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

    public Abs(Abs other, Optional<Symbol<Expr, SystemFType>> name, Optional<SystemFType> type, Expr body) {
      super(other);
      this.name = name;
      this.type = type;
      this.body = body;
    }

    @Override
    public final String toString() {
      return "λ" + name.map(Object::toString).orElse("") + type.map(t -> ": " + t).orElse("") + ". " + body;
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var b = new TypeVar<SystemFType>();
      var newCtx = ctx.copy();
      Scope<SystemFType> scope = newCtx.addScope();

      var mark = new Entry.Mark();
      newCtx.push(mark);

      this.name.ifPresent(name -> newCtx.push(new Entry.VarBnd(name, this.type.get())));
      newCtx.push(new Entry.ETVarBnd(b));

      var c1 = engine.check(newCtx, this.body, new SystemFType.EtVar(this.getLocation(), b));

      Context furtherCtx = c1.ctx().copy();
      SystemFType resultType = new SystemFType.EtVar(this.getLocation(), b);

      for (var retType : scope.getAllReturnTypesInScope()) {
        SubtypeResult subTypeRes = engine.subtype(furtherCtx, retType, resultType);
        furtherCtx = subTypeRes.ctx();
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
      // A zero parameter abstraction is a function taking the unit placeholder!
      var resType = new SystemFType.Arrow(this.getLocation(),
          this.type.orElseGet(() -> new SystemFType.Lit(this.getLocation(), TypeIdent.TYPE_IDENT_UNIT)),
          resultType);

      return new TypeResult(resType, finalCtx);
    }

    @Override
    public CheckResult check(
        TypeInference engine,
        Context ctx,
        SystemFType ty) {
      if (ty instanceof SystemFType.Arrow arrow) {
        var newCtx = ctx.copy();
        var mark = new Entry.Mark();
        this.name.ifPresent(name -> newCtx.push(new Entry.VarBnd(name, arrow.from)));

        var bodyCheck = engine.check(newCtx, this.body, arrow.to);
        var break3Result = bodyCheck.ctx().break3(
            entry -> entry instanceof Entry.Mark m && m.equals(mark));

        var finalCtx = new Context(break3Result.left(), bodyCheck.ctx());

        this.setInferredType(
            Optional
                .of(finalCtx.apply(new SystemFType.Arrow(this.getLocation(), arrow.from, finalCtx.apply(arrow.to)))));

        return new CheckResult(finalCtx);
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
      return this.name.map(name -> name.equals(symbol)).orElse(false) || this.body.containsSymbol(symbol);
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
      var newName = this.name.equals(Optional.of(original)) ? Optional.of(replacement) : this.name;
      return new Abs(this, newName, this.type, this.body.replaceSymbol(original, replacement));
    }

    @Override
    public List<Symbol<Expr, SystemFType>> getAbstractionsOverSymbols() {
      if (this.name.isPresent()) {
        return List.of(this.name.get());
      }
      return List.of();
    }

    @Override
    public Expr getAbstractionBody() {
      return this.body;
    }

    public Expr body() {
      return this.body.unwrapOrThis();
    }

    @Override
    public Expr copy() {
      return new Abs(this);
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      return new Abs(this, this.name, this.type, this.body.instantiate(engine, env, solution));
    }
  }

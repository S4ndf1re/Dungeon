package dgir.core.ir.types.builtin.systemf.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Entry;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.TypeResult;

public final class TAbs extends Expr {

    private final TypeVar<SystemFType> variable;
    private final Expr body;

    public TAbs(TypeVar<SystemFType> variable, Expr body) {
      this.variable = variable;
      this.body = body;
    }

    public TAbs(Location loc, TypeVar<SystemFType> variable, Expr body) {
      super(loc);
      this.variable = variable;
      this.body = body;
    }

    public TAbs(TAbs other) {
      super(other);
      this.variable = other.variable;
      this.body = other.body;
    }

    public TAbs(TAbs other, TypeVar<SystemFType> variable, Expr body) {
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
      var resType = new SystemFType.ForAll(this.getLocation(), this.variable, resolvedBodyType);

      return new TypeResult(resType, finalCtx);
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(this.body);
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
    public void replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      this.body.replaceSymbol(original, replacement);
    }

    @Override
    public Expr copy() {
      return new TAbs(this);
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      return this.body.instantiate(engine, env, solution);
    }
  }

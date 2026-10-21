package dgir.core.ir.types.builtin.systemf.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Entry;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.TypeResult;

public final class Let extends Expr {

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

    public Let(Location loc, Symbol<Expr, SystemFType> name, Expr value,
        Expr body) {
      super(loc);
      this.bindings = List.of(Pair.of(name, value));
      this.body = body;
    }

    public Let(Location loc, List<Pair<Symbol<Expr, SystemFType>, Expr>> bindings,
        Expr body) {
      super(loc);
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
      Context newCtx = ctx.copy();
      ArrayList<Triple<Symbol<Expr, SystemFType>, TypeVar<SystemFType>, ExprOrOperator<Expr, SystemFType>>> nonUnified = new ArrayList<>(
          this.bindings.size());

      var mark = new Entry.Mark();
      newCtx.push(mark);

      for (var binding : this.bindings) {
        var typeVar = new TypeVar<SystemFType>();
        nonUnified.add(Triple.of(binding.getLeft(), typeVar, binding.getRight()));
        newCtx.push(new Entry.ETVarBnd(typeVar));
        newCtx.push(new Entry.VarBnd(binding.getLeft(), new SystemFType.EtVar(this.getLocation(), typeVar)));
      }

      for (var binding : nonUnified) {
        var typeVar = binding.getMiddle();
        var expr = binding.getRight();
        var valueInferred = engine.check(newCtx, expr,
            newCtx.apply(new SystemFType.EtVar(this.getLocation(), typeVar)));
        newCtx = valueInferred.ctx().copy();
      }

      var bodyInferred = engine.infer(newCtx, this.body);

      var break3Result = bodyInferred.ctx().break3(
          entry -> entry instanceof Entry.Mark m && m.equals(mark));

      var solvedFinalCtxEntries = new ArrayList<>(break3Result.left());

      solvedFinalCtxEntries.addAll(break3Result.right()
          .stream()
          .filter(entry -> entry instanceof Entry.SETVarBnd)
          .collect(Collectors.toCollection(() -> new ArrayList<Entry>())));
      var finalCtx = new Context(solvedFinalCtxEntries, bodyInferred.ctx());

      return new TypeResult(bodyInferred.type(), finalCtx);
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
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
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
    public Expr copy() {
      return new Let(this);
    }

    @Override
    public List<Expr> getInstantiableChildren() {
      // Bindings are instantiated indirectly via beta-reduction of the
      // referencing variables in the body, never directly!
      return List.of(this.body);
    }

    public Expr body() {
      return this.body.unwrapOrThis();
    }

    @Override
    public void replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      // If the symbol is shadowed by one of the let bindings, do not replace
      // it in the body! The binding symbols themselves are never rewritten.
      // Since the binding values are mutated in place, the immutable pairs
      // and the list of bindings need no rebuild.
      if (this.bindings.stream().anyMatch(bnd -> bnd.getLeft().equals(original))) {
        return;
      }

      for (var bnd : this.bindings) {
        bnd.getRight().replaceSymbol(original, replacement);
      }
      this.body.replaceSymbol(original, replacement);
    }

  }

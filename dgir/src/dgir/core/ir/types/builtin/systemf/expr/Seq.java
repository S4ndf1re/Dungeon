package dgir.core.ir.types.builtin.systemf.expr;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.TypeResult;

/**
   * Seq is a sequence of expressions that infers all expressions sequentially,
   * where the type of the last expression is the type of the whole sequence.
   *
   * <p>
   * This is mainly considered to be useful with sequential definitions, i.e.
   * normal blocks, mirroring the {@code ExprSeq} of the Algorithm W dialect.
   */
  public final class Seq extends Expr {

    private List<Expr> expressions;

    public Seq(Location loc, Expr expr) {
      super(loc);
      this.expressions = List.of(expr);
    }

    public Seq(List<Expr> exprs) {
      this.expressions = List.copyOf(exprs);
    }

    public Seq(Location loc, List<Expr> exprs) {
      super(loc);
      this.expressions = List.copyOf(exprs);
    }

    public Seq(Expr... exprs) {
      this.expressions = List.of(exprs);
    }

    public Seq(Location loc, Expr... exprs) {
      super(loc);
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
    public TypeResult infer(TypeInference engine, Context ctx) {
      var currentCtx = ctx.copy();
      SystemFType lastType = new SystemFType.Lit(this.getLocation(), TypeIdent.TYPE_IDENT_UNIT);

      for (var expr : this.expressions) {
        var infRes = engine.infer(currentCtx, expr);
        currentCtx = infRes.ctx();
        lastType = currentCtx.apply(infRes.type());
      }

      return new TypeResult(lastType, currentCtx);
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
    public void replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      for (var expr : this.expressions) {
        expr.replaceSymbol(original, replacement);
      }
    }

    @Override
    public Expr copy() {
      return new Seq(this);
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      return new Seq(this, this.expressions.stream().map(e -> e.instantiate(engine, env, solution)).toList());
    }
  }

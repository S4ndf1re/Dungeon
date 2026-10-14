package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Env;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.InferResult;
import dgir.core.ir.types.builtin.algorithmw.Subst;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;

public class Seq extends Expr {
    private List<Expr> expressions;

    public List<Expr> expressions() {
      return this.expressions.stream().map(Expr::unwrapOrThis).toList();
    }

    public Seq(Expr expr) {
      super();
      this.expressions = List.of(expr);
    }

    public Seq(Location loc, Expr expr) {
      super(loc);
      this.expressions = List.of(expr);
    }

    public Seq(List<Expr> exprs) {
      super();
      this.expressions = List.copyOf(exprs);
    }

    public Seq(Location loc, List<Expr> exprs) {
      super(loc);
      this.expressions = List.copyOf(exprs);
    }

    public Seq(Seq other) {
      super(other);
      this.expressions = List.copyOf(other.expressions);
    }

    public Seq(Seq other, List<Expr> exprs) {
      super(other);
      this.expressions = List.copyOf(exprs);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.expressions, super.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Seq seq && this.expressions.equals(seq.expressions) && super.equals(obj);
    }

    @Override
    public List<Expr> getChildren() {
      return List.copyOf(this.expressions);
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      return new Seq(this, this.expressions.stream().map(e -> e.replaceSymbol(original, replacement)).toList());
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.expressions.stream().anyMatch(e -> e.containsSymbol(symbol));
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      AlgorithmWType lastType = new AlgorithmWType.LitType(this.getLocation(), TypeIdent.TYPE_IDENT_UNIT);
      for (var expr : this.expressions) {
        var infRes = engine.infer(expr, env);
        lastType = infRes.type();
      }

      var resType = lastType;
      return new InferResult(resType);
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      return new Seq(this, this.expressions.stream().map(e -> e.instantiate(engine, env, solution)).toList());
    }

    @Override
    public Expr copy() {
      return new Seq(this);
    }

  }

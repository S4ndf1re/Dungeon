package dgir.core.ir.types.builtin.hmx.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.Env;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Subst;
import dgir.core.ir.types.builtin.hmx.TypeInference;

public class Seq extends HMXExpr {
    private List<HMXExpr> expressions;

    public List<HMXExpr> expressions() {
      return this.expressions.stream().map(HMXExpr::unwrapOrThis).toList();
    }

    public Seq(HMXExpr expr) {
      super();
      this.expressions = List.of(expr);
    }

    public Seq(Location loc, HMXExpr expr) {
      super(loc);
      this.expressions = List.of(expr);
    }

    public Seq(List<HMXExpr> exprs) {
      super();
      this.expressions = List.copyOf(exprs);
    }

    public Seq(Location loc, List<HMXExpr> exprs) {
      super(loc);
      this.expressions = List.copyOf(exprs);
    }

    public Seq(Seq other) {
      super(other);
      this.expressions = List.copyOf(other.expressions);
    }

    public Seq(Seq other, List<HMXExpr> exprs) {
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
    public List<HMXExpr> getChildren() {
      return List.copyOf(this.expressions);
    }

    @Override
    public void replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
      for (var expr : this.expressions) {
        expr.replaceSymbol(original, replacement);
      }
    }

    @Override
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      return this.expressions.stream().anyMatch(e -> e.containsSymbol(symbol));
    }

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      var sequentialTypes = new ArrayList<TypeVar<HMXType>>();
      var sequentialConstraints = new ArrayList<Constraint>();
      for (var expr : this.expressions) {
        var freshTypeVar = new TypeVar<HMXType>();
        var infRes = engine.generate(expr, env, new HMXType.Var(this.getLocation(), freshTypeVar));
        sequentialTypes.add(freshTypeVar);
        sequentialConstraints.add(infRes.constr());
      }

      HMXType resType = new HMXType.LitType(this.getLocation(), TypeIdent.TYPE_IDENT_UNIT);
      if (!sequentialTypes.isEmpty()) {
        resType = new HMXType.Var(this.getLocation(), sequentialTypes.getLast());
      }

      return new GenerateResult(
          new Constraint.Exists(sequentialTypes,
              new Constraint.And(new Constraint.Equal(resType, type), new Constraint.And(sequentialConstraints))));
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      return new Seq(this, this.expressions.stream().map(e -> e.instantiate(engine, env, solution)).toList());
    }

    @Override
    public HMXExpr copy() {
      return new Seq(this);
    }

  }

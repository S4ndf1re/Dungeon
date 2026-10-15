package dgir.core.ir.types.builtin.systemf.expr;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.TypeResult;

public final class LitExpr extends Expr {

    private final Literal lit;

    public LitExpr(Literal lit) {
      this.lit = lit;
    }

    public LitExpr(Location loc, Literal lit) {
      super(loc);
      this.lit = lit;
    }

    public LitExpr(LitExpr other) {
      super(other);
      this.lit = other.lit;
    }

    @Override
    public final String toString() {
      return lit.toString();
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var res = engine.generalNominalTypeToInferenceType(lit.toParameterizedNominalType(), Optional.of(ctx));
      return new TypeResult(
          res.getLeft(),
          ((Context) res.getRight().get()).copy()); // This is safe, as the ctx is provided as a Some(ctx)
    }

    @Override
    public List<Expr> getChildren() {
      return List.of();
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return false;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof LitExpr other && this.lit.equals(other.lit) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.lit, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      return this;
    }

    @Override
    public Expr copy() {
      return new LitExpr(this);
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      return this;
    }
  }

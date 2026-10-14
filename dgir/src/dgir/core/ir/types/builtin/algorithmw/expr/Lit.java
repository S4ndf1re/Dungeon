package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Env;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.InferResult;
import dgir.core.ir.types.builtin.algorithmw.Subst;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;

public final class Lit extends Expr {

    private Literal value;

    public Literal value() {
      return this.value;
    }

    public Lit(Literal value) {
      this.value = value;
    }

    public Lit(Location loc, Literal value) {
      super(loc);
      this.value = value;
    }

    @SuppressWarnings("unused")
    private Lit(Lit other) {
      super(other);
      this.value = other.value;
    }

    @Override
    public final String toString() {
      return value.toString();
    }

    @Override
    public List<Expr> getChildren() {
      return List.of();
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return false;
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      var algoWType = engine.generalNominalTypeToInferenceType(value.toParameterizedNominalType(), null);
      return new InferResult(algoWType.getLeft());
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Lit lit && this.value.equals(lit.value) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.value, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      return this;
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      return this;
    }

    @Override
    public Expr copy() {
      return new Lit(this);
    }
  }

package dgir.core.ir.types.builtin.hmx.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.Env;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Subst;
import dgir.core.ir.types.builtin.hmx.TypeInference;

public final class Lit extends HMXExpr {

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
    public List<HMXExpr> getChildren() {
      return List.of();
    }

    @Override
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      return false;
    }

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      var hmxType = engine.generalNominalTypeToInferenceType(value.toParameterizedNominalType(), null);
      return new GenerateResult(
          new Constraint.Equal(type, hmxType.getLeft()));
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
    public void replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
      // Nothing to replace, as literals do not reference any symbols!
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      return this;
    }

    @Override
    public HMXExpr copy() {
      return new Lit(this);
    }
  }

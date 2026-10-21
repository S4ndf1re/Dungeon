package dgir.core.ir.types.builtin.hmx.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.traits.IVariable;
import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.Env;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Subst;
import dgir.core.ir.types.builtin.hmx.TypeInference;

public final class Var extends HMXExpr implements IVariable<HMXExpr, HMXType> {

    private Symbol<HMXExpr, HMXType> name;

    public Symbol<HMXExpr, HMXType> name() {
      return this.name;
    }

    public Var(Symbol<HMXExpr, HMXType> name) {
      this.name = name;
    }

    public Var(Location loc, Symbol<HMXExpr, HMXType> name) {
      super(loc);
      this.name = name;
    }

    @SuppressWarnings("unused")
    private Var(Var other) {
      super(other);
      this.name = other.name;
    }

    private Var(Var other, Symbol<HMXExpr, HMXType> name) {
      super(other);
      this.name = name;
    }

    @Override
    public final String toString() {
      return name + "";
    }

    @Override
    public Symbol<HMXExpr, HMXType> getReferencedVariable() {
      return this.name;
    }

    @Override
    public List<HMXExpr> getChildren() {
      return List.of();
    }

    @Override
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      return this.name.equals(symbol);
    }

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      return new GenerateResult(
          new Constraint.Inst(this.name, type));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Var other && this.name.equals(other.name) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.name, super.hashCode());
    }

    @Override
    public void replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
      if (this.name.equals(original)) {
        this.name = replacement;
      }
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      // Nothing to instantiate;
      return this;
    }

    @Override
    public HMXExpr copy() {
      return new Var(this);
    }
  }

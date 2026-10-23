package dgir.core.ir.types.builtin.hmx.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.Env;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Subst;
import dgir.core.ir.types.builtin.hmx.TypeInference;

public class Return extends HMXExpr {
    private HMXExpr value;

    public HMXExpr value() {
      return this.value.unwrapOrThis();
    }

    public Return(HMXExpr value) {
      this.value = value;
    }

    public Return(Location loc, HMXExpr value) {
      super(loc);
      this.value = value;
    }

    public Return(Return other) {
      super(other);
      this.value = other.value;
    }

    public Return(Return other, HMXExpr value) {
      super(other);
      this.value = value;
    }

    @Override
    public String toString() {
      return "return " + this.value;
    }

    @Override
    public List<HMXExpr> getChildren() {
      return List.of(this.value.getExpr());
    }


    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      GenerateResult inferred = engine.generate(this.value, env, type);
      var returnType = env.topScope().flatMap(scope -> scope.getReturnType());

      if (returnType.isPresent()) {
        // Relate the returned value to the function's result right here. The
        // returned value is usually a let-bound local; constraining it outside
        // that let would refer to the local scheme's quantified variable
        // instead of the value actually returned. The result variable belongs
        // to the enclosing function, so the local scheme cannot quantify it.
        return new GenerateResult(new Constraint.And(
            new Constraint.Equal(type, returnType.get()),
            inferred.constr()));
      }

      return new GenerateResult(inferred.constr());
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Return other && this.value.equals(other.value) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.value, super.hashCode());
    }

    @Override
    public void replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
      this.value.replaceSymbol(original, replacement);
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      return new Return(this, this.value.instantiate(engine, env, solution));
    }

    @Override
    public HMXExpr copy() {
      return new Return(this);
    }
  }

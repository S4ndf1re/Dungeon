package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Env;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.InferResult;
import dgir.core.ir.types.builtin.algorithmw.Subst;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;

public class Return extends Expr {
    private Expr value;

    public Expr value() {
      return this.value.unwrapOrThis();
    }

    public Return(Expr value) {
      this.value = value;
    }

    public Return(Location loc, Expr value) {
      super(loc);
      this.value = value;
    }

    public Return(Return other) {
      super(other);
      this.value = other.value;
    }

    public Return(Return other, Expr value) {
      super(other);
      this.value = value;
    }

    @Override
    public String toString() {
      return "return " + this.value;
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(this.value.getExpr());
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.value.containsSymbol(symbol);
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      InferResult inferred = engine.infer(this.value, env);
      var topScope = env.topScope();

      var inferredType = inferred.type();
      if (topScope.isPresent()) {
        topScope.get().addReturnType(inferredType);
      }

      return new InferResult(inferredType);
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
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      return new Return(this, this.value.replaceSymbol(original, replacement));
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      return new Return(this, this.value.instantiate(engine, env, solution));
    }

    @Override
    public Expr copy() {
      return new Return(this);
    }
  }

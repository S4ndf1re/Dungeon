package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.traits.IVariable;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Env;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.InferResult;
import dgir.core.ir.types.builtin.algorithmw.Scheme;
import dgir.core.ir.types.builtin.algorithmw.Subst;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;

public final class Var extends Expr implements IVariable<Expr, AlgorithmWType> {

    private Symbol<Expr, AlgorithmWType> name;

    public Symbol<Expr, AlgorithmWType> name() {
      return this.name;
    }

    public Var(Symbol<Expr, AlgorithmWType> name) {
      this.name = name;
    }

    public Var(Location loc, Symbol<Expr, AlgorithmWType> name) {
      super(loc);
      this.name = name;
    }

    @SuppressWarnings("unused")
    private Var(Var other) {
      super(other);
      this.name = other.name;
    }

    private Var(Var other, Symbol<Expr, AlgorithmWType> name) {
      super(other);
      this.name = name;
    }

    @Override
    public final String toString() {
      return name + "";
    }

    @Override
    public Symbol<Expr, AlgorithmWType> getReferencedVariable() {
      return this.name;
    }

    @Override
    public List<Expr> getChildren() {
      return List.of();
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.name.equals(symbol);
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      Scheme scheme = env.get(name);
      if (scheme != null) {
        AlgorithmWType instantiated = scheme.instantiate(engine);
        return new InferResult(instantiated);
      } else {
        throw new TypingException.UnknownVariable(name);
      }
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
    public void replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      if (this.name.equals(original)) {
        this.name = replacement;
      }
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      // Nothing to instantiate;
      return this;
    }

    @Override
    public Expr copy() {
      return new Var(this);
    }
  }

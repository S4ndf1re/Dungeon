package dgir.core.ir.types.builtin.systemf.expr;

import java.util.List;
import java.util.Objects;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Entry;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.TypeResult;
import dgir.core.ir.types.traits.IVariable;

public final class Var extends Expr implements IVariable<Expr, SystemFType> {

    private Symbol<Expr, SystemFType> name;

    public Var(Symbol<Expr, SystemFType> name) {
      this.name = name;
    }

    public Var(Location loc, Symbol<Expr, SystemFType> name) {
      super(loc);
      this.name = name;
    }

    public Var(Var other) {
      super(other);
      this.name = other.name;
    }

    public Var(Var other, Symbol<Expr, SystemFType> name) {
      super(other);
      this.name = name;
    }

    @Override
    public final String toString() {
      return name + "";
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      var boundVariable = ctx.find(
          entry -> entry instanceof Entry.VarBnd bnd && bnd.tmVar().equals(this.name));

      if (boundVariable.isPresent()) {
        var varBnd = (Entry.VarBnd) boundVariable.get();
        return new TypeResult(varBnd.type(), ctx.copy());
      }

      throw new TypingException.UnboundVariable(this.name);
    }

    @Override
    public List<Expr> getChildren() {
      return List.of();
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return this.name.equals(symbol);
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
    public void replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      if (this.name.equals(original)) {
        this.name = replacement;
      }
    }

    @Override
    public Expr copy() {
      return new Var(this);
    }

    @Override
    public Symbol<Expr, SystemFType> getReferencedVariable() {
      return this.name;
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      // Nothing to instantiate;
      return this;
    }
  }

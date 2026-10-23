package dgir.core.ir.types.builtin.hmx.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.Env;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Subst;
import dgir.core.ir.types.builtin.hmx.TypeInference;

public final class Tuple extends HMXExpr {

    private final List<HMXExpr> elements;

    public List<HMXExpr> elements() {
      return this.elements.stream().map(HMXExpr::unwrapOrThis).toList();
    }

    @Override
    public HMXExpr copy() {
      return new Tuple(this);
    }

    public Tuple(List<HMXExpr> elements) {
      this.elements = elements;
    }

    public Tuple(Location loc, List<HMXExpr> elements) {
      super(loc);
      this.elements = elements;
    }

    public Tuple(HMXExpr... elements) {
      ArrayList<HMXExpr> elems = new ArrayList<>();
      for (var elem : elements) {
        elems.add(elem);
      }

      this.elements = List.copyOf(elems);
    }

    public Tuple(Location loc, HMXExpr... elements) {
      super(loc);
      ArrayList<HMXExpr> elems = new ArrayList<>();
      for (var elem : elements) {
        elems.add(elem);
      }

      this.elements = List.copyOf(elems);
    }

    public Tuple(Tuple other) {
      super(other);
      this.elements = List.copyOf(other.elements);
    }

    public Tuple(Tuple other, List<HMXExpr> elements) {
      super(other);
      this.elements = List.copyOf(elements);
    }

    @Override
    public final String toString() {
      return ("(" +
          elements
              .stream()
              .map(Object::toString)
              .collect(Collectors.joining(", "))
          +
          ")");
    }

    @Override
    public List<HMXExpr> getChildren() {
      return this.elements.stream().map(ExprOrOperator::getExpr).toList();
    }


    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      ArrayList<TypeVar<HMXType>> typeVars = new ArrayList<>();
      ArrayList<HMXType> types = new ArrayList<>();
      ArrayList<Constraint> constraints = new ArrayList<>();
      Env currentEnv = env.copy();

      for (var expr : elements) {
        var freshTypVar = new TypeVar<HMXType>();
        typeVars.add(freshTypVar);
        var res = engine.generate(expr, currentEnv, new HMXType.Var(this.getLocation(), freshTypVar));
        types.add(new HMXType.Var(this.getLocation(), freshTypVar));
        constraints.add(res.constr());
      }

      var resultType = new HMXType.Tuple(this.getLocation(), List.copyOf(types));

      return new GenerateResult(
          new Constraint.Exists(typeVars,
              new Constraint.And(new Constraint.Equal(resultType, type), new Constraint.And(constraints))));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Tuple other && this.elements.equals(other.elements) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.elements, super.hashCode());
    }

    @Override
    public void replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
      for (var elem : this.elements) {
        elem.replaceSymbol(original, replacement);
      }
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      return new Tuple(this, this.elements.stream().map(elem -> elem.instantiate(engine, env, solution)).toList());
    }
  }

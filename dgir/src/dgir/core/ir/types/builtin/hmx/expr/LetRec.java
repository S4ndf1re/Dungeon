package dgir.core.ir.types.builtin.hmx.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.hmx.Constraint.LetBinding;

import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.Env;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Scheme;
import dgir.core.ir.types.builtin.hmx.Subst;
import dgir.core.ir.types.builtin.hmx.TypeInference;
/**
   * ExprLetRec is a multi let statement that infers multiple let
   * expressions at the
   * same time.
   *
   * <p>
   * This is mainly considered to be useful with function definitions, and thus
   * should be expected to be only used with fn defs
   *
   * <p>
   * As all expresions are considered global, to use this with ordered expressions
   * and their values, and allowing shadowing, LetExprSeq are required!
   *
   * @implNote The lets are considered global scope: i.e. the let values are first
   *           assigned to a new TypeVar, that is then unified with the
   *           inferred assigned type.
   *
   *
   */
  public final class LetRec extends HMXExpr {

    private List<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings;
    private HMXExpr body;

    public LetRec(Symbol<HMXExpr, HMXType> param, HMXExpr value,
        HMXExpr body) {
      this.bindings = List.of(Pair.of(param, value));
      this.body = body;
    }

    public LetRec(Location loc, Symbol<HMXExpr, HMXType> param, HMXExpr value,
        HMXExpr body) {
      super(loc);
      this.bindings = List.of(Pair.of(param, value));
      this.body = body;
    }

    public LetRec(List<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings,
        HMXExpr body) {
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public LetRec(Location loc,
        List<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings,
        HMXExpr body) {
      super(loc);
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public LetRec(LetRec other) {
      super(other);
      this.bindings = List.copyOf(other.bindings);
      this.body = other.body;
    }

    public LetRec(LetRec other, List<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings,
        HMXExpr body) {
      super(other);
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public List<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings() {
      return this.bindings.stream()
          .map(bnd -> Pair.of(bnd.getLeft(), bnd.getRight().unwrapOrThis())).toList();
    }

    public HMXExpr body() {
      return this.body.unwrapOrThis();
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      var newLetExpr = new LetRec(this,
          List.copyOf(this.bindings),
          new Lit(new Literal.Unit()));

      // This line is key, as the defining scope expression, in this case `newLetExpr`
      // is bound to the scope
      var newEnv = new InstEnv<HMXExpr, HMXType, Subst>(env, newLetExpr);
      for (int i = 0; i < this.bindings.size(); i++) {
        var bnd = this.bindings.get(i);
        newEnv.put(bnd.getLeft(), bnd.getRight(), i);
      }

      newLetExpr.body = this.body.instantiate(engine, newEnv, solution);
      return newLetExpr;
    }

    @Override
    public HMXExpr copy() {
      return new LetRec(this);
    }

    @Override
    public List<HMXExpr> getChildren() {
      var list = new ArrayList<HMXExpr>();
      this.bindings.forEach(bnd -> list.add(bnd.getRight().getExpr()));
      list.add(this.body.getExpr());
      return List.copyOf(list);
    }

    @Override
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      return this.bindings.stream().anyMatch(bnd -> bnd.getLeft().equals(symbol)) || this.body.containsSymbol(symbol);
    }

    @Override
    public List<HMXExpr> getInstantiableChildren() {
      return List.of(this.body);
    }

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      var bindings = new ArrayList<LetBinding>();
      for (var binding : this.bindings) {
        var param = binding.getLeft();
        var value = binding.getRight();
        var freshTypeVar = new TypeVar<HMXType>();

        GenerateResult res1 = engine.generate(value, env, new HMXType.Var(this.getLocation(), freshTypeVar));
        bindings.add(
            new LetBinding(param,
                new Scheme(List.of(freshTypeVar),
                    new HMXType.Var(this.getLocation(), freshTypeVar.find()), res1.constr())));
      }

      GenerateResult res2 = engine.generate(body, env, type);

      return new GenerateResult(
          new Constraint.LetRec(bindings, res2.constr()));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof LetRec other && this.bindings.equals(other.bindings)
          && this.body.equals(other.body)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.bindings, this.body, super.hashCode());
    }

    @Override
    public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
      // If symbol is shadowed by the env created by this let binding, do not
      // overwrite the value!
      if (this.bindings.stream().anyMatch(bnd -> bnd.getLeft().equals(original))) {
        return this;
      }

      var newLetExpr = new LetRec(this, this.bindings.stream()
          .map(binding -> Pair.of(binding.getLeft(), binding.getRight().replaceSymbol(original, replacement)))
          .toList(),
          this.body.replaceSymbol(original, replacement));

      // Children of the new subtree still reference THIS instance as their parent
      // scope. Re-point them, or scope-based block reconstruction finds nothing.
      newLetExpr.replaceParentScopeReferences(this, newLetExpr);
      return newLetExpr;
    }
  }

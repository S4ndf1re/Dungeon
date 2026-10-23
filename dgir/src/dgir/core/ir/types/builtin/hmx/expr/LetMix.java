package dgir.core.ir.types.builtin.hmx.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
 * A mixed let/let-rec binding group, mirroring OCaml's distinction between
 * {@code let} and {@code let rec}: a binding flagged recursive participates
 * in the group's monomorphic core (mutual and forward references inside the
 * group resolve against the monomorphic shell), and its scheme generalizes
 * when the group ends. A non-recursive binding behaves like a plain {@code
 * let}: it generalizes immediately after its own definition, so every later
 * binding — and the body — see its polymorphic scheme.
 *
 * <p>
 * Bindings are processed in their given order, so ordinary top-level
 * definition order is preserved.
 */
public final class LetMix extends HMXExpr {

  /**
   * One definition of the group: the bound symbol, its value expression and
   * the recursion marker.
   */
  public record MixDef(Symbol<HMXExpr, HMXType> name, HMXExpr value, boolean recursive) {
  }

  private List<MixDef> bindings;
  private HMXExpr body;

  public LetMix(List<MixDef> bindings, HMXExpr body) {
    this.bindings = List.copyOf(bindings);
    this.body = body;
  }

  public LetMix(Location loc, List<MixDef> bindings, HMXExpr body) {
    super(loc);
    this.bindings = List.copyOf(bindings);
    this.body = body;
  }

  public LetMix(LetMix other) {
    super(other);
    this.bindings = List.copyOf(other.bindings);
    this.body = other.body;
  }

  public LetMix(LetMix other, List<MixDef> bindings, HMXExpr body) {
    super(other);
    this.bindings = List.copyOf(bindings);
    this.body = body;
  }

  public List<MixDef> bindings() {
    return List.copyOf(this.bindings);
  }

  public HMXExpr body() {
    return this.body.unwrapOrThis();
  }

  @Override
  public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env,
      Subst solution) {
    var newLetExpr = new LetMix(this,
        List.copyOf(this.bindings),
        new Lit(new Literal.Unit()));

    var newEnv = new InstEnv<HMXExpr, HMXType, Subst>(env, newLetExpr, solution);
    for (int i = 0; i < this.bindings.size(); i++) {
      var bnd = this.bindings.get(i);
      newEnv.put(bnd.name(), bnd.value(), i);
    }

    newLetExpr.body = this.body.instantiate(engine, newEnv, solution);
    return newLetExpr;
  }

  @Override
  public HMXExpr copy() {
    return new LetMix(this);
  }

  @Override
  public List<HMXExpr> getChildren() {
    var list = new ArrayList<HMXExpr>();
    this.bindings.forEach(bnd -> list.add(bnd.value().getExpr()));
    list.add(this.body.getExpr());
    return List.copyOf(list);
  }

  @Override
  public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
    // The bound names are not children; everything below is.
    return this.bindings.stream().anyMatch(bnd -> bnd.name().equals(symbol)) || super.containsSymbol(symbol);
  }

  @Override
  public List<HMXExpr> getInstantiableChildren() {
    return List.of(this.body);
  }

  @Override
  public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
    var mixBindings = new ArrayList<Constraint.MixBinding>();
    for (var binding : this.bindings) {
      // The binding's type variable must carry the current level: it is
      // referenced by the scheme's constraint leaves, so generalize has to
      // quantify it. A level-less variable (-1) would escape quantification
      // and be unified by every scheme instance, corrupting the shared
      // original.
      var freshTypeVar = new TypeVar<HMXType>(engine.getCurrentLevel());

      GenerateResult res1 = engine.generate(binding.value(), env,
          new HMXType.Var(this.getLocation(), freshTypeVar));
      mixBindings.add(new Constraint.MixBinding(
          new LetBinding(binding.name(),
              new Scheme(List.of(freshTypeVar),
                  new HMXType.Var(this.getLocation(), freshTypeVar.find()), res1.constr())),
          binding.recursive()));
    }

    GenerateResult res2 = engine.generate(body, env, type);

    return new GenerateResult(
        new Constraint.LetMix(mixBindings, res2.constr()));
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof LetMix other && this.bindings.equals(other.bindings)
        && this.body.equals(other.body)
        && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.bindings, this.body, super.hashCode());
  }

  @Override
  public void replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
    // If a binding shadows the symbol, do not overwrite its value!
    if (this.bindings.stream().anyMatch(bnd -> bnd.name().equals(original))) {
      return;
    }

    this.body.replaceSymbol(original, replacement);
    this.bindings.forEach(bnd -> bnd.value().replaceSymbol(original, replacement));
  }

  @Override
  public String toString() {
    var bindingText = this.bindings.stream()
        .map(bnd -> (bnd.recursive() ? "rec " : "") + bnd.name() + " = " + bnd.value())
        .collect(Collectors.joining(" and "));
    return "let " + bindingText + " in " + this.body();
  }
}

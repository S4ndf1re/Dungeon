package dgir.core.ir.types.builtin.hmx;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.types.TypeVar;

public final class Scheme {
  private List<TypeVar<HMXType>> vars;
  private HMXType type;
  private Constraint constraint;

  public Scheme(List<TypeVar<HMXType>> vars, HMXType type, Constraint constraint) {
    this.vars = vars;
    this.type = type;
    this.constraint = constraint;
  }

  public List<TypeVar<HMXType>> vars() {
    return List.copyOf(this.vars);
  }

  public HMXType type() {
    return this.type;
  }

  public Constraint constr() {
    return this.constraint;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Scheme sch && this.vars.equals(sch.vars) && this.type.equals(sch.type)
        && this.constraint.equals(sch.constraint);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.type, this.vars, this.constraint);
  }

  /**
   * Apply the subst to this scheme. First filter all bound variables from the
   * subst, then apply
   * the filtered subst to the type.
   *
   * @param subst the subst to apply with
   * @return the applied scheme where subst is applied to this
   */
  public Scheme apply(Subst subst) {
    var filtered = new HashMap<TypeVar<HMXType>, HMXType>(subst.types());

    for (var s : this.vars) {
      filtered.remove(s);
    }

    var applied = new Subst(filtered);
    var newType = applied.apply(this.type);
    return new Scheme(this.vars, newType, this.constraint.applySubst(applied));
  }

  @Override
  public final String toString() {
    return ("[{" +
        this.vars
            .stream()
            .map(TypeVar::find)
            .map(Object::toString)
            .collect(Collectors.joining(", "))
        +
        "}, " +
        this.type.deref() + " ~> " + this.constraint +
        "]");
  }

  /**
   * Find all non bound type variables
   *
   * @return
   */
  public Set<TypeVar<HMXType>> freeTypeVars() {
    var ftv = this.type.freeTypeVars();
    var set = new HashSet<TypeVar<HMXType>>(ftv);
    set.addAll(this.constraint.freeTypeVars());
    set.removeAll(this.vars);
    return Set.copyOf(set);
  }

  public Subst toSubst(TypeInference engine) {
    Subst s = Subst.newEmpty();

    for (var typeVar : this.vars) {
      // Instance variables live at the current level, so an enclosing let
      // generalizes the instances created inside its own definition. Inheriting
      // the original's (outer) level would keep them out of that
      // generalization and share them across all instances of the enclosing
      // binding.
      var level = engine.getCurrentLevel();
      var fresh = new TypeVar<HMXType>(level);
      if (typeVar.getAssigendType().isEmpty()) {
        var lowerBounds = typeVar.getLowerBounds().stream().map(s::apply).toList();
        var upperBounds = typeVar.getUpperBounds().stream().map(s::apply).toList();
        fresh.assignLowerBounds(lowerBounds);
        fresh.assignUpperBounds(upperBounds);
        s.types().put(typeVar.find(), new HMXType.Var(fresh));
      }
    }

    return s;
  }

  public Pair<HMXType, Constraint> instantiate(TypeInference engine) {
    var subst = this.toSubst(engine);

    var appliedType = subst.apply(this.type);
    var appliedConstr = this.constr().applySubst(subst);


    return Pair.of(appliedType, appliedConstr);
  }
}

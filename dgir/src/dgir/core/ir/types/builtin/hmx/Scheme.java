package dgir.core.ir.types.builtin.hmx;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
            .map(Object::toString)
            .collect(Collectors.joining(", "))
        +
        "}, " +
        this.type +
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
    set.removeAll(this.vars);
    return Set.copyOf(set);
  }

  public Subst toSubst(TypeInference engine) {
    Subst s = Subst.newEmpty();

    for (var typeVar : this.vars) {
      var fresh = new TypeVar<HMXType>(engine.getCurrentLevel());
      s.types().put(typeVar, new HMXType.Var(fresh));
    }

    return s;
  }

  public Pair<HMXType, Constraint> instantiate(TypeInference engine) {
    var subst = this.toSubst(engine);
    return Pair.of(subst.apply(this.type), this.constr().applySubst(subst));
  }
}

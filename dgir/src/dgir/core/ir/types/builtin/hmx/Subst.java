package dgir.core.ir.types.builtin.hmx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.traits.IInstantiable.SolutionContext;

public final record Subst(HashMap<TypeVar<HMXType>, HMXType> types)
    implements SolutionContext<HMXExpr, HMXType, TypeInference, Subst> {

  public static Subst newEmpty() {
    return new Subst(new HashMap<>());
  }

  public static Subst newSingleton(TypeVar<HMXType> key, HMXType type) {
    var map = new HashMap<TypeVar<HMXType>, HMXType>();
    map.put(key, type);
    return new Subst(map);
  }

  @Override
  public final String toString() {
    return ("{" +
        types
            .entrySet()
            .stream()
            .map(entry -> entry.getKey() + " -> " + entry.getValue())
            .collect(Collectors.joining(", "))
        +
        "}");
  }

  @Override
  public HMXType apply(HMXType type) {
    this.dropCycles();
    return this.applyInner(type);
  }

  public HMXType applyInner(HMXType type) {
    type = type.deref();
    if (type instanceof HMXType.Var tyVar) {
      if (tyVar.tyVar.getAssigendType().isPresent()) {
        return apply(tyVar.tyVar.getAssigendType().get());
      }

      var t = types.get(tyVar.tyVar.find());
      if (t != null) {
        var resType = apply(t).deref();
        if (resType instanceof HMXType.Var resTypeVar) {
          resTypeVar.tyVar.replaceUpperBounds(resTypeVar.tyVar.getUpperBounds().stream().map(ub -> apply(ub)).toList());
          resTypeVar.tyVar.replaceLowerBounds(resTypeVar.tyVar.getLowerBounds().stream().map(lb -> apply(lb)).toList());
        }
        return resType.deref();
      } else {
        return type;
      }
    } else if (type instanceof HMXType.Arrow arrow) {
      return new HMXType.Arrow(apply(arrow.from), apply(arrow.to)).deref();
    } else if (type instanceof HMXType.LitType) {
      return type.deref();
    } else if (type instanceof HMXType.Tuple tuple) {
      return new HMXType.Tuple(tuple.elements.stream().map(this::apply).toList());
    } else {
      throw new TypingException.UnknownType(type);
    }
  }

  public void dropCycles() {
    ArrayList<TypeVar<HMXType>> toDrop = new ArrayList<>();
    for (var entry : this.types.keySet()) {
      if (new HMXType.Var(entry).equals(this.types.get(entry))) {
        toDrop.add(entry);
      }
    }

    for (var entry : toDrop) {
      this.types.remove(entry);
    }
  }

  /**
   * Compose this subst with another subst, by first relaying other through this
   *
   * @param other the other subst to compose with
   * @return the composed subst
   */
  public Subst compose(Subst other) {
    this.dropCycles();
    other.dropCycles();

    var otherTypes = new HashMap<TypeVar<HMXType>, HMXType>(other.types);
    otherTypes
        .entrySet()
        .stream()
        .forEach(entry -> entry.setValue(this.apply(entry.getValue())));

    this.types
        .entrySet()
        .stream()
        .forEach(entry -> otherTypes.putIfAbsent(entry.getKey(), entry.getValue()));

    var newSubst = new Subst(otherTypes);
    newSubst.dropCycles();
    return newSubst;
  }

  /**
   * Instantiates the referenced definition for one reference during
   * reconstruction: every free variable of its type gets a fresh copy, which
   * is bound to the call-site type ({@link #matchFresh}).
   *
   * <p>
   * Only the variables of the type are instantiated. Variables that occur only
   * in the definition's scheme constraint (e.g. the results of determine
   * leaves) are not copied, and those leaves are not resolved per instance, so
   * they are read from the original definition. See the FIXME on the final
   * convergence pass in {@link Constraint.LetMix#solve}.
   */
  @Override
  public Subst expand(TypeInference engine, HMXExpr target, HMXType targetType) {
    var ty1 = target.getInferredType().get().deref();
    var ftv = ty1.freeTypeVars();

    var scheme = new Scheme(ftv.stream().map(v -> v.find()).toList(), ty1, new Constraint.Trivial());
    var s = scheme.toSubst(engine);

    var newType = s.apply(ty1);
    var fresh = new java.util.HashSet<TypeVar<HMXType>>();
    s.types.values().forEach(value -> {
      if (value.deref() instanceof HMXType.Var freshVar) {
        fresh.add(freshVar.tyVar.find());
      }
    });
    matchFresh(newType, targetType, fresh);

    // The fresh variables (a2, b2, ...) were bound to the call-site type, so
    // deref'ing their entries collapses the indirection:
    // {a1 ↦ Var(a2), ...} becomes {a1 ↦ int, b1 ↦ float, ...}.
    for (var key : Set.copyOf(s.types.keySet())) {
      s.types.put(key, s.types.get(key).deref());
    }
    s.dropCycles();
    // `this` is the solution of the scope defining the reference (see
    // IInstantiable.instantiate), so extending it with the scheme's own
    // instance gives the referenced binding exactly its lexical environment.
    // For top-level bindings that is the empty solution, which keeps the
    // result canonical across call sites — the InstEnv memo keys rely on it
    // for deduplication.
    return this.compose(s);
  }

  /**
   * Binds the fresh instance variables in {@code pattern} to the matching parts
   * of {@code target}, the call-site type. Only the fresh variables are ever
   * assigned: the call-site type belongs to the caller and may mention
   * variables of a polymorphic definition, so it is read, never written.
   * Unifying instead would assign those variables during reconstruction and
   * change the definition (and its memo keys) for every later instance.
   * Bounds are not re-checked here; inference already checked the call site.
   * Parts that do not line up carry no information for the instance and are
   * skipped.
   */
  private static void matchFresh(HMXType pattern, HMXType target, Set<TypeVar<HMXType>> fresh) {
    var p = pattern.deref();
    var t = target.deref();

    if (p instanceof HMXType.Var pv && fresh.contains(pv.tyVar.find())) {
      if (!(t instanceof HMXType.Var tv && tv.tyVar.find() == pv.tyVar.find())) {
        pv.tyVar.assignType(t);
      }
      return;
    }

    if (p instanceof HMXType.Arrow pa && t instanceof HMXType.Arrow ta) {
      matchFresh(pa.from, ta.from, fresh);
      matchFresh(pa.to, ta.to, fresh);
    } else if (p instanceof HMXType.LitType pl && t instanceof HMXType.LitType tl
        && pl.tyName.equals(tl.tyName) && pl.parameters.size() == tl.parameters.size()) {
      for (int i = 0; i < pl.parameters.size(); i++) {
        matchFresh(pl.parameters.get(i), tl.parameters.get(i), fresh);
      }
    } else if (p instanceof HMXType.Tuple pt && t instanceof HMXType.Tuple tt
        && pt.elements.size() == tt.elements.size()) {
      for (int i = 0; i < pt.elements.size(); i++) {
        matchFresh(pt.elements.get(i), tt.elements.get(i), fresh);
      }
    }
  }
}

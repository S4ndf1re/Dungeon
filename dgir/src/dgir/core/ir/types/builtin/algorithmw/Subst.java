package dgir.core.ir.types.builtin.algorithmw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.traits.IInstantiable.SolutionContext;

public final record Subst(HashMap<TypeVar<AlgorithmWType>, AlgorithmWType> types)
    implements SolutionContext<Expr, AlgorithmWType, TypeInference, Subst> {

  public static Subst newEmpty() {
    return new Subst(new HashMap<>());
  }

  public static Subst newSingleton(TypeVar<AlgorithmWType> key, AlgorithmWType type) {
    var map = new HashMap<TypeVar<AlgorithmWType>, AlgorithmWType>();
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
  public AlgorithmWType apply(AlgorithmWType type) {
    if (type instanceof AlgorithmWType.Var tyVar) {
      if (tyVar.tyVar.getAssigendType().isPresent()) {
        return apply(tyVar.tyVar.getAssigendType().get());
      }

      var t = types.get(tyVar.tyVar.find());
      if (t != null) {
        var resType = apply(t);
        return resType.deref();
      } else {
        return type.deref();
      }
    } else if (type instanceof AlgorithmWType.Arrow arrow) {
      return new AlgorithmWType.Arrow(apply(arrow.from), apply(arrow.to)).deref();
    } else if (type instanceof AlgorithmWType.LitType) {
      return type.deref();
    } else if (type instanceof AlgorithmWType.Tuple tuple) {
      return new AlgorithmWType.Tuple(tuple.elements.stream().map(this::apply).toList());
    } else {
      throw new TypingException.UnknownType(type);
    }
  }

  public void dropCycles() {
    ArrayList<TypeVar<AlgorithmWType>> toDrop = new ArrayList<>();
    for (var entry : this.types.keySet()) {
      if (new AlgorithmWType.Var(entry).equals(this.types.get(entry))) {
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
    var otherTypes = new HashMap<TypeVar<AlgorithmWType>, AlgorithmWType>(other.types);
    otherTypes
        .entrySet()
        .stream()
        .forEach(entry -> entry.setValue(this.apply(entry.getValue())));

    this.types
        .entrySet()
        .stream()
        .forEach(entry -> otherTypes.putIfAbsent(entry.getKey(), entry.getValue()));

    return new Subst(otherTypes);
  }

  @Override
  public Subst expand(TypeInference engine, Expr target, AlgorithmWType targetType) {
    // This one is a1 -> b1 -> c1, and is only ever used
    var ty1 = target.getInferredType().get().deref();
    var ftv = ty1.freeTypeVars();

    var scheme = new Scheme(ftv.stream().map(v -> v.find()).toList(), ty1);
    var s = scheme.toSubst(engine);

    var newType = s.apply(ty1);
    // FIXME: this will actually destroy the type used within the polymorhphic
    // function!
    engine.unify(newType, targetType);

    // The fresh scheme variables (a2, b2, ...) were unified against the
    // reference's solved type, so deref'ing their entries collapses the
    // indirection: {a1 ↦ Var(a2), ...} becomes {a1 ↦ int, b1 ↦ float, ...}.
    // The result is scheme-local (NOT composed with the caller's solution):
    // all inference constraints are already solved at this point, so the
    // caller chain contributes nothing, and keeping it out makes the
    // substitution canonical across call sites — which the InstEnv memo keys
    // rely on for deduplication.
    for (var key : Set.copyOf(s.types.keySet())) {
      s.types.put(key, s.types.get(key).deref());
    }
    s.dropCycles();
    return s;

  }
}

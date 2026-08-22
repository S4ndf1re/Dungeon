package dgir.core.ir.types.algorithmw;

import java.util.HashMap;
import java.util.stream.Collectors;

import dgir.core.ir.types.Expression;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;

public final record Subst(HashMap<TypeVar, AlgorithmWType> types)
      implements Expression.SolutionContext<AlgorithmWType> {

    public static Subst newEmpty() {
      return new Subst(new HashMap<>());
    }

    public static Subst newSingleton(TypeVar key, AlgorithmWType type) {
      var map = new HashMap<TypeVar, AlgorithmWType>();
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

    public AlgorithmWType apply(AlgorithmWType type) {
      if (type instanceof AlgorithmWType.Var var) {
        var t = types.get(var.tyVar);
        if (t != null) {
          // In the case that a substitution for a type is found, make sure to inform a
          // possibly present value that this substitution was performed and a type is
          // possibly solved.
          var resType = apply(t);
          var.tyVar.provideSolution(resType);
          return resType;
        } else {
          return type;
        }
      } else if (type instanceof AlgorithmWType.Arrow arrow) {
        return new AlgorithmWType.Arrow(apply(arrow.from), apply(arrow.to));
      } else if (type instanceof AlgorithmWType.LitType) {
        return type;
      } else if (type instanceof AlgorithmWType.Tuple tuple) {
        return new AlgorithmWType.Tuple(tuple.elements.stream().map(this::apply).toList());
      } else {
        throw new TypingException.UnknownType(type);
      }
    }

    /**
     * Compose this subst with another subst, by first relaying other through this
     *
     * @param other the other subst to compose with
     * @return the composed subst
     */
    public Subst compose(Subst other) {
      var otherTypes = new HashMap<TypeVar, AlgorithmWType>(other.types);
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
  }

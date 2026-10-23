package dgir.core.ir.types.builtin.hmx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import dgir.core.debug.Location;
import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;

public abstract sealed class HMXType extends Type<HMXType> {

  protected HMXType() {
    super();
  }

  protected HMXType(Location loc) {
    super(loc);
  }

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  public Scheme generalize(int level, Constraint constr) {
    var type = this.deref();

    var freeTypeVars = new HashSet<TypeVar<HMXType>>();
    for (var tv : type.freeTypeVars()) {
      freeTypeVars.add(tv.find());

      for (var upper : tv.getUpperBounds()) {
        for (var upperTv : upper.freeTypeVars()) {
          freeTypeVars.add(upperTv.find());
        }
      }

      for (var lower : tv.getLowerBounds()) {
        for (var lowerTv : lower.freeTypeVars()) {
          freeTypeVars.add(lowerTv.find());
        }
      }
    }

    for (var tv : constr.freeTypeVars()) {
      freeTypeVars.add(tv);

      for (var upper : tv.getUpperBounds()) {
        for (var upperTv : upper.freeTypeVars()) {
          freeTypeVars.add(upperTv.find());
        }
      }

      for (var lower : tv.getLowerBounds()) {
        for (var lowerTv : lower.freeTypeVars()) {
          freeTypeVars.add(lowerTv.find());
        }
      }
    }

    List<TypeVar<HMXType>> unboundFtv = freeTypeVars.stream().map(v -> v.find()).filter(v -> v.getLevel() >= level)
        .toList();

    var scheme = new Scheme(unboundFtv, type, constr);

    return scheme;
  }

  @Override
  public boolean occursCheck(TypeVar<HMXType> ty) {
    var ftv = this.freeTypeVars();
    return ftv.contains(ty);
  }

  public abstract boolean isFullySpecified();

  public abstract Set<TypeVar<HMXType>> freeTypeVars();

  public static final class Var extends HMXType {

    public final TypeVar<HMXType> tyVar;

    public Var(TypeVar<HMXType> tyVar) {
      this.tyVar = tyVar;
    }

    public Var(Location loc, TypeVar<HMXType> tyVar) {
      super(loc);
      this.tyVar = tyVar;
    }

    @Override
    public String toString() {
      var dereffed = this.deref();
      if (dereffed instanceof Var v) {
        return v.tyVar.toString();
      } else {
        return dereffed.toString();
      }
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Var vOther) {
        var dereffed = this.deref();
        var otherDereffed = vOther.deref();
        if (dereffed instanceof Var v1 && otherDereffed instanceof Var v2) {
          var res = v1.tyVar.find().equals(v2.tyVar.find());
          return res;
        }

        var res2 = dereffed.equals(otherDereffed);
        return res2;
      }
      return false;
    }

    @Override
    public int hashCode() {
      var dereffed = this.deref();
      if (dereffed instanceof Var v && v.tyVar.find() == this.tyVar.find()) {
        return this.tyVar.find().hashCode();
      }
      return dereffed.hashCode();
    }

    @Override
    public Set<TypeVar<HMXType>> freeTypeVars() {
      if (this.tyVar.getAssigendType().isPresent()) {
        return Set.copyOf(this.tyVar.getAssigendType().get().freeTypeVars());
      }

      return Set.of(this.tyVar.find());
    }

    public boolean determineTypeWhenBoundConstrainted(TypeInference engine, boolean allowUpperBoundGuess) {
      // When a type is already assigned no further subtyping constraints apply, other
      // than the already checked!
      // From then on, every subtype constraint would actually subtype check the
      // assigned type!
      if (this.tyVar.getAssigendType().isPresent()) {
        return false;
      }

      // A concrete type may be chosen from lower bounds (what the variable
      // actually is). Upper bounds are only used when the caller explicitly
      // allows guessing: an upper-bound choice is not evidence, it pre-empts
      // annotations and unifications that would otherwise decide the variable.
      var lowerBounds = this.tyVar.getLowerBounds();
      var upperBounds = this.tyVar.getUpperBounds();
      if (lowerBounds.isEmpty() && !(allowUpperBoundGuess && !upperBounds.isEmpty())) {
        return false;
      }

      // Find candiates that may be the supertype
      HMXType choice;
      if (!lowerBounds.isEmpty()) {
        choice = lowerBounds.getFirst().deref();
        for (int i = 1; i < lowerBounds.size(); i++) {
          var candidate = lowerBounds.get(i).deref();
          try {
            engine.subtype(choice, candidate);
            choice = candidate;
          } catch (Throwable e) {
          }
        }
      } else {
        // No lower-bound evidence: take the greatest upper bound as the
        // choice, checking the remaining uppers against it.
        choice = upperBounds.getFirst().deref();
        for (int i = 1; i < upperBounds.size(); i++) {
          var candidate = upperBounds.get(i).deref();
          try {
            engine.subtype(candidate, choice);
            choice = candidate;
          } catch (Throwable e) {
          }
        }
      }

      // Pass 1 (evidence-driven only) must not pin a variable to another
      // still-unresolved variable: that would freeze the reference and lock
      // both out of later resolution. Leave the pair for pass 2, which may
      // guess concrete types for the whole chain.
      if (!allowUpperBoundGuess && !choice.isFullySpecified()) {
        return false;
      }

      // After finding a possible common subtype, check against all constraints.
      // The upper bounds act as constraints here: they are enforced against the
      // chosen type instead of being guessed from. When this fails, no valid
      // solution will exist, and the type system fails.
      for (var lowerBound : lowerBounds) {
        engine.subtype(lowerBound, choice);
      }
      for (var upperBound : upperBounds) {
        engine.subtype(choice, upperBound);
      }

      this.tyVar.assignType(choice);
      return true;
    }

    @Override
    public boolean isFullySpecified() {
      var assignedType = this.tyVar.getAssigendType();
      if (assignedType.isPresent()) {
        return assignedType.get().isFullySpecified();
      }
      return false;
    }

    @Override
    public void occursCheckAjustLevel(TypeVar<HMXType> tyVar) {
      if (this.tyVar.getAssigendType().isPresent()) {
        this.tyVar.getAssigendType().get().occursCheckAjustLevel(tyVar);
      } else if (this.tyVar == tyVar) {
        throw new TypingException.OccursCheckFailed(this, tyVar);
      }

      var data = tyVar.find();
      var oData = this.tyVar.find();
      oData.setLevel(TypeVar.mergeLevel(data.getLevel(), oData.getLevel(), data, oData));
    }

    @Override
    public HMXType deref() {
      var assigendType = this.tyVar.getAssigendType();
      if (assigendType.isPresent()) {
        return assigendType.get().deref();
      }
      return new HMXType.Var(this.tyVar.find());
    }

    @Override
    public GeneralTypeParameter asTypeParameter() {
      var assigendType = this.tyVar.getAssigendType();
      if (assigendType.isPresent()) {
        return assigendType.get().asTypeParameter();
      }
      throw new UnsupportedOperationException("The variable " + this.tyVar
          + " is not bound to a type, i.e. is not fully inferred and hence can't be converted to a type parameter");
    }
  }

  public static final class Arrow extends HMXType {

    public final HMXType from;
    public final HMXType to;

    public Arrow(HMXType from, HMXType to) {
      this.from = from;
      this.to = to;
    }

    public Arrow(Location loc, HMXType from, HMXType to) {
      super(loc);
      this.from = from;
      this.to = to;
    }

    @Override
    public String toString() {
      return from.deref() + " -> " + to.deref();
    }

    @Override
    public boolean equals(Object obj) {
      return (obj instanceof Arrow other &&
          this.from.equals(other.from) &&
          this.to.equals(other.to));
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.from, this.to);
    }

    @Override
    public GeneralTypeParameter asTypeParameter() {
      assert this.isFullySpecified();

      ArrayList<HMXType> types = new ArrayList<>();

      HMXType current = this;
      while (current instanceof HMXType.Arrow) {
        var arrow = (HMXType.Arrow) current;
        types.add(arrow.from);
        current = arrow.to;
      }
      types.add(current);

      return GeneralTypeParameter.of(new GeneralParameterizedNominalType(TypeIdent.TYPE_IDENT_FUNC,
          types.stream().map(Type::asTypeParameter).toList()));
    }

    @Override
    public Set<TypeVar<HMXType>> freeTypeVars() {
      var set = new HashSet<TypeVar<HMXType>>();
      set.addAll(this.from.freeTypeVars());
      set.addAll(this.to.freeTypeVars());
      return Set.copyOf(set);
    }

    @Override
    public boolean isFullySpecified() {
      return this.from.isFullySpecified() && this.to.isFullySpecified();
    }

    @Override
    public void occursCheckAjustLevel(TypeVar<HMXType> tyVar) {
      this.from.occursCheckAjustLevel(tyVar);
      this.to.occursCheckAjustLevel(tyVar);
    }

    @Override
    public HMXType deref() {
      return new HMXType.Arrow(this.from.deref(), this.to.deref());
    }
  }

  public static final class LitType extends HMXType {

    public final TypeIdent tyName;
    public final List<HMXType> parameters;

    public LitType(TypeIdent tyName) {
      this.tyName = tyName;
      this.parameters = List.of();
    }

    public LitType(Location loc, TypeIdent tyName) {
      super(loc);
      this.tyName = tyName;
      this.parameters = List.of();
    }

    public LitType(TypeIdent tyName, List<HMXType> parameters) {
      this.tyName = tyName;
      this.parameters = List.copyOf(parameters);
    }

    public LitType(Location loc, TypeIdent tyName, List<HMXType> parameters) {
      super(loc);
      this.tyName = tyName;
      this.parameters = List.copyOf(parameters);
    }

    @Override
    public GeneralTypeParameter asTypeParameter() {
      assert this.isFullySpecified() : "the type must be fully specified to be convertable to a general type";

      return GeneralTypeParameter.of(new GeneralParameterizedNominalType(this.tyName,
          this.parameters.stream().map(HMXType::asTypeParameter).toList()));
    }

    @Override
    public String toString() {
      return (tyName +
          (parameters.isEmpty() ? ""
              : "<" +
                  parameters
                      .stream()
                      .map(HMXType::deref)
                      .map(Object::toString)
                      .collect(Collectors.joining(","))
                  +
                  ">"));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof LitType other && other.tyName.equals(this.tyName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.tyName, this.parameters);
    }

    @Override
    public Set<TypeVar<HMXType>> freeTypeVars() {
      var set = new HashSet<TypeVar<HMXType>>();
      this.parameters.forEach(p -> set.addAll(p.freeTypeVars()));
      return Set.copyOf(set);
    }

    @Override
    public boolean isFullySpecified() {
      return this.parameters.stream().allMatch(HMXType::isFullySpecified);
    }

    @Override
    public void occursCheckAjustLevel(TypeVar<HMXType> tyVar) {
      this.parameters.forEach(p -> p.occursCheckAjustLevel(tyVar));
    }

    @Override
    public HMXType deref() {
      return new HMXType.LitType(this.tyName, this.parameters.stream().map(p -> p.deref()).toList());
    }
  }

  public static final class NumericType extends HMXType {
    public long size;

    public NumericType(long size) {
      this.size = size;
    }

    public NumericType(Location loc, long size) {
      super(loc);
      this.size = size;
    }

    @Override
    public String toString() {
      return "" + this.size;
    }

    @Override
    public Set<TypeVar<HMXType>> freeTypeVars() {
      return Set.of();
    }

    @Override
    public GeneralTypeParameter asTypeParameter() {
      assert this.isFullySpecified() : "the type must be fully specified to be convertable to a general type";

      return GeneralTypeParameter.of(this.size);
    }

    @Override
    public boolean isFullySpecified() {
      return true;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof NumericType nt && this.size == nt.size;
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.size);
    }

    @Override
    public void occursCheckAjustLevel(TypeVar<HMXType> tyVar) {
    }

    @Override
    public HMXType deref() {
      return new HMXType.NumericType(this.size);
    }
  }

  public static final class Tuple extends HMXType {

    public final List<HMXType> elements;

    public Tuple(List<HMXType> elements) {
      this.elements = elements;
    }

    public Tuple(Location loc, List<HMXType> elements) {
      super(loc);
      this.elements = elements;
    }

    @Override
    public String toString() {
      return ("(" +
          this.elements
              .stream()
              .map(HMXType::deref)
              .map(Object::toString)
              .collect(Collectors.joining(", "))
          +
          ")");
    }

    @Override
    public boolean equals(Object obj) {
      return (obj instanceof Tuple other && this.elements.equals(other.elements));
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.elements);
    }

    @Override
    public Set<TypeVar<HMXType>> freeTypeVars() {
      var set = new HashSet<TypeVar<HMXType>>();

      this.elements.stream().forEach(e -> set.addAll(e.freeTypeVars()));

      return Set.copyOf(set);
    }

    @Override
    public boolean isFullySpecified() {
      return this.elements.stream().allMatch(HMXType::isFullySpecified);
    }

    @Override
    public void occursCheckAjustLevel(TypeVar<HMXType> tyVar) {
      this.elements.forEach(e -> e.occursCheckAjustLevel(tyVar));
    }

    @Override
    public GeneralTypeParameter asTypeParameter() {
      throw new UnsupportedOperationException("This method is not implemented for tuples");
    }

    @Override
    public HMXType deref() {
      return new HMXType.Tuple(this.elements.stream().map(e -> e.deref()).toList());
    }
  }
}

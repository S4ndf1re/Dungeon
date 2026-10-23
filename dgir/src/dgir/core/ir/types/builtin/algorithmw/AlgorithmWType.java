package dgir.core.ir.types.builtin.algorithmw;

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

public abstract sealed class AlgorithmWType extends Type<AlgorithmWType> {

  protected AlgorithmWType() {
    super();
  }

  protected AlgorithmWType(Location loc) {
    super(loc);
  }

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  public Scheme generalize(int level) {
    Set<TypeVar<AlgorithmWType>> ftv = this.freeTypeVars();

    List<TypeVar<AlgorithmWType>> unboundFtv = ftv.stream().filter(v -> v.find().getLevel() >= level).toList();

    return new Scheme(unboundFtv, this);
  }

  @Override
  public boolean occursCheck(TypeVar<AlgorithmWType> ty) {
    var ftv = this.freeTypeVars();
    return ftv.contains(ty);
  }

  public abstract boolean isFullySpecified();

  public abstract Set<TypeVar<AlgorithmWType>> freeTypeVars();

  public static final class Var extends AlgorithmWType {

    public final TypeVar<AlgorithmWType> tyVar;

    public Var(TypeVar<AlgorithmWType> tyVar) {
      this.tyVar = tyVar;
    }

    public Var(Location loc, TypeVar<AlgorithmWType> tyVar) {
      super(loc);
      this.tyVar = tyVar;
    }

    @Override
    public String toString() {
      return tyVar.toString();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Var vOther) {
        var dereffed = this.deref();
        var otherDereffed = vOther.deref();
        if (dereffed instanceof Var v1 && otherDereffed instanceof Var v2) {
          return v1.tyVar.find().equals(v2.tyVar.find());
        }

        return dereffed.equals(otherDereffed);
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
    public Set<TypeVar<AlgorithmWType>> freeTypeVars() {
      if (this.tyVar.getAssigendType().isPresent()) {
        return Set.copyOf(this.tyVar.getAssigendType().get().freeTypeVars());
      }

      return Set.of(this.tyVar.find());
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
    public void occursCheckAjustLevel(TypeVar<AlgorithmWType> tyVar) {
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
    public AlgorithmWType deref() {
      var assigendType = this.tyVar.getAssigendType();
      if (assigendType.isPresent()) {
        return assigendType.get().deref();
      }
      return new AlgorithmWType.Var(this.tyVar.find());
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

  public static final class Arrow extends AlgorithmWType {

    public final AlgorithmWType from;
    public final AlgorithmWType to;

    public Arrow(AlgorithmWType from, AlgorithmWType to) {
      this.from = from;
      this.to = to;
    }

    public Arrow(Location loc, AlgorithmWType from, AlgorithmWType to) {
      super(loc);
      this.from = from;
      this.to = to;
    }

    @Override
    public String toString() {
      return from + " -> " + to;
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

      ArrayList<AlgorithmWType> types = new ArrayList<>();

      AlgorithmWType current = this;
      while (current instanceof AlgorithmWType.Arrow) {
        var arrow = (AlgorithmWType.Arrow) current;
        types.add(arrow.from);
        current = arrow.to;
      }
      types.add(current);

      return GeneralTypeParameter.of(new GeneralParameterizedNominalType(TypeIdent.TYPE_IDENT_FUNC,
          types.stream().map(Type::asTypeParameter).toList()));
    }

    @Override
    public Set<TypeVar<AlgorithmWType>> freeTypeVars() {
      var set = new HashSet<TypeVar<AlgorithmWType>>();
      set.addAll(this.from.freeTypeVars());
      set.addAll(this.to.freeTypeVars());
      return Set.copyOf(set);
    }

    @Override
    public boolean isFullySpecified() {
      return this.from.isFullySpecified() && this.to.isFullySpecified();
    }

    @Override
    public void occursCheckAjustLevel(TypeVar<AlgorithmWType> tyVar) {
      this.from.occursCheckAjustLevel(tyVar);
      this.to.occursCheckAjustLevel(tyVar);
    }

    @Override
    public AlgorithmWType deref() {
      return new AlgorithmWType.Arrow(this.from.deref(), this.to.deref());
    }
  }

  public static final class LitType extends AlgorithmWType {

    public final TypeIdent tyName;
    public final List<AlgorithmWType> parameters;

    public LitType(TypeIdent tyName) {
      this.tyName = tyName;
      this.parameters = List.of();
    }

    public LitType(Location loc, TypeIdent tyName) {
      super(loc);
      this.tyName = tyName;
      this.parameters = List.of();
    }

    public LitType(TypeIdent tyName, List<AlgorithmWType> parameters) {
      this.tyName = tyName;
      this.parameters = List.copyOf(parameters);
    }

    public LitType(Location loc, TypeIdent tyName, List<AlgorithmWType> parameters) {
      super(loc);
      this.tyName = tyName;
      this.parameters = List.copyOf(parameters);
    }

    @Override
    public GeneralTypeParameter asTypeParameter() {
      assert this.isFullySpecified() : "the type must be fully specified to be convertable to a general type";

      return GeneralTypeParameter.of(new GeneralParameterizedNominalType(this.tyName,
          this.parameters.stream().map(AlgorithmWType::asTypeParameter).toList()));
    }

    @Override
    public String toString() {
      return (tyName +
          (parameters.isEmpty() ? ""
              : "<" +
                  parameters
                      .stream()
                      .map(Object::toString)
                      .collect(Collectors.joining(","))
                  +
                  ">"));
    }

    @Override
    public boolean equals(Object obj) {
      return (obj instanceof LitType && ((LitType) obj).tyName.equals(this.tyName));
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.tyName, this.parameters);
    }

    @Override
    public Set<TypeVar<AlgorithmWType>> freeTypeVars() {
      var set = new HashSet<TypeVar<AlgorithmWType>>();
      this.parameters.forEach(p -> set.addAll(p.freeTypeVars()));
      return Set.copyOf(set);
    }

    @Override
    public boolean isFullySpecified() {
      return this.parameters.stream().allMatch(AlgorithmWType::isFullySpecified);
    }

    @Override
    public void occursCheckAjustLevel(TypeVar<AlgorithmWType> tyVar) {
      this.parameters.forEach(p -> p.occursCheckAjustLevel(tyVar));
    }

    @Override
    public AlgorithmWType deref() {
      return new AlgorithmWType.LitType(this.tyName, this.parameters.stream().map(p -> p.deref()).toList());
    }
  }

  public static final class NumericType extends AlgorithmWType {
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
    public Set<TypeVar<AlgorithmWType>> freeTypeVars() {
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
    public void occursCheckAjustLevel(TypeVar<AlgorithmWType> tyVar) {
    }

    @Override
    public AlgorithmWType deref() {
      return new AlgorithmWType.NumericType(this.size);
    }
  }

  public static final class Tuple extends AlgorithmWType {

    public final List<AlgorithmWType> elements;

    public Tuple(List<AlgorithmWType> elements) {
      this.elements = elements;
    }

    public Tuple(Location loc, List<AlgorithmWType> elements) {
      super(loc);
      this.elements = elements;
    }

    @Override
    public String toString() {
      return ("(" +
          this.elements
              .stream()
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
    public Set<TypeVar<AlgorithmWType>> freeTypeVars() {
      var set = new HashSet<TypeVar<AlgorithmWType>>();

      this.elements.stream().forEach(e -> set.addAll(e.freeTypeVars()));

      return Set.copyOf(set);
    }

    @Override
    public boolean isFullySpecified() {
      return this.elements.stream().allMatch(AlgorithmWType::isFullySpecified);
    }

    @Override
    public void occursCheckAjustLevel(TypeVar<AlgorithmWType> tyVar) {
      this.elements.forEach(e -> e.occursCheckAjustLevel(tyVar));
    }

    @Override
    public GeneralTypeParameter asTypeParameter() {
      throw new UnsupportedOperationException("This method is not implemented for tuples");
    }

    @Override
    public AlgorithmWType deref() {
      return new AlgorithmWType.Tuple(this.elements.stream().map(e -> e.deref()).toList());
    }
  }
}

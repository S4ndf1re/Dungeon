package dgir.core.ir.types.builtin.algorithmw;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;
import dgir.core.ir.types.InferenceTree;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;

public abstract sealed class AlgorithmWType extends Type<AlgorithmWType> {

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  public Scheme generalize(int level) {
    Set<TypeVar<AlgorithmWType>> ftv = this.freeTypeVars();

    List<TypeVar<AlgorithmWType>> unboundFtv = ftv.stream().filter(v -> v.find().getLevel() >= level).toList();

    return new Scheme(unboundFtv, this);
  }

  /**
   * unify both this and other to a common substitution that can be used for
   * inference
   *
   * @param other The other type to unify with.
   * @return The unification result consisting of a substitution and an inference
   *         tree.
   * @throws RuntimeException if unimplemented
   */
  public abstract InferenceTree unify(
      TypeInference engine,
      AlgorithmWType other);

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

    @Override
    public String toString() {
      return tyVar.toString();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof AlgorithmWType other && this.deref().equals(other.deref());
    }

    @Override
    public int hashCode() {
      var dereffed = this.deref();
      if (dereffed instanceof Var v && v.tyVar.find() == this.tyVar.find()) {
        return this.tyVar.find().hashCode();
      }
      return Objects.hash(dereffed);
    }

    @Override
    public InferenceTree unify(TypeInference engine, AlgorithmWType other) {
      // Maybe the two types (this and other) are actually the same type variable
      if (other instanceof Var b) {
        var unifyRes = this.tyVar.unify(b.tyVar);
        if (unifyRes.isPresent()) {
          return engine.unify(unifyRes.get().getLeft(), unifyRes.get().getRight());
        } else {
          return new InferenceTree(
              "Unify-Var-Unify",
              this.toString() + " ~ " + b.toString());
        }
      } else if (other.occursCheck(this.tyVar)) {
        throw new TypingException.OccursCheckFailed(other, this.tyVar);
      } else {
        if (this.tyVar.getAssigendType().isPresent()) {
          engine.unify(this.tyVar.getAssigendType().get(), other);
        } else {
          other.occursCheckAjustLevel(this.tyVar);
          this.tyVar.assignType(other);
        }
        // In every other case, the type variable can be substituded with the concrete
        // type that is other
        return new InferenceTree(
            "Unify-Var",
            this.toString() + " ~ " + other.toString(),
            other.toString() + "/" + this.toString());
      }
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
      oData.setLevel(TypeVar.mergeLevel(data.getLevel(), oData.getLevel()));
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
    public InferenceTree unify(TypeInference engine, AlgorithmWType other) {
      if (other instanceof Arrow b) {
        InferenceTree u1 = engine.unify(this.from, b.from);
        InferenceTree u2 = engine.unify(
            this.to,
            b.to);

        return new InferenceTree(
            "Unify-Arrow",
            this.toString() + " ~ " + b.toString(),
            "",
            List.of(u1, u2));
      } else {
        throw new TypingException.UnificationFailed(this, other);
      }
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

    public LitType(TypeIdent tyName, List<AlgorithmWType> parameters) {
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
    public InferenceTree unify(TypeInference engine, AlgorithmWType other) {
      if (other instanceof LitType otherLit &&
          otherLit.tyName.equals(this.tyName)) {
        var trees = new ArrayList<InferenceTree>();

        if (this.parameters.size() != otherLit.parameters.size()) {
          throw new RuntimeException(
              "Parameter count mismatch: " +
                  this.parameters.size() +
                  " vs " +
                  otherLit.parameters.size());
        }

        for (int i = 0; i < this.parameters.size(); i++) {
          var result = engine.unify(
              this.parameters.get(i),
              otherLit.parameters.get(i));
          trees.add(result);
        }

        return new InferenceTree(
            "Unify-Base",
            this.toString() + " ~ " + other.toString());
      } else {
        throw new TypingException.UnificationFailed(this, other);
      }
    }

    @Override
    public Set<TypeVar<AlgorithmWType>> freeTypeVars() {
      return Set.of();
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

    @Override
    public String toString() {
      return "" + this.size;
    }

    @Override
    public InferenceTree unify(TypeInference engine, AlgorithmWType other) {

      if (other instanceof NumericType otherNum && this.size == otherNum.size) {
        return new InferenceTree(
            "Unify-NumericType",
            this.toString() + " ~ " + other.toString());
      } else {
        throw new TypingException.UnificationFailed(this, other);
      }
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
    public InferenceTree unify(TypeInference engine, AlgorithmWType other) {
      if (other instanceof Tuple b) {
        if (this.elements.size() != b.elements.size()) {
          throw new TypingException.TupleSizeMismatch(
              this.elements.size(),
              b.elements.size());
        }
        ArrayList<InferenceTree> trees = new ArrayList<>();

        for (int i = 0; i < this.elements.size(); i++) {
          InferenceTree result = engine.unify(
              this.elements.get(i),
              b.elements.get(i));
          trees.add(result);
        }

        return new InferenceTree(
            "Unify-Tuple",
            this + " ~ " + other,
            "",
            List.copyOf(trees));
      } else {
        throw new TypingException.UnificationFailed(this, other);
      }
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

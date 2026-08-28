package dgir.core.ir.types.algorithmw;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;
import dgir.core.ir.types.InferenceTree;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.compatibility.ExprOrOperator;

public abstract sealed class AlgorithmWType extends Type {

  @Override
  public GeneralTypeParameter asTypeParameter() {
    throw new RuntimeException("cannot convert, as the type is not fully specified!");
  }

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  public Scheme generalize(Env env, Optional<ExprOrOperator<Expr, AlgorithmWType>> originExpr) {
    Set<TypeVar> ftv = this.freeTypeVars();
    Set<TypeVar> envFtv = env.freeTypeVars();

    List<TypeVar> unboundFtv = ftv
        .stream()
        .filter(ty -> !envFtv.contains(ty))
        .collect(Collectors.toList());

    return new Scheme(unboundFtv, this, originExpr);
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
  public abstract UnifyResult unify(
      TypeInference engine,
      AlgorithmWType other);

  public boolean occursCheck(TypeVar ty) {
    var ftv = this.freeTypeVars();
    return ftv.contains(ty);
  }

  public abstract boolean isFullySpecified();

  public abstract Set<TypeVar> freeTypeVars();

  public static final class Var extends AlgorithmWType {

    public final TypeVar tyVar;

    public Var(TypeVar tyVar) {
      this.tyVar = tyVar;
    }

    @Override
    public String toString() {
      return tyVar.toString();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Var other && this.tyVar == other.tyVar;
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.tyVar);
    }

    @Override
    public UnifyResult unify(TypeInference engine, AlgorithmWType other) {
      // Maybe the two types (this and other) are actually the same type variable
      if (other instanceof Var b && this.tyVar == b.tyVar) {
        return new UnifyResult(
            Subst.newEmpty(),
            new InferenceTree(
                "Unify-Var-Same",
                this.toString() + " ~ " + b.toString()));
      } else if (other.occursCheck(this.tyVar)) {
        throw new TypingException.OccursCheckFailed(other, this.tyVar);
      } else {
        // In every other case, the type variable can be substituded with the concrete
        // type that is other
        var subst = Subst.newSingleton(this.tyVar, other);
        return new UnifyResult(
            subst,
            new InferenceTree(
                "Unify-Var",
                this.toString() + " ~ " + other.toString(),
                other.toString() + "/" + this.toString()));
      }
    }

    @Override
    public Set<TypeVar> freeTypeVars() {
      return Set.of(this.tyVar);
    }

    @Override
    public boolean isFullySpecified() {
      return false;
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
    public UnifyResult unify(TypeInference engine, AlgorithmWType other) {
      if (other instanceof Arrow b) {
        UnifyResult u1 = engine.unify(this.from, b.from);
        UnifyResult u2 = engine.unify(
            u1.applySubst(this.to),
            u1.applySubst(b.to));

        Subst finalSubst = u2.subst().compose(u1.subst());

        return new UnifyResult(
            finalSubst,
            new InferenceTree(
                "Unify-Arrow",
                this.toString() + " ~ " + b.toString(),
                finalSubst.toString(),
                List.of(u1.tree(), u2.tree())));
      } else {
        throw new TypingException.UnificationFailed(this, other);
      }
    }

    @Override
    public Set<TypeVar> freeTypeVars() {
      var set = new HashSet<TypeVar>();
      set.addAll(this.from.freeTypeVars());
      set.addAll(this.to.freeTypeVars());
      return Set.copyOf(set);
    }

    @Override
    public boolean isFullySpecified() {
      return this.from.isFullySpecified() && this.to.isFullySpecified();
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
    public UnifyResult unify(TypeInference engine, AlgorithmWType other) {
      if (other instanceof LitType otherLit &&
          otherLit.tyName.equals(this.tyName)) {
        var subst = Subst.newEmpty();
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
          subst = result.subst().compose(subst);
          trees.add(result.tree());
        }

        return new UnifyResult(
            Subst.newEmpty(),
            new InferenceTree(
                "Unify-Base",
                this.toString() + " ~ " + other.toString()));
      } else {
        throw new TypingException.UnificationFailed(this, other);
      }
    }

    @Override
    public Set<TypeVar> freeTypeVars() {
      return Set.of();
    }

    @Override
    public boolean isFullySpecified() {
      return this.parameters.stream().allMatch(AlgorithmWType::isFullySpecified);
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
    public UnifyResult unify(TypeInference engine, AlgorithmWType other) {

      if (other instanceof NumericType otherNum && this.size == otherNum.size) {
        return new UnifyResult(
            Subst.newEmpty(),
            new InferenceTree(
                "Unify-NumericType",
                this.toString() + " ~ " + other.toString()));
      } else {
        throw new TypingException.UnificationFailed(this, other);
      }
    }

    @Override
    public Set<TypeVar> freeTypeVars() {
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
    public Set<TypeVar> freeTypeVars() {
      var set = new HashSet<TypeVar>();

      this.elements.stream().forEach(e -> set.addAll(e.freeTypeVars()));

      return Set.copyOf(set);
    }

    @Override
    public UnifyResult unify(TypeInference engine, AlgorithmWType other) {
      if (other instanceof Tuple b) {
        if (this.elements.size() != b.elements.size()) {
          throw new TypingException.TupleSizeMismatch(
              this.elements.size(),
              b.elements.size());
        }
        Subst subst = Subst.newEmpty();
        ArrayList<InferenceTree> trees = new ArrayList<>();

        for (int i = 0; i < this.elements.size(); i++) {
          UnifyResult result = engine.unify(
              subst.apply(this.elements.get(i)),
              subst.apply(b.elements.get(i)));
          subst = result.subst().compose(subst);
          trees.add(result.tree());
        }

        return new UnifyResult(
            subst,
            new InferenceTree(
                "Unify-Tuple",
                this + " ~ " + other,
                subst + "",
                List.copyOf(trees)));
      } else {
        throw new TypingException.UnificationFailed(this, other);
      }
    }

    @Override
    public boolean isFullySpecified() {
      return this.elements.stream().allMatch(AlgorithmWType::isFullySpecified);
    }
  }
}

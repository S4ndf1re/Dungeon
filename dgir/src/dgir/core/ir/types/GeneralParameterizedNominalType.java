package dgir.core.ir.types;

import java.util.List;
import java.util.Objects;

/**
 * Representation of a parameterized type of a nominal type system.
 * This class should be useable with all Type-Systems.
 * Additionally, A Literal class is defined to return its type as a
 * GeneralParameterizedNominalType, in order to work with every type system.
 */
public class GeneralParameterizedNominalType {

  public static sealed interface GeneralTypeParameter {

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

    public default boolean isConcrete() {
      return this instanceof Concrete;
    }

    public default boolean isUnknown() {
      return this instanceof Unknown;
    }

    public default boolean isNumeric() {
      return this instanceof Numeric;
    }

    public default GeneralParameterizedNominalType getConcrete() {
      if (this.isConcrete()) {
        return ((Concrete) this).ty;
      }
      throw new IllegalArgumentException("this is not of type concrete");
    }

    public default long getNumeric() {
      if (this.isNumeric()) {
        return ((Numeric) this).number;
      }
      throw new IllegalArgumentException("this is not of type numeric");
    }

    public static GeneralTypeParameter of(GeneralParameterizedNominalType ty) {
      return new Concrete(ty);
    }

    public static GeneralTypeParameter of() {
      return new Unknown();
    }

    public static GeneralTypeParameter of(long number) {
      return new Numeric(number);
    }

    public static final record Concrete(GeneralParameterizedNominalType ty) implements GeneralTypeParameter {
    }

    public static final record Unknown() implements GeneralTypeParameter {
    }

    public static final record Numeric(long number) implements GeneralTypeParameter {
    }
  }

  private final TypeIdent ident;
  private final List<GeneralTypeParameter> typedParameters;

  public GeneralParameterizedNominalType(TypeIdent ident) {
    this.ident = ident;
    this.typedParameters = List.of();
  }

  public GeneralParameterizedNominalType(TypeIdent ident, List<GeneralTypeParameter> typedParameters) {
    this.ident = ident;
    this.typedParameters = typedParameters;
  }

  public TypeIdent getIdent() {
    return ident;
  }

  public List<GeneralTypeParameter> getTypedParameters() {
    return typedParameters;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof GeneralParameterizedNominalType other && this.ident.equals(other.ident)
        && this.typedParameters.equals(other.typedParameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.ident, this.typedParameters);
  }
}

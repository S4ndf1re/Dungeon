package dgir.core.ir.types;

import java.util.List;

/**
 * Representation of a parameterized type of a nominal type system.
 * This class should be useable with all Type-Systems.
 * Additionally, A Literal class is defined to return its type as a
 * GeneralParameterizedNominalType, in order to work with every type system.
 */
public class GeneralParameterizedNominalType  {

  public static sealed interface GeneralTypeParameter {
    public static final record Concrete(GeneralParameterizedNominalType ty) implements GeneralTypeParameter {
    }

    public static final record Unknown() implements GeneralTypeParameter {
    }
  }

  private final String ident;
  private final List<GeneralTypeParameter> typedParameters;

  public GeneralParameterizedNominalType(String ident) {
    this.ident = ident;
    this.typedParameters = List.of();
  }

  public GeneralParameterizedNominalType(String ident, List<GeneralTypeParameter> typedParameters) {
    this.ident = ident;
    this.typedParameters = typedParameters;
  }

  public String getIdent() {
    return ident;
  }

  public List<GeneralTypeParameter> getTypedParameters() {
    return typedParameters;
  }

}

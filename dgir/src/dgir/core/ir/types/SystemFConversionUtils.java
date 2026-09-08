package dgir.core.ir.types;

import java.util.ArrayList;
import java.util.Optional;

import dgir.core.ir.types.systemf.SystemFType;
import dgir.core.ir.types.systemf.TypeInference;

/**
 * Shared helpers to convert between IR types and System F inference types.
 */
public final class SystemFConversionUtils {

  private SystemFConversionUtils() {
  }

  /** Converts a known IR type into its System F inference type representation. */
  public static SystemFType irTypeToSystemF(TypeInference engine, dgir.core.ir.Type irType) {
    return engine.generalNominalTypeToInferenceType(
        irType.asParameterizedNominalType(),
        Optional.empty()).getLeft();
  }

  /**
   * Converts a fully specified System F type (curried {@link SystemFType.Arrow}
   * or a nominal {@link SystemFType.Lit}) into its general nominal IR
   * representation.
   */
  public static GeneralParameterizedNominalType systemFTypeToGeneralNominal(SystemFType ty) {
    assert ty.isFullySpecified() : "the type must be fully specified to be convertable";

    if (ty instanceof SystemFType.Arrow) {
      var types = new ArrayList<SystemFType>();

      var current = ty;
      while (current instanceof SystemFType.Arrow arrow) {
        types.add(arrow.from);
        current = arrow.to;
      }
      types.add(current);

      return new GeneralParameterizedNominalType(TypeIdent.TYPE_IDENT_FUNC,
          types.stream().map(SystemFType::asTypeParameter).toList());
    } else if (ty instanceof SystemFType.Lit lit) {
      return new GeneralParameterizedNominalType(lit.ident,
          lit.parameters.stream().map(SystemFType::asTypeParameter).toList());
    }

    throw new IllegalArgumentException("Cannot convert type to general nominal type: " + ty);
  }

  /** Converts a fully specified System F type into its IR type. */
  public static dgir.core.ir.Type systemFTypeToIrType(SystemFType ty) {
    return dgir.core.ir.Type.fromGeneralParameterizedNominalType(systemFTypeToGeneralNominal(ty));
  }
}

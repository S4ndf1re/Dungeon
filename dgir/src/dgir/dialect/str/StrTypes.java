package dgir.dialect.str;

import dgir.core.ir.Dialect;
import dgir.core.ir.Type;
import dgir.core.ir.TypeDescriptor;
import dgir.core.ir.TypeDetails;
import dgir.core.ir.types.GeneralParameterizedNominalType;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/**
 * Sealed marker interface for all types contributed by the {@link StrDialect}.
 */
public sealed interface StrTypes {
  /**
   * Abstract base class for all type-descriptors contributed by the
   * {@link StrDialect}.
   */
  sealed interface StrTypeDescriptor extends TypeDescriptor {
    @Override
    default @NotNull Class<? extends Dialect> getDialect() {
      return StrDialect.class;
    }

    final class StringDescriptor implements StrTypeDescriptor {
      @Contract(pure = true)
      public static @NotNull @Unmodifiable List<TypeDescriptor> getDescriptors() {
        return List.of(new StringDescriptor());
      }

      @Override
      public @NotNull Class<? extends Type> getTypeClass() {
        return StringT.class;
      }

      @Override
      public @NotNull String getIdent() {
        return "string";
      }

      @Override
      public @NotNull Function<Object, Boolean> getValidator() {
        return value -> value instanceof String;
      }

      @Override
      public @NotNull Function<@NotNull Pair<@NotNull String, @NotNull TypeDetails>, @NotNull Type> getParameterizedIdentFactory() {
        return args -> StrTypes.StringT.INSTANCE();
      }

      @Override
      public void initDefaultTypeInstances() {
        StringT.INSTANCE = new StringT();
      }

      @Override
      public @NotNull Function<@NotNull Pair<@NotNull GeneralParameterizedNominalType, @NotNull TypeDetails>, @NotNull Type> getGeneralParameterizedNominalTypeFactory() {
        return typeArgs -> {
          assert typeArgs.getLeft().getIdent().asStringIdent().equals(this.getIdent())
              : "the factory is configured to the wrong type";

          return StrTypes.StringT.INSTANCE();
        };
      }
    }
  }

  /**
   * UTF-16 string type in the {@code str} dialect.
   *
   * <p>
   * Ident: {@code string}. Validated values must be Java {@link String}
   * instances.
   *
   * <p>
   * The single pre-built instance is available as {@link #INSTANCE}.
   */
  final class StringT extends Type implements StrTypes {

    // =========================================================================
    // Static Fields
    // =========================================================================

    /** Singleton instance of the string type. */
    static @Nullable StringT INSTANCE;

    public static @NotNull StringT INSTANCE() {
      return Objects.requireNonNull(
          INSTANCE,
          "StringT instance not initialized. Ensure that StrDialect.initDefaultTypeInstances() is called during DGIRContext initialization.");
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    /**
     * Creates a new {@code StringT} instance. Prefer {@link #INSTANCE} over this
     * constructor.
     */
    StringT() {
      super("string");
    }
  }
}

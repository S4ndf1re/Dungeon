package dgir.dialect.func;

import dgir.core.ir.*;
import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/**
 * Sealed marker interface for all types contributed by the {@link FuncDialect}.
 */
public sealed interface FuncTypes {
  /**
   * Abstract base class for all type-descriptors contributed by the
   * {@link FuncDialect}.
   */
  sealed interface FuncTypeDescriptor extends TypeDescriptor {
    @Override
    default @NotNull Class<? extends Dialect> getDialect() {
      return FuncDialect.class;
    }

    final class FunctionDescriptor implements FuncTypeDescriptor {
      @Contract(pure = true)
      public static @NotNull @Unmodifiable List<TypeDescriptor> getDescriptors() {
        return List.of(new FunctionDescriptor());
      }

      @Override
      public @NotNull Class<? extends Type> getTypeClass() {
        return FuncType.class;
      }

      @Override
      public @NotNull String getIdent() {
        return "func.func";
      }

      @Override
      public @NotNull Function<Object, Boolean> getValidator() {
        // Function types validate their internal signature shape, not storage values.
        return value -> true;
      }

      @Override
      public void initDefaultTypeInstances() {
      }

      /**
       * Pattern to match correct function types and extract their arguments via group
       * 1 and 2
       * {@code ^func\.func<"\((.*)\)\s*->\s*\((.*)\)">$}
       */
      private static final Pattern FUNC_TYPE_PATTERN = Pattern
          .compile("^func\\.func<\"\\((.*)\\)\\s*->\\s*\\((.*)\\)\">$");

      @Override
      public @NotNull Function<@NotNull Pair<@NotNull String, @NotNull TypeDetails>, @NotNull Type> getParameterizedIdentFactory() {
        return args -> {
          Matcher matcher = FUNC_TYPE_PATTERN.matcher(args.getLeft());
          if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "Invalid parameterized ident for func.func type: %s".formatted(args.getLeft()));
          }
          // Everything inside first ()
          String inputsPart = Type.unescapeCustomExpression(matcher.group(1));
          List<MaybeType> inputs = new ArrayList<>();
          Type.consumeParameterText(inputsPart, Type.AllTypes.of(inputs));

          // Everything inside second ()
          String outputPart = Type.unescapeCustomExpression(matcher.group(2));
          Type output = null;
          if (!outputPart.isBlank())
            output = Type.fromParameterizedIdent(outputPart);

          return FuncType.of(inputs, MaybeType.of(output));
        };
      }

      @Override
      public @NotNull Function<@NotNull Pair<@NotNull GeneralParameterizedNominalType, @NotNull TypeDetails>, @NotNull Type> getGeneralParameterizedNominalTypeFactory() {
        return typeArg -> {
          assert typeArg.getLeft().getIdent().asStringIdent().equals(this.getIdent())
              : "func.func was expected, but received" + typeArg.getLeft().getIdent();

          var gpnt = typeArg.getLeft();
          var params = gpnt.getTypedParameters();
          assert params.size() >= 1 : "a function must at least have its return type specified";
          assert !params.stream().anyMatch(param -> param.isUnknown() || param.isNumeric())
              : "cannot convert unknown or numeric types to concrete function type";

          ArrayList<MaybeType> inputs = new ArrayList<>();
          // Select all parameters that are not the return type
          for (var param : params.subList(0, params.size() - 1)) {
            if (param.isUnknown()) {
              throw new IllegalArgumentException(
                  "parameter is of type Unknown, which is not parseable. This was already checked without success");
            }
            inputs.add(MaybeType.of(Type.fromGeneralParameterizedNominalType(param.getConcrete())));
          }

          // This will always return a non-null value!
          var outputGpnt = params.getLast();
          assert outputGpnt.isConcrete() : "the output type of a function must always be known for parsing";
          Type output = null;
          if (!outputGpnt.getConcrete().getIdent().equals(TypeIdent.TYPE_IDENT_UNIT)) {
            output = Type.fromGeneralParameterizedNominalType(outputGpnt.getConcrete());
          }

          return FuncType.of(inputs, MaybeType.of(output));
        };
      }
    }
  }

  /**
   * Function signature type in the {@code func} dialect.
   *
   * <p>
   * A {@code FuncType} describes a function's parameter types and optional return
   * type:
   *
   * <pre>
   *   func.func&lt;"(int32, string) -&gt; (bool)"&gt;
   * </pre>
   *
   * <p>
   * The {@link #getParameterizedIdent()} method renders the full signature;
   * simple (void/no-arg)
   * function types can be compared by this string.
   */
  final class FuncType extends Type implements FuncTypes {

    // =========================================================================
    // Type Info
    // =========================================================================

    @Contract(pure = true)
    @Override
    public @NotNull String getParameterizedIdent() {
      String inputs = Type.buildParameterList(getInputs());
      String output = getOutputAsNullable() != null ? getOutputAsNullable().toString() : "";
      String expression = "(%s) -> (%s)".formatted(inputs, output);
      return Type.buildParameterizedIdent(getDetails(), List.of(expression));
    }

    // =========================================================================
    // Factory
    // =========================================================================

    /**
     * Returns the canonical empty function type.
     *
     * @return the empty function signature.
     */
    public static FuncType empty() {
      return TypeUniquer.uniqueInstance(new FuncType());
    }

    /**
     * Returns a function type with the given inputs and output.
     *
     * @param inputs the ordered list of parameter types.
     * @param output the return type, or {@code null} for void.
     * @return a canonicalized {@link FuncType} instance.
     */
    public static FuncType of(@NotNull List<MaybeType> inputs, @Nullable MaybeType output) {
      return TypeUniquer.uniqueInstance(new FuncType(inputs, output));
    }

    @Override
    public GeneralParameterizedNominalType asParameterizedNominalType() {
      var collectedInputs = this.getInputs().stream()
          .map(inputType -> {
            if (inputType.isUnknown()) {
              return GeneralTypeParameter.of();
            } else {
              return GeneralTypeParameter.of(inputType.getAsKnownOrThrow().asParameterizedNominalType());
            }
          })
          .toList();

      GeneralParameterizedNominalType.GeneralTypeParameter output = null;
      if (this.getOutput().isPresent()) {
        if (this.getOutput().get().isKnown()) {
          output = GeneralParameterizedNominalType.GeneralTypeParameter.of(
              this.getOutput().get().getAsKnownOrThrow().asParameterizedNominalType());
        } else {
          output = GeneralParameterizedNominalType.GeneralTypeParameter.of();
        }
      } else {
        output = GeneralParameterizedNominalType.GeneralTypeParameter.of(
            new GeneralParameterizedNominalType(TypeIdent.TYPE_IDENT_UNIT));
      }

      ArrayList<GeneralParameterizedNominalType.GeneralTypeParameter> typeParams = new ArrayList<>(collectedInputs);
      typeParams.add(output);
      return new GeneralParameterizedNominalType(TypeIdent.from(this.getIdent()), List.copyOf(typeParams));
    }

    // =========================================================================
    // Members
    // =========================================================================

    /** The ordered list of input types (never {@code null}, may be empty). */
    private final List<MaybeType> inputs;

    /** The return type, or {@code null} for void functions. */
    private final Optional<MaybeType> output;

    // =========================================================================
    // Constructors
    // =========================================================================

    /** Create a no-argument void function type. */
    private FuncType() {
      super("func.func");
      inputs = List.of();
      output = Optional.empty();
    }

    /**
     * Create a function type with the given input types and return type.
     *
     * @param inputs the ordered list of parameter types; must not be {@code null}.
     * @param output the return type, or {@code null} for a void function.
     */
    private FuncType(@NotNull List<MaybeType> inputs, @Nullable MaybeType output) {
      super("func.func");
      this.inputs = Collections.unmodifiableList(inputs);
      this.output = Optional.ofNullable(output);
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the ordered list of input (parameter) types.
     *
     * @return immutable list of input types.
     */
    @Contract(pure = true)
    public @NotNull List<MaybeType> getInputs() {
      return inputs;
    }

    /**
     * Returns the return type of this function, or {@code null} for void functions.
     *
     * @return the return type, or {@code null}.
     */
    @Contract(pure = true)
    public Optional<MaybeType> getOutput() {
      return output;
    }

    /**
     * Returns the return type of this function, or {@code null} for void functions.
     *
     * @return the return type, or {@code null}.
     */
    @Contract(pure = true)
    public MaybeType getOutputAsNullable() {
      return output.orElse(null);
    }
  }
}

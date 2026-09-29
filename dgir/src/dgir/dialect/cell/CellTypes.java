package dgir.dialect.cell;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.NotNull;

import dgir.core.ir.Dialect;
import dgir.core.ir.MaybeType;
import dgir.core.ir.Type;
import dgir.core.ir.TypeDescriptor;
import dgir.core.ir.TypeDetails;
import dgir.core.ir.TypeUniquer;
import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.TypeIdent;

public sealed interface CellTypes {

  final class CellType extends Type implements CellTypes {

    private final MaybeType wrapped;

    private CellType() {
      super("cell.cell");
      this.wrapped = MaybeType.of();
    }

    private CellType(MaybeType type) {
      super("cell.cell");
      this.wrapped = type;
    }

    @Override
    public @NotNull String getParameterizedIdent() {
      return Type.buildParameterizedIdent(getDetails(), List.of(this.wrapped));
    }

    public MaybeType getWrappedType() {
      return this.wrapped;
    }

    public static CellType of() {
      return TypeUniquer.uniqueInstance(new CellType());
    }

    public static CellType of(MaybeType type) {
      return TypeUniquer.uniqueInstance(new CellType(type));
    }

    @Override
    public GeneralParameterizedNominalType asParameterizedNominalType() {
      return new GeneralParameterizedNominalType(TypeIdent.from(this.getIdent()),
          this.wrapped.asParameterizedNominalTypeParameter());
    }

  }


  sealed interface CellTypeDescriptor extends TypeDescriptor {

    final class CellDescriptor implements CellTypeDescriptor {

      @Contract(pure = true)
      public static @NotNull @Unmodifiable List<TypeDescriptor> getDescriptors() {
        return List.of(new CellDescriptor());
      }

      @Override
      public @NotNull Class<? extends Dialect> getDialect() {
        return CellDialect.class;
      }

      @Override
      public @NotNull Class<? extends Type> getTypeClass() {
        return CellType.class;
      }

      @Override
      public @NotNull String getIdent() {
        return "cell.cell";
      }

      @Override
      public @NotNull Function<Object, Boolean> getValidator() {
        return value -> true;
      }

      @Override
      public void initDefaultTypeInstances() {
      }

      @Override
      public @NotNull Function<@NotNull Pair<@NotNull String, @NotNull TypeDetails>, @NotNull Type> getParameterizedIdentFactory() {
        return args -> {
          List<String> parameters = Type.extractParameterStrings(args.getLeft());
          if (parameters.size() != 1) {
            throw new IllegalArgumentException(
                "cell.cell expects exactly one type parameter: " + args.getLeft());
          }
          return CellType.of(MaybeType.of(Type.fromParameterizedIdent(parameters.get(0))));
        };
      }

      @Override
      public @NotNull Function<@NotNull Pair<@NotNull GeneralParameterizedNominalType, @NotNull TypeDetails>, @NotNull Type> getGeneralParameterizedNominalTypeFactory() {
        return typeArg -> {
          var gpnt = typeArg.getLeft();
          var params = gpnt.getTypedParameters();
          if (params.size() != 1) {
            throw new IllegalArgumentException(
                "cell.cell expects exactly one type parameter, got " + params.size());
          }
          var wrapped = params.get(0);
          if (!wrapped.isConcrete()) {
            throw new IllegalArgumentException(
                "cell.cell wrapped type must be concrete: " + gpnt);
          }
          return CellType.of(MaybeType.of(Type.fromGeneralParameterizedNominalType(wrapped.getConcrete())));
        };
      }
    }
  }


}

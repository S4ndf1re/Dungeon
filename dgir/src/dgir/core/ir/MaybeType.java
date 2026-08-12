package dgir.core.ir;

import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import dgir.core.ir.types.TypeDialect.TypeInferenceSolver;
import dgir.core.serialization.MaybeTypeDeserializer;
import dgir.core.serialization.MaybeTypeSerializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * The `MaybeType` is a wrapper around a {@link Type} similar
 * to {@link Optional<Type>}.
 * The key difference is, that a `MaybeType` can be null as well, indicating
 * that no {@link Type} is expected.
 *
 * <p>
 * If the `MaybeType` is not null, it indicates that a type is expected,
 * but may not be known ahead of time and
 * will be solved by a {@link TypeInferenceSolver}
 */
@JsonDeserialize(using = MaybeTypeDeserializer.class)
@JsonSerialize(using = MaybeTypeSerializer.class)
public class MaybeType {
  private Type type;

  protected MaybeType(Type type) {
    this.type = type;
  }

  protected MaybeType() {
    this.type = null;
  }

  public static MaybeType of(Type ty) {
    return new MaybeType(ty);
  }

  public static MaybeType of(MaybeType ty) {
    return ty;
  }

  public static MaybeType of() {
    return new MaybeType();
  }

  public void specifyToKnown(@NotNull Type ty) {
    if (ty == null) {
      throw new IllegalArgumentException("Type msut not be null for resetting the type");
    }

    this.type = ty;
  }

  public boolean isKnown() {
    return this.type != null;
  }

  public boolean isUnknown() {
    return this.type == null;
  }

  public Optional<Type> getAsOptional() {
    return Optional.of(this.type);
  }

  public Type getAsNullable() {
    return this.type;
  }

  public Type getAsKnownOrThrow() {
    if (this.isUnknown()) {
      throw new RuntimeException("type is expected to be known, but is unknown");
    }

    return this.type;
  }

  @Override
  public final boolean equals(Object obj) {
    if (obj instanceof MaybeType other) {
      // Test for object equality (asserted by the TypeUniquer for normal Types)
      return this.getAsNullable() == other.getAsNullable();
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public String toString() {
    return this.type != null ? this.type + "" : "";
  }
}

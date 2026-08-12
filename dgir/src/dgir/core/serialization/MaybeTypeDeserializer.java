package dgir.core.serialization;

import dgir.core.ir.MaybeType;
import dgir.core.ir.Type;
import dgir.core.ir.TypeDetails;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Deserializes a {@link Type} from its parameterized ident string
 * representation.
 *
 * <p>
 * Input must be a non-empty JSON string. Unknown or unregistered type idents
 * are surfaced as
 * input mismatches with descriptive error text.
 */
public class MaybeTypeDeserializer extends StdDeserializer<MaybeType> {
  /** Constructs the deserializer bound to {@link MaybeType} class. */
  public MaybeTypeDeserializer() {
    this(MaybeType.class);
  }

  /**
   * Constructs the deserializer with an explicit target class.
   *
   * @param vc target class for deserialization.
   */
  public MaybeTypeDeserializer(Class<?> vc) {
    super(vc);
  }

  /**
   * Deserialize and validate a type ident string before resolving it through
   * {@link TypeDetails}.
   */
  @Override
  public MaybeType deserialize(JsonParser jp, DeserializationContext ctxt) throws JacksonException {
    JsonNode node = jp.readValueAsTree();
    if (node == null || node.isNull()) {
      return MaybeType.of();
    }

    if (!node.isString()) {
      return ctxt.reportInputMismatch(MaybeType.class, "Type value must be a string.");
    }

    String parameterizedIdent = node.asString().trim();
    if (parameterizedIdent.isEmpty()) {
      return ctxt.reportInputMismatch(MaybeType.class, "Type string must not be empty.");
    }

    try {
      return MaybeType.of(Type.fromParameterizedIdent(parameterizedIdent));
    } catch (IllegalArgumentException ex) {
      return ctxt.reportInputMismatch(MaybeType.class, ex.getMessage());
    }
  }
}

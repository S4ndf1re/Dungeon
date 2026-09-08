package dgir.core.analysis;

import java.util.IdentityHashMap;
import java.util.List;

import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;
import dgir.core.ir.types.Type;

/**
 * Builds a Graphviz DOT representation of a {@link Type} tree.
 *
 * <p>
 * The universal representation is {@link GeneralParameterizedNominalType}: a
 * type ident plus a list of {@link GeneralTypeParameter}s, which may nest
 * further parameterized types. This builder renders that nesting as a DOT
 * tree. Unresolved (non-concrete) inference types fall back to a single leaf
 * node labeled with their string representation.
 *
 * <p>
 * The resulting DOT string can be printed, saved as {@code .dot}, or rendered
 * to an image with {@code Graphviz.fromString(...)}, as done in
 * {@code DgirTestUtils}.
 */
public class DotType {
  private DotType() {}

  /**
   * Build the DOT graph string for the given inference type. If the type is
   * fully specified, it is expanded via its
   * {@link GeneralParameterizedNominalType} representation; otherwise a single
   * leaf node with the type's string representation is emitted.
   *
   * @param type the type to render.
   * @return a DOT digraph string.
   */
  public static String toDot(Type type) {
    StringBuilder dot = new StringBuilder();
    dot.append("digraph type {\n");
    dot.append("\tnode [shape=box];\n");

    if (type != null) {
      try {
        GeneralTypeParameter param = type.asTypeParameter();
        appendParameter(dot, param, "t0", new IdentityHashMap<>());
      } catch (RuntimeException e) {
        // Not fully specified (e.g. an unresolved inference variable)
        dot.append("\tt0 [label=\"").append(escape(type.toString())).append("\"];\n");
      }
    }

    dot.append("}\n");
    return dot.toString();
  }

  /**
   * Build the DOT graph string for the given parameterized nominal type.
   *
   * @param type the type to render.
   * @return a DOT digraph string.
   */
  public static String toDot(GeneralParameterizedNominalType type) {
    StringBuilder dot = new StringBuilder();
    dot.append("digraph type {\n");
    dot.append("\tnode [shape=box];\n");

    if (type != null) {
      appendType(dot, type, "t0", new IdentityHashMap<>());
    }

    dot.append("}\n");
    return dot.toString();
  }

  private static String appendType(
      StringBuilder dot,
      GeneralParameterizedNominalType type,
      String id,
      IdentityHashMap<GeneralParameterizedNominalType, String> ids) {
    String existing = ids.get(type);
    if (existing != null) {
      return existing;
    }

    ids.put(type, id);
    dot.append("\t").append(id).append(" [label=\"").append(escape(type.getIdent().toString())).append("\"];\n");

    List<GeneralTypeParameter> params = type.getTypedParameters();
    for (int i = 0; i < params.size(); i++) {
      String childId = appendParameter(dot, params.get(i), id + "_" + i, ids);
      dot.append("\t").append(id).append(" -> ").append(childId).append(";\n");
    }

    return id;
  }

  private static String appendParameter(
      StringBuilder dot,
      GeneralTypeParameter param,
      String id,
      IdentityHashMap<GeneralParameterizedNominalType, String> ids) {
    if (param.isConcrete()) {
      return appendType(dot, param.getConcrete(), id, ids);
    }
    if (param.isNumeric()) {
      dot.append("\t").append(id).append(" [label=\"").append(param.getNumeric()).append("\"];\n");
      return id;
    }
    // Unknown / open type parameter
    dot.append("\t").append(id).append(" [label=\"?\"];\n");
    return id;
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}

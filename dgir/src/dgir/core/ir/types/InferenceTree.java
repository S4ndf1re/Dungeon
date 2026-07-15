package dgir.core.ir.types;

import java.util.List;
import java.util.stream.Collectors;

public final class InferenceTree {

  public final String name;
  public final String input;
  public final String output;
  public final List<InferenceTree> children;

  public InferenceTree(
    String name,
    String input,
    String output,
    List<InferenceTree> children
  ) {
    this.name = name;
    this.input = input;
    this.output = output;
    this.children = children;
  }

  public InferenceTree(String name, String input, String output) {
    this(name, input, output, List.of());
  }

  public InferenceTree(String name, String input) {
    this(name, input, "", List.of());
  }

  @Override
  public String toString() {
    return this.toStringWithIdent(0);
  }

  String toStringWithIdent(int ident) {
    String prefix = "  ".repeat(ident + 1);
    String childrenStr = children
      .stream()
      .map(child -> child.toStringWithIdent(ident + 1))
      .collect(Collectors.joining("\n"));

    return (
      prefix +
      name +
      ":" +
      input +
      " => " +
      output +
      (childrenStr.length() > 0 ? "\n" + childrenStr : "")
    );
  }
}

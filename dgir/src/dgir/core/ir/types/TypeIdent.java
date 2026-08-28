package dgir.core.ir.types;

import java.util.HashMap;

public class TypeIdent {

  private static HashMap<String, TypeIdent> uniquedTypes = new HashMap<>();
  public static TypeIdent TYPE_IDENT_UNIT = TypeIdent.from("unit");
  public static TypeIdent TYPE_IDENT_INT = TypeIdent.from("int32");
  public static TypeIdent TYPE_IDENT_BOOL = TypeIdent.from("bool");
  public static TypeIdent TYPE_IDENT_LIST = TypeIdent.from("list");
  public static TypeIdent TYPE_IDENT_FUNC = TypeIdent.from("func.func");

  private final String ident;

  public TypeIdent() {
    this.ident = "$type_" + Integer.toHexString(this.hashCode());
  }

  private TypeIdent(String ident) {
    this.ident = ident;
  }

  @Override
  public String toString() {
    return this.ident;
  }

  public String asStringIdent() {
    return this.ident;
  }

  public static TypeIdent from(String ident) {
    var normalizedIdent = ident.toLowerCase();
    var uniquedTypeIdent = TypeIdent.uniquedTypes.get(normalizedIdent);
    if (uniquedTypeIdent == null) {
      var newTypeIdent = new TypeIdent(normalizedIdent);
      TypeIdent.uniquedTypes.put(normalizedIdent, newTypeIdent);
      return newTypeIdent;
    }
    return uniquedTypeIdent;
  }

}

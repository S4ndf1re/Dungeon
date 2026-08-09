package dgir.core.ir.types;

import java.util.HashMap;

public class TypeIdent {

  private static HashMap<String, TypeIdent> uniquedTypes = new HashMap<>();
  public static TypeIdent TYPE_IDENT_UNIT = TypeIdent.from("Unit");
  public static TypeIdent TYPE_IDENT_INT = TypeIdent.from("Int");
  public static TypeIdent TYPE_IDENT_BOOL = TypeIdent.from("Bool");
  public static TypeIdent TYPE_IDENT_LIST = TypeIdent.from("List");

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

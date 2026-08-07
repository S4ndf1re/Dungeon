package dgir.core.ir.types;

public class TypeIdent {

  public static TypeIdent TYPE_IDENT_UNIT = new TypeIdent("Unit");
  public static TypeIdent TYPE_IDENT_INT = new TypeIdent("Int");
  public static TypeIdent TYPE_IDENT_BOOL = new TypeIdent("Bool");
  public static TypeIdent TYPE_IDENT_LIST = new TypeIdent("List");

  private final String ident;

  public TypeIdent() {
    this.ident = "$type_" + Integer.toHexString(this.hashCode());
  }

  public TypeIdent(String ident) {
    this.ident = ident;
  }

  @Override
  public String toString() {
    return this.ident;
  }

}

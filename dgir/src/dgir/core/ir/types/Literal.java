package dgir.core.ir.types;

import java.util.List;
import java.util.Objects;

import dgir.core.ir.Value;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;

/**
 * A generic Literal that can be used with every {@link TypeDialect}
 */
public abstract class Literal {
  public abstract GeneralParameterizedNominalType toParameterizedNominalType();

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  public static class Unit extends Literal {

    @Override
    public String toString() {
      return "unit";
    }

    @Override
    public GeneralParameterizedNominalType toParameterizedNominalType() {
      return new GeneralParameterizedNominalType(TypeIdent.TYPE_IDENT_UNIT);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Unit;
    }

    @Override
    public int hashCode() {
      return "unit".hashCode();
    }

  }

  public static class Int extends Literal {
    private final int value;

    public Int(int value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value + "";
    }

    @Override
    public GeneralParameterizedNominalType toParameterizedNominalType() {
      return new GeneralParameterizedNominalType(TypeIdent.TYPE_IDENT_INT);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Int val && this.value == val.value;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(this.value);
    }
  }

  public static class Bool extends Literal {
    private boolean value;

    public Bool(boolean value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value + "";
    }

    @Override
    public GeneralParameterizedNominalType toParameterizedNominalType() {
      return new GeneralParameterizedNominalType(TypeIdent.TYPE_IDENT_BOOL);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Bool b && this.value == b.value;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(this.value);
    }
  }

  public static class MyList extends Literal {

    public MyList() {
    }

    @Override
    public String toString() {
      return "MyList";
    }

    @Override
    public GeneralParameterizedNominalType toParameterizedNominalType() {
      return new GeneralParameterizedNominalType(TypeIdent.TYPE_IDENT_LIST,
          List.of(GeneralTypeParameter.of()));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MyList;
    }

    @Override
    public int hashCode() {
      return "MyList".hashCode();
    }

  }

  public static class Generic extends Literal {
    private Value value;
    private GeneralParameterizedNominalType type;

    public Generic(Value value) {
      this.value = value;
      assert value.getType().isKnown();
      this.type = value.getType().getAsKnownOrThrow().asParameterizedNominalType();
    }

    @Override
    public String toString() {
      return this.value + "";
    }

    @Override
    public GeneralParameterizedNominalType toParameterizedNominalType() {
      return this.type;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Generic gen && this.value.equals(gen.value);
    }

    @Override
    public int hashCode() {
      return this.value.hashCode();
    }
  }
}

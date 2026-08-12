package dgir.core.ir.types;

import java.util.List;

import dgir.core.ir.Value;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;

/**
 * A generic Literal that can be used with every {@link TypeDialect}
 */
public abstract class Literal {
  public abstract GeneralParameterizedNominalType toParameterizedNominalType();

  public static class Unit extends Literal {

    @Override
    public String toString() {
      return "unit";
    }

    @Override
    public GeneralParameterizedNominalType toParameterizedNominalType() {
      return new GeneralParameterizedNominalType(TypeIdent.TYPE_IDENT_UNIT);
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
  }
}

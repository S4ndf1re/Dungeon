package dgir.core.ir.types;

import java.util.List;

import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;

/**
 * A generic Literal that can be used with every {@link TypeDialect}
 */
public abstract class Literal {
  public abstract GeneralParameterizedNominalType toParameterizedNominalType();

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
      return new GeneralParameterizedNominalType("Int");
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
      return new GeneralParameterizedNominalType("Bool");
    }
  }

  public static class MyList extends Literal {

    public MyList() {
    }

    @Override
    public String toString() {
      return "List";
    }

    @Override
    public GeneralParameterizedNominalType toParameterizedNominalType() {
      return new GeneralParameterizedNominalType("List",
          List.of(new GeneralTypeParameter.Unknown()));
    }

  }

}

package dgir.core.ir.types;

import dgir.core.ir.Value;

public abstract sealed class TypingException extends RuntimeException {

  protected TypingException(String message) {
    super(message);
  }

  public static final class InvalidLiteral extends TypingException {

    public final Object literal;

    public InvalidLiteral(Object literal) {
      super("value is not of type Lit: " + literal);
      this.literal = literal;
    }
  }

  public static final class UnknownVariable extends TypingException {

    public final Symbol variableName;

    public UnknownVariable(Symbol variableName) {
      super("Unknown variable: " + variableName);
      this.variableName = variableName;
    }
  }

  public static final class UnsupportedExpression extends TypingException {

    public enum AlgorithmType {
      AlgorithmW,
      SystemF;

      @Override
      public String toString() {
        switch (this) {
          case AlgorithmW:
            return "Algorithm-W";
          case SystemF:
            return "System-F";
        }
        return "AlgorithmType";
      }
    }

    public final AlgorithmType dialect;
    public final Object expression;

    public UnsupportedExpression(AlgorithmType dialect, Object expression) {
      super(
          "Expression is not type compatible with " + dialect + ": " + expression);
      this.dialect = dialect;
      this.expression = expression;
    }
  }

  public static final class UnknownType extends TypingException {

    public final Object type;

    public UnknownType(Object type) {
      super("Unknown type: " + type);
      this.type = type;
    }
  }

  public static final class OccursCheckFailed extends TypingException {

    public final Type type;
    public final TypeVar symbol;

    public OccursCheckFailed(Type type, TypeVar symbol) {
      super("Occurs-Check failed: " + symbol + " occurs in " + type);
      this.symbol = symbol;
      this.type = type;
    }
  }

  public static final class UnificationFailed extends TypingException {

    public final Type left;
    public final Type right;

    public UnificationFailed(Type left, Type right) {
      super("Unification failed: " + left + " != " + right);
      this.left = left;
      this.right = right;
    }
  }

  public static final class TupleSizeMismatch extends TypingException {

    public final int leftSize;
    public final int rightSize;

    public TupleSizeMismatch(int leftSize, int rightSize) {
      super("Tuple size does not match: " + leftSize + " != " + rightSize);
      this.leftSize = leftSize;
      this.rightSize = rightSize;
    }
  }

  public static final class ApplicationTypeError extends TypingException {

    public ApplicationTypeError() {
      super("Application Type Error");
    }
  }

  public static final class ExpectedForAllType extends TypingException {

    public ExpectedForAllType() {
      super("Not implemented for non ForAll types");
    }
  }

  public static final class SubtypingFailed extends TypingException {

    public final Type left;
    public final Type right;

    public SubtypingFailed(Type left, Type right) {
      super("Subtyping error between " + left + " and " + right);
      this.left = left;
      this.right = right;
    }
  }

  public static final class UnboundVariable extends TypingException {

    public final Value varName;

    public UnboundVariable(Value varName) {
      super("Variable " + varName + " is unbound");
      this.varName = varName;
    }
  }

  public static final class InstantiationError extends TypingException {

    public final String detail;

    public InstantiationError(String detail) {
      super(detail);
      this.detail = detail;
    }
  }
}

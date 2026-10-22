package dgir.core.ir.types;

import dgir.core.debug.Location;

public abstract sealed class TypingException extends RuntimeException {

  protected TypingException(String message) {
    super(message);
  }

  protected static String located(Type<?> type) {
    return type + " @ " + type.getLocation();
  }

  protected static String located(Expression<?, ?> expr) {
    return expr + " @ " + expr.getLocation();
  }

  public static final class InvalidLiteral extends TypingException {

    public final Object literal;

    public InvalidLiteral(Object literal) {
      super("value is not of type Lit: " + literal);
      this.literal = literal;
    }
  }

  public static final class UnknownVariable extends TypingException {

    public final Symbol<?, ?> variableName;

    public UnknownVariable(Symbol<?, ?> variableName) {
      super("Unknown variable: " + variableName);
      this.variableName = variableName;
    }

    public UnknownVariable(Symbol<?, ?> variableName, Location location) {
      super("Unknown variable: " + variableName + " @ " + location);
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

    public final Type<?> type;

    public UnknownType(Type<?> type) {
      super("Unknown type: " + located(type));
      this.type = type;
    }
  }

  public static final class CyclicSymbolAssignment extends TypingException {

    public final Symbol<?, ?> symbol;
    public final Expression<?, ?> expr;

    public CyclicSymbolAssignment(Symbol<?, ?> symbol, Expression<?, ?> expr) {
      super("Cyclic Symbol assignment was found. Symbol " + symbol + " was found within " + located(expr));
      this.symbol = symbol;
      this.expr = expr;
    }
  }

  public static final class OccursCheckFailed extends TypingException {

    public final Type<?> type;
    public final TypeVar<?> symbol;

    public OccursCheckFailed(Type<?> type, TypeVar<?> symbol) {
      super("Occurs-Check failed: " + symbol + " occurs in " + located(type));
      this.symbol = symbol;
      this.type = type;
    }
  }

  public static final class UnificationFailed extends TypingException {

    public final Type<?> left;
    public final Type<?> right;

    public UnificationFailed(Type<?> left, Type<?> right) {
      super("Unification failed: " + located(left) + " != " + located(right));
      this.left = left;
      this.right = right;
    }
  }

  public static final class TupleSizeMismatch extends TypingException {

    public final Type<?> left;
    public final Type<?> right;
    public final int leftSize;
    public final int rightSize;

    public TupleSizeMismatch(Type<?> left, Type<?> right, int leftSize, int rightSize) {
      super("Tuple size does not match: " + located(left) + " (" + leftSize
          + ") != " + located(right) + " (" + rightSize + ")");
      this.left = left;
      this.right = right;
      this.leftSize = leftSize;
      this.rightSize = rightSize;
    }
  }

  public static final class ApplicationTypeError extends TypingException {

    public ApplicationTypeError(Location location) {
      super("Application Type Error @ " + location);
    }
  }

  public static final class ExpectedForAllType extends TypingException {

    public ExpectedForAllType(Location location) {
      super("Not implemented for non ForAll types @ " + location);
    }
  }

  public static final class SubtypingFailed extends TypingException {

    public final Type<?> left;
    public final Type<?> right;

    public SubtypingFailed(Type<?> left, Type<?> right) {
      super("Subtyping error between " + located(left) + " and " + located(right));
      this.left = left;
      this.right = right;
    }
  }

  public static final class UnboundVariable extends TypingException {

    public final Symbol<?, ?> varName;

    public UnboundVariable(Symbol<?, ?> varName) {
      super("Variable " + varName + " is unbound");
      this.varName = varName;
    }

    public UnboundVariable(Symbol<?, ?> varName, Location location) {
      super("Variable " + varName + " is unbound @ " + location);
      this.varName = varName;
    }
  }

  public static final class InstantiationError extends TypingException {

    public final String detail;

    public InstantiationError(String detail) {
      super(detail);
      this.detail = detail;
    }

    public InstantiationError(String detail, Type<?> type) {
      super(detail + " @ " + type.getLocation());
      this.detail = detail;
    }
  }

  public static final class ExpectedLiteral extends TypingException {
    public final Symbol<?, ?> sym;
    public final Type<?> type;

    public ExpectedLiteral(Symbol<?, ?> sym, Type<?> type) {
      super(sym + " has an invalid type: " + located(type) + ". Expected Literal");
      this.sym = sym;
      this.type = type;
    }
  }

  public static final class NotFullySpecified extends TypingException {
    public final Type<?> type;

    public NotFullySpecified(Type<?> type) {
      super("Type " + located(type)
          + " is not fully specified. I.e. there are unresolved type variables contained wihtin the type");
      this.type = type;
    }

  }
}

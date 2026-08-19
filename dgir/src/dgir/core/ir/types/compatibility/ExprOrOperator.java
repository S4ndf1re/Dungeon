package dgir.core.ir.types.compatibility;

import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Type;

public abstract class ExprOrOperator<E extends Expression<E, T>, T extends Type> {
  public static <E extends Expression<E, T>, T extends Type> ExprOrOperator<E, T> of(Operation op) {
    return new ExprOrOperator.OperatorVariant<>(op);
  }

  public static <E extends Expression<E, T>, T extends Type> ExprOrOperator<E, T> of(E expr) {
    return new ExprOrOperator.ExpressionVariant<>(expr);
  }

  public boolean isExpr() {
    return this instanceof ExpressionVariant;
  }

  public boolean isOperator() {
    return this instanceof OperatorVariant;
  }

  public E getExpr() {
    if (!this.isExpr()) {
      throw new RuntimeException("value is not of type Expr");
    }

    return ((ExpressionVariant<E, T>) this).expr;
  }

  public Operation getOp() {
    if (!this.isOperator()) {
      throw new RuntimeException("value is not of type Operator");
    }

    return ((OperatorVariant<E, T>) this).op;
  }

  public static final class ExpressionVariant<E extends Expression<E, T>, T extends Type> extends ExprOrOperator<E, T> {
    public E expr;

    public ExpressionVariant(E expr) {
      this.expr = expr;
    }
  }

  public static final class OperatorVariant<E extends Expression<E, T>, T extends Type> extends ExprOrOperator<E, T> {
    public Operation op;

    public OperatorVariant(Operation op) {
      this.op = op;
    }
  }

}

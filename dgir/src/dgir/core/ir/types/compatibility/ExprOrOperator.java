package dgir.core.ir.types.compatibility;

import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression;

public interface ExprOrOperator<E extends Expression> {

  public default boolean isExpr() {
    return this instanceof ExpressionVariant;
  }

  public default boolean isOperator() {
    return this instanceof OperatorVariant;
  }

  public default E getExpr() {
    if (!this.isExpr()) {
      throw new RuntimeException("value is not of type Expr");
    }

    return ((ExpressionVariant<E>) this).expr;
  }

  public default Operation getOp() {
    if (!this.isOperator()) {
      throw new RuntimeException("value is not of type Operator");
    }

    return ((OperatorVariant<E>) this).op;
  }

  public final record ExpressionVariant<E extends Expression>(E expr) implements ExprOrOperator<E> {
  }

  public final record OperatorVariant<E extends Expression>(Operation op) implements ExprOrOperator<E> {
  }

}

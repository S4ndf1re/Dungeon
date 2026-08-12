package dgir.core.ir.types.compatibility;

import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Type;

public interface ExprOrOperator<E extends Expression<? extends Type>> {
  public static <E extends Expression<? extends Type>> ExprOrOperator<E> of(Operation op) {
    return new ExprOrOperator.OperatorVariant<>(op);
  }

  public static <E extends Expression<? extends Type>> ExprOrOperator<E> of(E expr) {
    return new ExprOrOperator.ExpressionVariant<>(expr);
  }

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

  public final record ExpressionVariant<E extends Expression<? extends Type>>(E expr) implements ExprOrOperator<E> {
  }

  public final record OperatorVariant<E extends Expression<? extends Type>>(Operation op) implements ExprOrOperator<E> {
  }

}

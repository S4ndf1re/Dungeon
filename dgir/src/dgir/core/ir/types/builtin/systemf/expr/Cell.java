package dgir.core.ir.types.builtin.systemf.expr;

import java.util.List;
import java.util.Optional;

import dgir.core.ir.Operation;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.traits.IExpressionCell;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.TypeResult;

/**
 * ExprCell is an inherently mutable cell, that just references a changeable
 * value in place! Overall, it will just copy the behaviour of the inner expr!
 *
 * <p>
 * No instantiation, replace and other operations are permitted.
 *
 * <p>
 * NOTE: it can be expected, that the cell may only ever occur in recursive
 * functions to trace back the solutions to the original expression! Hence, this
 * expression represents a dead end and is therefore a recursion breaker during
 * instantiation!
 */
public final class Cell extends Expr implements IExpressionCell<Expr, SystemFType> {
  private Expr reference;
  private Expr cellValue;

  public Cell(Expr reference, Expr cellValue) {
    this.reference = reference;
    this.cellValue = cellValue;
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public Optional<SystemFType> getInferredType() {
    return cellValue.getInferredType();
  }

  @Override
  public void setInferredType(Optional<SystemFType> inferredType) {
    cellValue.setInferredType(inferredType);
  }

  @Override
  public void setParentScopeExpression(Optional<Expr> expr, Optional<Integer> position) {
    this.cellValue.setParentScopeExpression(expr, position);
  }

  @Override
  public Optional<Operation> getUnderlyingOperation() {
    return cellValue.getUnderlyingOperation();
  }

  @Override
  public List<Expr> getChildren() {
    return List.of(cellValue);
  }

  @Override
  public Expr replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
    return this;
  }

  @Override
  public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
    return false;
  }

  @Override
  public TypeResult infer(TypeInference engine, Context ctx) {
    throw new UnsupportedOperationException("The memory cell is expected to only exist after instantiation!");
  }

  @Override
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
      Context solution) {
    throw new UnsupportedOperationException("The memory cell is expected to only exist after instantiation!");
  }

  @Override
  public Expr unwrap() {
    return this.cellValue;
  }

  @Override
  public void replaceIfMatches(Expr reference, Expr replacement) {
    if (reference == this.reference) {
      this.cellValue = replacement;
    }
  }

  @Override
  public Expr copy() {
    return this;
  }

}

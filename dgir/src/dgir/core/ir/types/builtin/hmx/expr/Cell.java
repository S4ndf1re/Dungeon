package dgir.core.ir.types.builtin.hmx.expr;

import java.util.List;
import java.util.Optional;

import dgir.core.ir.Operation;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.traits.ICell;
import dgir.core.ir.types.traits.IExpressionCell;
import dgir.core.ir.types.builtin.hmx.Env;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Subst;
import dgir.core.ir.types.builtin.hmx.TypeInference;

/**
   * ExprCell is an inherently mutable cell, that just references a changable
   * value in place!
   * Overall, it will just copy the behaviour of the inner expr!
   *
   * No instantiation, replace and other operations are permitted.
   *
   * <p>
   * NOTE: it can be expected, that the cell may only ever occur in recursive
   * functions to trace back the solutions to the original expression!
   * Hence, this expression represents a dead end and is therefore a recursion
   * breaker during instantiation!
   */
  public final class Cell extends HMXExpr implements IExpressionCell<HMXExpr, HMXType>, ICell {
    private HMXExpr reference;
    private HMXExpr cellValue;

    // public Expr reference() {
    // return this.reference;
    // }
    //
    // public Expr cellValue() {
    // return this.cellValue;
    // }

    public Cell(HMXExpr reference, HMXExpr cellValue) {
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
    public Optional<HMXType> getInferredType() {
      return cellValue.getInferredType();
    }

    @Override
    public void setInferredType(Optional<HMXType> inferredType) {
      cellValue.setInferredType(inferredType);
    }

    @Override
    public Optional<Operation> getUnderlyingOperation() {
      return cellValue.getUnderlyingOperation();
    }

    @Override
    public void setParentScopeExpression(Optional<HMXExpr> expr, Optional<Integer> position) {
      this.cellValue.setParentScopeExpression(expr, position);
    }

    @Override
    public Optional<HMXExpr> getParentScopeExpr() {
      return this.cellValue.getParentScopeExpr();
    }

    @Override
    public Optional<Integer> getParentScopePosition() {
      return this.cellValue.getParentScopePosition();
    }

    @Override
    public List<HMXExpr> getChildren() {
      return List.of(cellValue);
    }

    @Override
    public void reinstantiateSymbols() {
      // Do nothing, as the referenced expression is already expected to be
      // re-instantiated!
    }

    @Override
    public void replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
      // Do nothing, as the referenced expression is already expected to be
      // re-instantiated!
    }

    @Override
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      return false;
    }

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      throw new UnsupportedOperationException("The memory cell is expected to only exist after instantiation!");
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      throw new UnsupportedOperationException("The memory cell is expected to only exist after instantiation!");
    }

    @Override
    public HMXExpr unwrap() {
      return this.cellValue;
    }

    @Override
    public void replaceIfMatches(HMXExpr reference, HMXExpr replacement) {
      if (reference == this.reference) {
        this.cellValue = replacement;
      }
    }

    @Override
    public HMXExpr copy() {
      return this;
    }

  }

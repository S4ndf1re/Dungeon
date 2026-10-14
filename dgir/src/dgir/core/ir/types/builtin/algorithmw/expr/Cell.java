package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.List;
import java.util.Optional;

import dgir.core.ir.Operation;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.traits.IExpressionCell;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Env;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.InferResult;
import dgir.core.ir.types.builtin.algorithmw.Subst;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;

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
  public final class Cell extends Expr implements IExpressionCell<Expr, AlgorithmWType> {
    private Expr reference;
    private Expr cellValue;

    // public Expr reference() {
    // return this.reference;
    // }
    //
    // public Expr cellValue() {
    // return this.cellValue;
    // }

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
    public Optional<AlgorithmWType> getInferredType() {
      return cellValue.getInferredType();
    }

    @Override
    public void setInferredType(Optional<AlgorithmWType> inferredType) {
      cellValue.setInferredType(inferredType);
    }

    @Override
    public Optional<Operation> getUnderlyingOperation() {
      return cellValue.getUnderlyingOperation();
    }

    @Override
    public void setParentScopeExpression(Optional<Expr> expr, Optional<Integer> position) {
      this.cellValue.setParentScopeExpression(expr, position);
    }

    @Override
    public Optional<Expr> getParentScopeExpr() {
      return this.cellValue.getParentScopeExpr();
    }

    @Override
    public Optional<Integer> getParentScopePosition() {
      return this.cellValue.getParentScopePosition();
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(cellValue);
    }

    @Override
    public void reinstantiateSymbols() {
      // Do nothing, as the referenced expression is already expected to be
      // re-instantiated!
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      return this;
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return false;
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      throw new UnsupportedOperationException("The memory cell is expected to only exist after instantiation!");
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
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

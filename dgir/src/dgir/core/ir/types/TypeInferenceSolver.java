package dgir.core.ir.types;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.analysis.OperationVerifier;
import dgir.core.analysis.OperationVerifier.VerifyOptions;
import dgir.core.debug.Location;
import dgir.core.ir.Value;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitGetChildrenOption;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.compatibility.ConvertedOperationBuffer;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.traits.IAbstraction;
import dgir.core.ir.types.traits.IExpressionCell;
import dgir.core.traits.ISymbol;

public abstract class TypeInferenceSolver<TypeInferenceT extends TypeInferenceSolver<TypeInferenceT, E, T>, E extends Expression<E, T>, T extends Type<T>> {
  protected TypeDialectConverterRegistry registry;

  private ConvertedOperationBuffer<E, T, TypeInferenceT> operationToExprBuffer;
  private HashMap<GeneralBlock, E> convertedBlockCache;

  /**
   * Result of a full solve: the final type, the expression tree after
   * inference but before instantiation, and the instantiated tree.
   */
  public static record SolveResult<E extends Expression<E, T>, T extends Type<T>>(T type, E preInstantiation,
      E instantiated) {
  }

  /**
   * A simpler marker interface, marking all allowed contexts for conversion from
   * GPNT to inference types.
   */
  public static interface ConversionContext<E, T> {
  }

  public TypeInferenceSolver(TypeDialectConverterRegistry registry) {
    this.registry = registry;
    this.operationToExprBuffer = new ConvertedOperationBuffer<>();
    this.convertedBlockCache = new HashMap<>();
  }

  /** Returns the converter registry this solver was bound to. */
  public TypeDialectConverterRegistry getRegistry() {
    return registry;
  }

  // TODO: implement this for all missing builtin functionalities (i.e. all
  // converters that relie on custom expressions / custom constraints)
  public abstract E Var(Symbol<E, T> symbol);

  public abstract E Let(List<Pair<Symbol<E, T>, E>> bindings, E body);

  public abstract E Seq(List<E> exprs);

  public abstract E Lit(Literal lit);

  public abstract E App(E func, List<E> args);

  public abstract E Abs(List<Symbol<E, T>> params, E body);

  public abstract E Ann(E body, T type);

  public abstract E Block(E body);

  public E Var(Location loc, Symbol<E, T> symbol) {
    var expr = Var(symbol);
    expr.setLocation(loc);
    return expr;
  }

  public E Let(Location loc, List<Pair<Symbol<E, T>, E>> bindings, E body) {
    var expr = Let(bindings, body);
    expr.setLocation(loc);
    return expr;
  }

  public E Seq(Location loc, List<E> exprs) {
    var expr = Seq(exprs);
    expr.setLocation(loc);
    return expr;
  }

  public E Lit(Location loc, Literal lit) {
    var expr = Lit(lit);
    expr.setLocation(loc);
    return expr;
  }

  public E App(Location loc, E func, List<E> args) {
    var expr = App(func, args);
    expr.setLocation(loc);
    return expr;
  }

  public E Abs(Location loc, List<Symbol<E, T>> params, E body) {
    var expr = Abs(params, body);
    expr.setLocation(loc);
    return expr;
  }

  public E Ann(Location loc, E body, T type) {
    var expr = Ann(body, type);
    expr.setLocation(loc);
    return expr;
  }

  public E Block(Location loc, E body) {
    var expr = Block(body);
    expr.setLocation(loc);
    return expr;
  }

  public abstract T Arrow(T from, T to);

  public T ArrowChain(@SuppressWarnings("unchecked") T... types) {
    assert types.length >= 2 : "to construct an arrow chain, at least two types are required";

    T finalType = types[types.length - 1];
    for (int i = types.length - 2; i >= 0; i--) {
      finalType = Arrow(types[i], finalType);
    }

    return finalType;
  }

  /**
   * Solve the full expression tree by applying algorithm specific logic, like
   * inference and unification.
   * Different algorithms provide different mechanisms and different general
   * logic.
   *
   * <p>
   * NOTE: to be able to solve non-{@link Expression}
   * trees containing some or all {@link Operation},
   * conversion functions must be registered globalls using
   * {@link ConverterRegistry}
   * Those registered converter functions are tasked with converting
   * {@link Operation} to inference specific {@link Expression}
   *
   * @param expr the {@link ExprOrOperator} to solve.
   *
   * @return a {@link SolveResult} of the final type, the pre-instantiation
   *         expression tree, and the fully type annotated instantiated tree
   */
  public abstract SolveResult<E, T> solve(ExprOrOperator<E, T> expr);

  /**
   * Post-Solve stage to finish Expression Instantiation.
   *
   * <p>
   * This consists of four steps:
   * 1. Replace all old Symbols like Abstraction (function) parameter values
   * 2. Instantiate Operations from instantiated Expressions in bottom-up manner
   * 3. Clean up Temorary Blocks
   * 4. Validate resulting Operation tree
   */
  protected final E postSolve(E instantiated) {
    // 1. Replace values in Let and Abs expressions with new values
    // As Exprs are already hash-consed, this will visit every relevant expression
    // only once!
    // Additionally, function parameters are also unique Values, i.e. they cannot
    // get destroy hash-consing uniqueness!
    new Expression.ExpressionVisitor<E, T>(VisitOrder.IN_ORDER).visit(instantiated, e -> {
      e.reinstantiateSymbols();
    });
    // 2. Instantiate Operations bottom-up. As all values are newly assigned, this
    // operation will create a new operation tree
    // During this stage, make sure to fully type the values using the expressions
    // inferred types! The types are normally fully qualified, due to hash consing
    // and solution
    // applicaiton! In cases where the type is not fully qualified, throw a typing
    // error, as annotations may be needed to fully infer typing.
    // A few problems may arise in reconstructing the blocks and regions.
    // The new expression tree is actually a sea-of-nodes like Expression tree
    new Expression.ExpressionVisitor<E, T>(VisitOrder.POST_ORDER, VisitGetChildrenOption.ONLY_INSTANTIATED)
        .visit(instantiated, e -> {
          var instOp = e.getInstantiateOperationCallback();
          if (instOp.isPresent()) {
            @SuppressWarnings("unchecked")
            var exprUnwrapped = e instanceof IExpressionCell ? ((IExpressionCell<E, T>) e).unwrap() : e;
            var instantiatedOperation = instOp.get().instantiate(exprUnwrapped);
            e.setUnderlyingOperation(instantiatedOperation);
          }
        });

    // 3. Post-Process and move all temporary blocks to their operations parent
    // region!
    new Expression.ExpressionVisitor<E, T>(VisitOrder.POST_ORDER).visit(instantiated, e -> {
      var op = e.getUnderlyingOperation();
      if (op.isPresent() && !op.get().getTemporaryRegion().getBlocks().isEmpty()) {
        // Only try to move when the operation has its temporary region filled!
        // In case the parent region does not exist, it is invalid to move the child
        // blocks
        // to any position up the operation chain, hence, temporary region resolution is
        // invalid!
        var parentRegion = op.get().getParentRegionOrThrow();
        op.get().appendTemporaryBlocksToOtherRegion(parentRegion);
      }
    });

    if (instantiated.getUnderlyingOperation().isPresent()) {
      boolean valid = new OperationVerifier(VerifyOptions.FULL_VERIFICATION)
          .verify(instantiated.getUnderlyingOperation().get());
      if (!valid) {
        throw new RuntimeException(
            "Reconstructed operation failed verification: " + instantiated.getUnderlyingOperation().get());
      }
    }

    return instantiated;
  }

  /**
   * Convert a {@link GeneralBlock} to an algorithm specific {@link Expression}
   *
   * @param block the block to convert
   * @return the converted {@link Expression}
   */
  public E generalBlockToInferenceExpr(GeneralBlock block) {
    if (this.convertedBlockCache.containsKey(block)) {
      return this.convertedBlockCache.get(block);
    }

    ArrayList<Pair<Symbol<E, T>, E>> bindings = new ArrayList<>();
    Optional<Symbol<E, T>> lastValue = Optional.empty();

    for (var op : block.getOperations()) {
      var opOutput = op.getOutput();
      if (opOutput.isPresent()) {
        Symbol<E, T> sym = Symbol.of(opOutput.get().getValue());
        var expr = this.asExpression(ExprOrOperator.of(op));
        if (expr.containsSymbol(sym)) {
          throw new TypingException.CyclicSymbolAssignment(sym, expr);
        }
        bindings.add(Pair.of(sym, expr));
        lastValue = Optional.of(sym);
      } else {
        /*
         * NOTE: handle everything as a returnable value, even though something like a
         * function is not actually a expression! This is done to correctly typecheck
         * each function and their parameters!
         */
        Symbol<E, T> sym = null;
        if (op.asOp() instanceof ISymbol isym) {
          sym = Symbol.of(isym.getSymbol());
        } else {
          sym = Symbol.of(new Value());
        }
        var expr = this.asExpression(ExprOrOperator.of(op));
        if (expr.containsSymbol(sym)) {
          throw new TypingException.CyclicSymbolAssignment(sym, expr);
        }
        bindings.add(Pair.of(sym, expr));
        lastValue = Optional.of(sym);
      }
    }

    E innerLet = null;
    if (lastValue.isPresent()) {
      var sequence = new ArrayList<>(
          bindings.stream().filter(bnd -> !(bnd.getRight() instanceof IAbstraction) && !bnd.getLeft().isUsed())
              .map(bnd -> this.Var(bnd.getLeft())).toList());

      // In this case, the previous filter filtered the value, hence leading to
      // missing value within the sequence!
      if (bindings.getLast().getLeft().isUsed()) {
        sequence.add(this.Var(bindings.getLast().getLeft()));
      }

      innerLet = this.Let(bindings,
          this.Seq(sequence));
    } else {
      innerLet = this.Let(bindings, this.Lit(new Literal.Unit()));
    }

    var resultExpr = this.Block(innerLet);
    this.convertedBlockCache.put(block, resultExpr);
    return resultExpr;
  }

  /**
   * Convert a {@link GeneralParameterizedNominalType} to an algorithm specific
   * type.
   *
   * @param type    the GPNT type to convert
   * @param context the algorithm specific context
   * @return a pair of the algorithm specific type, and a potentially modified
   *         context
   */
  public abstract Pair<T, Optional<ConversionContext<E, T>>> generalNominalTypeToInferenceType(
      GeneralParameterizedNominalType type,
      Optional<ConversionContext<E, T>> context);

  @SuppressWarnings("unchecked")
  public E asExpression(ExprOrOperator<E, T> exprOrOp) {
    Class<E> exprClass = (Class<E>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1];
    if (exprOrOp.isExpr()) {
      return exprOrOp.getExpr();
    } else if (exprOrOp.isOperator()) {
      var op = exprOrOp.getOp();
      return this.operationToExprBuffer.operationToExpr((TypeInferenceT) this, op, this.registry, exprClass);
    } else {
      throw new RuntimeException("unimplemented for OPs");
    }
  }
}

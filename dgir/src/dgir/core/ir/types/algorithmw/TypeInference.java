package dgir.core.ir.types.algorithmw;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.analysis.OperationVerifier;
import dgir.core.analysis.OperationVerifier.VerifyOptions;
import dgir.core.ir.Value;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeDialect;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.Expression.ExpressionVisitor;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitGetChildrenOption;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.compatibility.ConvertedOperationBuffer;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.traits.ISymbol;

public final class TypeInference
    extends TypeDialect.TypeInferenceSolver<ExprOrOperator<Expr, AlgorithmWType>, Expr, AlgorithmWType> {

  private ConvertedOperationBuffer<ExprOrOperator<Expr, AlgorithmWType>, Expr, AlgorithmWType, TypeInference> operationToExprBuffer;

  public TypeInference() {
    this(new TypeDialectConverterRegistry());
  }

  public TypeInference(TypeDialectConverterRegistry registry) {
    super(registry);
    operationToExprBuffer = new ConvertedOperationBuffer<>();
  }

  @Override
  public Pair<AlgorithmWType, Optional<ConversionContext<Expr, AlgorithmWType>>> generalNominalTypeToInferenceType(
      GeneralParameterizedNominalType type,
      Optional<ConversionContext<Expr, AlgorithmWType>> data) {
    List<AlgorithmWType> paramTypes = type.getTypedParameters().stream().map(param -> switch (param) {
      case GeneralTypeParameter.Concrete con -> this.generalNominalTypeToInferenceType(con.ty(), data).getLeft();
      case GeneralTypeParameter.Unknown unk -> new AlgorithmWType.Var(new TypeVar());
      case GeneralTypeParameter.Numeric num -> new AlgorithmWType.NumericType(num.number());
    }).toList();

    return Pair.of(new AlgorithmWType.LitType(type.getIdent(), paramTypes), null);
  }

  @Override
  public Expr generalBlockToInferenceExpr(GeneralBlock block) {
    ArrayList<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings = new ArrayList<>();
    Optional<Symbol<Expr, AlgorithmWType>> lastValue = Optional.empty();

    for (var op : block.getOperations()) {
      var opOutput = op.getOutput();
      if (opOutput.isPresent()) {
        var sym = Symbol.<Expr, AlgorithmWType>of(opOutput.get().getValue());
        bindings.add(Pair.of(sym, this.asExpression(ExprOrOperator.of(op))));
        lastValue = Optional.of(sym);
      } else {
        /*
         * NOTE: handle everything as a returnable value, even though something like a
         * function is not actually a expression! This is done to correctly typecheck
         * each function and their parameters!
         */
        Symbol<Expr, AlgorithmWType> sym = null;
        if (op.asOp() instanceof ISymbol isym) {
          sym = Symbol.<Expr, AlgorithmWType>of(isym.getSymbol());
        } else {
          var val = new Value();
          sym = Symbol.<Expr, AlgorithmWType>of(val);
        }
        bindings.add(Pair.of(sym, this.asExpression(ExprOrOperator.of(op))));
        lastValue = Optional.of(sym);
      }
    }

    if (lastValue.isPresent()) {
      return new Expr.ExprLetRec(bindings, new Expr.ExprVar(lastValue.get()));
    } else {
      return new Expr.ExprLetRec(bindings, new Expr.ExprLit(new Literal.Unit()));
    }
  }

  @Override
  public Pair<Type, Expr> solve(ExprOrOperator<Expr, AlgorithmWType> exprOrOp) {
    Env env = new Env();
    Expr expr = this.asExpression(exprOrOp);
    InferResult res = this.infer(expr, env);
    var finalType = res.subst().apply(res.type());

    var instantiated = expr.instantiate(this, new InstEnv<>(expr), res.subst());

    // 1. Replace values in Let and Abs expressions with new values
    // As Exprs are already hash-consed, this will visit every relevant expression
    // only once!
    // Additionally, function parameters are also unique Values, i.e. they cannot
    // get destroy hash-consing uniqueness!
    new ExpressionVisitor<Expr, AlgorithmWType>(VisitOrder.IN_ORDER).visit(instantiated, e -> {
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
    new ExpressionVisitor<Expr, AlgorithmWType>(VisitOrder.POST_ORDER, VisitGetChildrenOption.ONLY_INSTANTIATED)
        .visit(instantiated, e -> {
          var instOp = e.getInstantiateOperationCallback();
          if (instOp.isPresent()) {
            var instantiatedOperation = instOp.get().instantiate(e);
            e.setUnderlyingOperation(instantiatedOperation);
          }
        });

    // 3. Post-Process and move all temporary blocks to their operations parent
    // region!
    new ExpressionVisitor<Expr, AlgorithmWType>(VisitOrder.POST_ORDER).visit(instantiated, e -> {
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

    // 4. Validate the expression tree, with all its types!
    var underlyingOp = instantiated.getUnderlyingOperation();
    if (underlyingOp.isPresent()) {
      assert new OperationVerifier(VerifyOptions.FULL_VERIFICATION).verify(underlyingOp.get());
    }

    return Pair.of((Type) finalType, instantiated);
  }

  /**
   * First infer, the `exprOrOp`. If `exprOrOp` is of type Operator, first convert
   * to an {@link Expr} using the conversion untilities and buffer the converted
   * result.
   *
   * <p>
   * After inferring either the {@link Expr} or the {@Link Operation}, store the
   * inferred result in combination with the already inferred {@link Expr}
   */
  public InferResult infer(Expr expr, Env env) {
    InferResult res = expr.infer(this, env);
    expr.setInferredType(res.type());
    return res;
  }

  public Expr asExpression(ExprOrOperator<Expr, AlgorithmWType> exprOrOp) {
    if (exprOrOp.isExpr()) {
      return exprOrOp.getExpr();
    } else if (exprOrOp.isOperator()) {
      var op = exprOrOp.getOp();
      return this.operationToExprBuffer.operationToExpr(this, op, this.registry, Expr.class);
    } else {
      throw new RuntimeException("unimplemented for OPs");
    }
  }

  public UnifyResult unify(AlgorithmWType left, AlgorithmWType right) {
    if (right instanceof AlgorithmWType.Var) {
      return right.unify(this, left);
    }
    return left.unify(this, right);
  }
}

package dgir.core.ir.types.builtin.algorithmw;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

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
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.compatibility.ConvertedOperationBuffer;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;
import dgir.core.ir.types.traits.IAbstraction;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.traits.ISymbol;

public final class TypeInference
    extends TypeDialect.TypeInferenceSolver<Expr, AlgorithmWType> {

  private ConvertedOperationBuffer<Expr, AlgorithmWType, TypeInference> operationToExprBuffer;

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
        Symbol<Expr, AlgorithmWType> sym = null;
        if (op.asOp() instanceof ISymbol isym) {
          sym = Symbol.<Expr, AlgorithmWType>of(isym.getSymbol());
        } else {
          var val = new Value();
          sym = Symbol.<Expr, AlgorithmWType>of(val);
        }
        var expr = this.asExpression(ExprOrOperator.of(op));
        if (expr.containsSymbol(sym)) {
          throw new TypingException.CyclicSymbolAssignment(sym, expr);
        }
        bindings.add(Pair.of(sym, expr));
        lastValue = Optional.of(sym);
      }
    }

    if (lastValue.isPresent()) {
      return new Expr.ExprLetRec(bindings,
          new Expr.ExprSeq(bindings.stream().filter(bnd -> !(bnd.getRight() instanceof IAbstraction))
              .map(bnd -> (Expr) new Expr.ExprVar(bnd.getLeft())).toList()));
    } else {
      return new Expr.ExprLetRec(bindings, new Expr.ExprLit(new Literal.Unit()));
    }
  }

  @Override
  public SolveResult<Expr> solve(ExprOrOperator<Expr, AlgorithmWType> exprOrOp) {
    Env env = new Env();
    Expr expr = this.asExpression(exprOrOp);
    InferResult res = this.infer(expr, env);
    var finalType = res.subst().apply(res.type());

    var instantiated = expr.instantiate(this, new InstEnv<>(expr), res.subst());

    instantiated = this.postSolve(instantiated);

    return new SolveResult<>((Type) finalType, expr, instantiated);
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
    expr.setInferredType(Optional.ofNullable(res.type()));
    return res;
  }

  @Override
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

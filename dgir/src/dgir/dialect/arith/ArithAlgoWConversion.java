package dgir.dialect.arith;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.algorithmw.AlgorithmWType;
import dgir.core.ir.types.algorithmw.Expr;
import dgir.core.ir.types.algorithmw.Expr.ExprCustom.GetChildrenFunction;
import dgir.core.ir.types.algorithmw.Expr.ExprCustom.InferFunction;
import dgir.core.ir.types.algorithmw.Expr.ExprCustom.InferFunctionResult;
import dgir.core.ir.types.algorithmw.Expr.ExprCustom.InstantiateFunction;
import dgir.core.ir.types.algorithmw.Expr.ExprCustom.ReplaceSymbolFunction;
import dgir.core.ir.types.algorithmw.InferResult;
import dgir.core.ir.types.algorithmw.Subst;
import dgir.core.ir.types.algorithmw.TypeInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.traits.IHasResult;
import dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;
import dgir.dialect.arith.ArithAttrs.UnaryModeAttr.UnaryMode;
import dgir.dialect.arith.ArithOps.BinaryOp;
import dgir.dialect.arith.ArithOps.ConstantOp;
import dgir.dialect.arith.ArithOps.UnaryOp;

public final class ArithAlgoWConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {

    ConverterRegistry.<ExprOrOperator<Expr, AlgorithmWType>, Expr, AlgorithmWType, TypeInference>addOperatorsToDialect(
        AlgorithmWInference.class,
        Pair.of(ConstantOp.class, ArithAlgoWConversion::convertConstOp),
        Pair.of(BinaryOp.class, ArithAlgoWConversion::convertBinOp),
        Pair.of(UnaryOp.class, ArithAlgoWConversion::convertUnaryOp));
    // TODO(jan): add cast operation once implemented
  }

  public static Expr convertConstOp(
      Operation op,
      TypeInference engine) {
    ArithOps.ConstantOp constOp = (ArithOps.ConstantOp) op.asOp();

    var expr = new Expr.ExprLit(new Literal.Generic(constOp.getResult()));
    expr.setInstantiateOperationCallback(instantiatedExpr -> {
      var inferredType = instantiatedExpr.getInferredType();
      assert inferredType.isPresent();

      var gpnt = inferredType.get().asTypeParameter();
      assert gpnt.isConcrete();

      Type t = Type.fromGeneralParameterizedNominalType(gpnt.getConcrete());
      assert t == constOp.getValueAttribute().getType();

      return new ArithOps.ConstantOp(constOp.getLocation(), constOp.getValueAttribute()).getOperation();
    });

    return expr;
  }

  public static Expr convertBinOp(
      Operation op,
      TypeInference engine) {

    record BinOpData(Expr lhs, Expr rhs, BinMode binMode) {
    }
    ;

    ArithOps.BinaryOp binOp = (ArithOps.BinaryOp) op.asOp();
    var binMode = binOp.getMode();

    var lhs = Symbol.<Expr, AlgorithmWType>of(binOp.getLhs());
    var rhs = Symbol.<Expr, AlgorithmWType>of(binOp.getRhs());
    var binOpData = new BinOpData(new Expr.ExprVar(lhs), new Expr.ExprVar(rhs), binMode);

    InferFunction<BinOpData> infFunc = (eng, env, data) -> {

      InferResult resLhs = eng.infer(data.lhs, env);
      var newEnv = env.apply(resLhs.subst());
      InferResult resRhs = eng.infer(data.rhs, newEnv);
      Subst finalSubst = resRhs.subst().compose(resLhs.subst());
      var lhsType = finalSubst.apply(resLhs.type());
      var rhsType = finalSubst.apply(resRhs.type());

      // TODO: maybe, it is possible to defer the type finding until both lhs and rhs
      // are completely inferred. This would require careful algorithm engineering and
      // is not possible, as of now.
      assert lhsType instanceof AlgorithmWType.LitType
          : "as a numeric type lattice must be applied, the types must be known and cannot be partially inferred";
      assert rhsType instanceof AlgorithmWType.LitType
          : "as a numeric type lattice must be applied, the types must be known and cannot be partially inferred";

      var lhsTypeParam = lhsType.asTypeParameter();
      var rhsTypeParam = rhsType.asTypeParameter();
      assert lhsTypeParam.isConcrete() : "lhs must be a concrete type parameter";
      assert rhsTypeParam.isConcrete() : "rhs must be a concrete type parameter";

      var lhsIrType = Type.fromGeneralParameterizedNominalType(lhsTypeParam.getConcrete());
      var rhsIrType = Type.fromGeneralParameterizedNominalType(rhsTypeParam.getConcrete());

      var resultIrType = data.binMode.getExpectedResultTypeForParams(lhsIrType, rhsIrType);

      // NOTE: this operation can be considered as a function application (calling).
      // Hence the result type is the expected BinOp type converted into an AlgoWType
      //
      // SAFETY: The cast to AlgorithmWType is safe, as this funciton should only get
      // called from algorithmW engine
      AlgorithmWType resultType = (AlgorithmWType) engine
          .generalNominalTypeToInferenceType(resultIrType.getAsKnownOrThrow().asParameterizedNominalType(),
              Optional.empty())
          .getLeft();

      return new InferFunctionResult(finalSubst, resultType);
    };

    GetChildrenFunction<BinOpData> getChildrenFn = (data) -> {
      return List.of(data.lhs, data.rhs);
    };

    InstantiateFunction<BinOpData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new Expr.ExprCustom<BinOpData>(toInstantiate, new BinOpData(data.lhs.instantiate(eng, env, solution),
          data.rhs.instantiate(eng, env, solution), data.binMode));
    };

    ReplaceSymbolFunction<BinOpData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new Expr.ExprCustom<BinOpData>(oldExpr, new BinOpData(data.lhs.replaceSymbol(original, replacement),
          data.rhs.replaceSymbol(original, replacement), data.binMode));
    };

    var result = new Expr.ExprCustom<BinOpData>(binOpData, infFunc, instFn, getChildrenFn, replaceSymbolFn);
    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (Expr.ExprCustom<BinOpData>) instantiatedExpr;

      var lhsOp = custExpr.getData().lhs.getUnderlyingOperation();
      var rhsOp = custExpr.getData().rhs.getUnderlyingOperation();

      assert lhsOp.isPresent();
      assert rhsOp.isPresent();
      assert lhsOp.get().asOp() instanceof IHasResult;
      assert rhsOp.get().asOp() instanceof IHasResult;

      return new BinaryOp(op.getLocation(), ((IHasResult) lhsOp.get().asOp()).getResult(),
          ((IHasResult) rhsOp.get().asOp()).getResult(), custExpr.getData().binMode).getOperation();
    });

    return result;
  }

  public static Expr convertUnaryOp(
      Operation op,
      TypeInference engine) {

    record UnaryData(Expr lhs, UnaryMode unaryMode) {
    }
    ;

    ArithOps.UnaryOp unaryOp = (ArithOps.UnaryOp) op.asOp();
    var lhs = Symbol.<Expr, AlgorithmWType>of(unaryOp.getOperand());
    var unaryMode = unaryOp.getMode();
    var unaryOpData = new UnaryData(new Expr.ExprVar(lhs), unaryMode);

    InferFunction<UnaryData> infFunc = (eng, env, data) -> {

      InferResult resLhs = eng.infer(data.lhs, env);
      Subst finalSubst = resLhs.subst();
      var lhsType = finalSubst.apply(resLhs.type());

      // TODO: maybe, it is possible to defer the type finding until both lhs and rhs
      // are completely inferred. This would require careful algorithm engineering and
      // is not possible, as of now.
      assert lhsType instanceof AlgorithmWType.LitType
          : "as a numeric type lattice must be applied, the types must be known and cannot be partially inferred";

      var lhsTypeParam = lhsType.asTypeParameter();
      assert lhsTypeParam.isConcrete() : "lhs must be a concrete type parameter";

      var lhsIrType = Type.fromGeneralParameterizedNominalType(lhsTypeParam.getConcrete());

      var resultIrType = data.unaryMode.getExpectedResultTypeForParams(lhsIrType);

      // NOTE: this operation can be considered as a function application (calling).
      // Hence the result type is the expected UnaryOp type converted into an
      // AlgoWType
      //
      // SAFETY: The cast to AlgorithmWType is safe, as this funciton should only get
      // called from algorithmW engine
      AlgorithmWType resultType = (AlgorithmWType) engine
          .generalNominalTypeToInferenceType(resultIrType.asParameterizedNominalType(), Optional.empty())
          .getLeft();

      return new InferFunctionResult(finalSubst, resultType);
    };

    GetChildrenFunction<UnaryData> getChildrenFn = (data) -> {
      return List.of(data.lhs);
    };

    InstantiateFunction<UnaryData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new Expr.ExprCustom<UnaryData>(toInstantiate, new UnaryData(data.lhs.instantiate(eng, env, solution),
          data.unaryMode));
    };

    ReplaceSymbolFunction<UnaryData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new Expr.ExprCustom<UnaryData>(oldExpr, new UnaryData(data.lhs.replaceSymbol(original, replacement),
          data.unaryMode));
    };

    var result = new Expr.ExprCustom<UnaryData>(unaryOpData, infFunc, instFn, getChildrenFn, replaceSymbolFn);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (Expr.ExprCustom<UnaryData>) instantiatedExpr;

      var lhsOp = custExpr.getData().lhs.getUnderlyingOperation();

      assert lhsOp.isPresent();
      assert lhsOp.get().asOp() instanceof IHasResult;

      return new UnaryOp(op.getLocation(), ((IHasResult) lhsOp.get().asOp()).getResult(),
          custExpr.getData().unaryMode).getOperation();
    });
    return result;
  }

  public static Expr convertCastOp(
      Operation op,
      TypeInference engine) {

    // TODO: this oepration is also only valid for specific combinations of values.
    // See the reference validation logic
    throw new UnsupportedOperationException("unimplemented, requires special handling");
  }
}

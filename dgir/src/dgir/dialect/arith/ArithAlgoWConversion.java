package dgir.dialect.arith;

import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeDialect.TypeInferenceSolver;
import dgir.core.ir.types.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.algorithmw.AlgorithmWType;
import dgir.core.ir.types.algorithmw.Expr;
import dgir.core.ir.types.algorithmw.Expr.ExprCustom.InferFunction;
import dgir.core.ir.types.algorithmw.Expr.ExprCustom.InferFunctionResult;
import dgir.core.ir.types.algorithmw.InferResult;
import dgir.core.ir.types.algorithmw.Subst;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.dialect.arith.ArithOps.BinaryOp;
import dgir.dialect.arith.ArithOps.ConstantOp;
import dgir.dialect.arith.ArithOps.UnaryOp;

public final class ArithAlgoWConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.addOperatorsToDialect(AlgorithmWInference.class,
        Pair.of(ConstantOp.class, ArithAlgoWConversion::convertConstOp),
        Pair.of(BinaryOp.class, ArithAlgoWConversion::convertBinOp),
        Pair.of(UnaryOp.class, ArithAlgoWConversion::convertUnaryOp));
  }

  public static Expr convertConstOp(
      Operation op,
      TypeInferenceSolver<ExprOrOperator<Expr, AlgorithmWType>, Expr, AlgorithmWType> engine) {
    ArithOps.ConstantOp constOp = (ArithOps.ConstantOp) op.asOp();

    return new Expr.ExprLit(new Literal.Generic(constOp.getResult()));
  }

  public static Expr convertBinOp(
      Operation op,
      TypeInferenceSolver<ExprOrOperator<Expr, AlgorithmWType>, Expr, AlgorithmWType> engine) {

    InferFunction<Object> infFunc = (eng, env, data) -> {
      ArithOps.BinaryOp binOp = (ArithOps.BinaryOp) op.asOp();

      var lhs = Symbol.<Expr, AlgorithmWType>of(binOp.getLhs());
      var rhs = Symbol.<Expr, AlgorithmWType>of(binOp.getRhs());
      var binMode = binOp.getMode();

      InferResult resLhs = eng.infer(new Expr.ExprVar(lhs), env);
      var newEnv = env.apply(resLhs.subst());
      InferResult resRhs = eng.infer(new Expr.ExprVar(rhs), newEnv);
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

      var lhsTypeParam = lhsType.toGeneralTypeParameter();
      var rhsTypeParam = rhsType.toGeneralTypeParameter();
      assert lhsTypeParam.isConcrete() : "lhs must be a concrete type parameter";
      assert rhsTypeParam.isConcrete() : "rhs must be a concrete type parameter";

      var lhsIrType = Type.fromGeneralParameterizedNominalType(lhsTypeParam.getConcrete());
      var rhsIrType = Type.fromGeneralParameterizedNominalType(rhsTypeParam.getConcrete());

      var resultIrType = binMode.getExpectedResultTypeForParams(lhsIrType, rhsIrType);

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

    var result = new Expr.ExprCustom<Object>(null, infFunc);
    result.setUnderlyingOperation(op);
    return result;
  }

  public static Expr convertUnaryOp(
      Operation op,
      TypeInferenceSolver<ExprOrOperator<Expr, AlgorithmWType>, Expr, AlgorithmWType> engine) {

    InferFunction<Object> infFunc = (eng, env, data) -> {
      ArithOps.UnaryOp unaryOp = (ArithOps.UnaryOp) op.asOp();

      var lhs = Symbol.<Expr, AlgorithmWType>of(unaryOp.getOperand());
      var unaryMode = unaryOp.getMode();

      InferResult resLhs = eng.infer(new Expr.ExprVar(lhs), env);
      Subst finalSubst = resLhs.subst();
      var lhsType = finalSubst.apply(resLhs.type());

      // TODO: maybe, it is possible to defer the type finding until both lhs and rhs
      // are completely inferred. This would require careful algorithm engineering and
      // is not possible, as of now.
      assert lhsType instanceof AlgorithmWType.LitType
          : "as a numeric type lattice must be applied, the types must be known and cannot be partially inferred";

      var lhsTypeParam = lhsType.toGeneralTypeParameter();
      assert lhsTypeParam.isConcrete() : "lhs must be a concrete type parameter";

      var lhsIrType = Type.fromGeneralParameterizedNominalType(lhsTypeParam.getConcrete());

      var resultIrType = unaryMode.getExpectedResultTypeForParams(lhsIrType);

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

    var result = new Expr.ExprCustom<Object>(null, infFunc);
    result.setUnderlyingOperation(op);
    return result;
  }

  public static Expr convertCastOp(
      Operation op,
      TypeInferenceSolver<ExprOrOperator<Expr, AlgorithmWType>, Expr, AlgorithmWType> engine) {

    InferFunction<Object> infFunc = (eng, env, data) -> {
      ArithOps.CastOp unaryOp = (ArithOps.CastOp) op.asOp();
      return new InferFunctionResult(Subst.newEmpty(), (AlgorithmWType) engine
          .generalNominalTypeToInferenceType(unaryOp.getTargetType().asParameterizedNominalType(), null).getLeft());
    };

    var result = new Expr.ExprCustom<Object>(null, infFunc);
    result.setUnderlyingOperation(op);
    return result;
  }
}

package dgir.dialect.arith;

import static dgir.dialect.builtin.BuiltinTypes.isNumeric;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypingException;
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
import dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;
import dgir.dialect.arith.ArithAttrs.UnaryModeAttr.UnaryMode;
import dgir.dialect.arith.ArithOps.BinaryOp;
import dgir.dialect.arith.ArithOps.CastOp;
import dgir.dialect.arith.ArithOps.ConstantOp;
import dgir.dialect.arith.ArithOps.UnaryOp;
import dgir.dialect.builtin.BuiltinTypes;

public final class ArithAlgoWConversion {

  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<Expr, AlgorithmWType, TypeInference>addOperatorsToDialect(
        AlgorithmWInference.class,
        Pair.of(ConstantOp.class, ArithAlgoWConversion::convertConstOp),
        Pair.of(BinaryOp.class, ArithAlgoWConversion::convertBinOp),
        Pair.of(UnaryOp.class, ArithAlgoWConversion::convertUnaryOp));
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

      var integerDesciptors = BuiltinTypes.BuiltinTypeDescriptor.IntegerDescriptor.getDescriptors();
      var floatDesciptors = new ArrayList<>(BuiltinTypes.BuiltinTypeDescriptor.FloatDescriptor.getDescriptors());
      floatDesciptors.addAll(integerDesciptors);

      Optional<TypingException> firstError = Optional.empty();
      for (var floatDesc : floatDesciptors) {
        try {
          var unifyRes = eng.unify(lhsType, new AlgorithmWType.LitType(TypeIdent.from(floatDesc.getIdent())));
          finalSubst = unifyRes.subst().compose(finalSubst);
          firstError = Optional.empty();
          break;
        } catch (TypingException e) {
          firstError = firstError.or(() -> Optional.of(e));
        }
      }
      if (firstError.isPresent()) {
        throw firstError.get();
      }

      lhsType = finalSubst.apply(lhsType);

      for (var floatDesc : floatDesciptors) {
        try {
          var unifyRes = eng.unify(rhsType, new AlgorithmWType.LitType(TypeIdent.from(floatDesc.getIdent())));
          finalSubst = unifyRes.subst().compose(finalSubst);
          firstError = Optional.empty();
          break;
        } catch (TypingException e) {
          firstError = firstError.or(() -> Optional.of(e));
        }
      }
      if (firstError.isPresent()) {
        throw firstError.get();
      }

      rhsType = finalSubst.apply(rhsType);

      // NOTE: deferring the constraint solution to concrete instantiation of
      // monomorphic instances of polymorphic values requires constraint based
      // solving.
      // Classic algorithm W does not offer said capabilites. HM(X) is needed here!
      var lhsIrType = lhsType.toIrType();
      var rhsIrType = rhsType.toIrType();

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

      var lhsValue = custExpr.getData().lhs.getOutputValue();
      var rhsValue = custExpr.getData().rhs.getOutputValue();

      return new BinaryOp(op.getLocation(), lhsValue,
          rhsValue, custExpr.getData().binMode).getOperation();
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

      var integerDesciptors = BuiltinTypes.BuiltinTypeDescriptor.IntegerDescriptor.getDescriptors();
      var floatDesciptors = new ArrayList<>(BuiltinTypes.BuiltinTypeDescriptor.FloatDescriptor.getDescriptors());
      floatDesciptors.addAll(integerDesciptors);

      Optional<TypingException> firstError = Optional.empty();
      for (var floatDesc : floatDesciptors) {
        try {
          var unifyRes = eng.unify(lhsType, new AlgorithmWType.LitType(TypeIdent.from(floatDesc.getIdent())));
          finalSubst = unifyRes.subst().compose(finalSubst);
          firstError = Optional.empty();
          break;
        } catch (TypingException e) {
          firstError = firstError.or(() -> Optional.of(e));
        }
      }
      if (firstError.isPresent()) {
        throw firstError.get();
      }

      lhsType = finalSubst.apply(lhsType);

      // TODO: maybe, it is possible to defer the type finding until both lhs and rhs
      // are completely inferred. This would require careful algorithm engineering and
      // is not possible, as of now.
      var lhsIrType = lhsType.toIrType();

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

      var lhsValue = custExpr.getData().lhs.getOutputValue();

      return new UnaryOp(op.getLocation(), lhsValue,
          custExpr.getData().unaryMode).getOperation();
    });
    return result;
  }

  public static Expr convertCastOp(
      Operation op,
      TypeInference engine) {

    record CastData(Expr value, Type targetType) {
    }
    ;

    ArithOps.CastOp castOp = (ArithOps.CastOp) op.asOp();
    var value = Symbol.<Expr, AlgorithmWType>of(castOp.getOperand());
    var targetType = castOp.getTargetType();
    var unaryOpData = new CastData(new Expr.ExprVar(value), targetType);

    InferFunction<CastData> infFunc = (eng, env, data) -> {

      InferResult resValue = eng.infer(data.value, env);
      Subst finalSubst = resValue.subst();
      var valueType = finalSubst.apply(resValue.type());

      var integerDesciptors = BuiltinTypes.BuiltinTypeDescriptor.IntegerDescriptor.getDescriptors();
      var floatDesciptors = new ArrayList<>(BuiltinTypes.BuiltinTypeDescriptor.FloatDescriptor.getDescriptors());
      floatDesciptors.addAll(integerDesciptors);

      Optional<TypingException> firstError = Optional.empty();
      for (var floatDesc : floatDesciptors) {
        try {
          var unifyRes = eng.unify(valueType, new AlgorithmWType.LitType(TypeIdent.from(floatDesc.getIdent())));
          finalSubst = unifyRes.subst().compose(finalSubst);
          firstError = Optional.empty();
          break;
        } catch (TypingException e) {
          firstError = firstError.or(() -> Optional.of(e));
        }
      }
      if (firstError.isPresent()) {
        throw firstError.get();
      }

      valueType = finalSubst.apply(valueType);

      // TODO: maybe, it is possible to defer the type finding until both lhs and rhs
      // are completely inferred. This would require careful algorithm engineering and
      // is not possible, as of now.
      var valueIrType = valueType.toIrType();
      var resultIrType = data.targetType;

      assert isNumeric(valueIrType);
      assert isNumeric(resultIrType);

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

    GetChildrenFunction<CastData> getChildrenFn = (data) -> {
      return List.of(data.value);
    };

    InstantiateFunction<CastData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new Expr.ExprCustom<CastData>(toInstantiate, new CastData(data.value.instantiate(eng, env, solution),
          data.targetType));
    };

    ReplaceSymbolFunction<CastData> replaceSymbolFn = (oldExpr, original, replacement, data) -> {
      return new Expr.ExprCustom<CastData>(oldExpr, new CastData(data.value.replaceSymbol(original, replacement),
          data.targetType));
    };

    var result = new Expr.ExprCustom<CastData>(unaryOpData, infFunc, instFn, getChildrenFn, replaceSymbolFn);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (Expr.ExprCustom<CastData>) instantiatedExpr;

      var lhsValue = custExpr.getData().value.getOutputValue();

      return new CastOp(op.getLocation(), lhsValue,
          custExpr.getData().targetType).getOperation();
    });
    return result;
  }
}

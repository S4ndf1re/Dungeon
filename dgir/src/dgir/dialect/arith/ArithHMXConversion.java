package dgir.dialect.arith;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXExpr.ExprCustom.GetChildrenFunction;
import dgir.core.ir.types.builtin.hmx.HMXExpr.ExprCustom.GenerateFunction;
import dgir.core.ir.types.builtin.hmx.HMXExpr.ExprCustom.InstantiateFunction;
import dgir.core.ir.types.builtin.hmx.HMXExpr.ExprCustom.ReplaceSymbolFunction;
import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.builtin.hmx.Constraint.DetermineByCallbackAndUnify;
import dgir.core.ir.types.builtin.hmx.Constraint.DetermineByCallbackAndUnify.DetermineCallback;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;
import dgir.dialect.arith.ArithAttrs.UnaryModeAttr.UnaryMode;
import dgir.dialect.arith.ArithOps.BinaryOp;
import dgir.dialect.arith.ArithOps.CastOp;
import dgir.dialect.arith.ArithOps.ConstantOp;
import dgir.dialect.arith.ArithOps.UnaryOp;

public final class ArithHMXConversion {

  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<HMXExpr, HMXType, TypeInference>addOperatorsToDialect(HMXInference.class,
        Pair.of(ConstantOp.class, ArithHMXConversion::convertConstOp),
        Pair.of(BinaryOp.class, ArithHMXConversion::convertBinOp),
        Pair.of(UnaryOp.class, ArithHMXConversion::convertUnaryOp));
  }

  public static HMXExpr convertConstOp(Operation op, TypeInference engine) {
    ArithOps.ConstantOp constOp = (ArithOps.ConstantOp) op.asOp();

    var expr = new HMXExpr.ExprLit(new Literal.Generic(constOp.getResult()));
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

  public static HMXExpr convertBinOp(Operation op, TypeInference engine) {

    record BinOpData(HMXExpr lhs, HMXExpr rhs, BinMode binMode) {
    }

    ArithOps.BinaryOp binOp = (ArithOps.BinaryOp) op.asOp();
    var binMode = binOp.getMode();

    var lhs = Symbol.<HMXExpr, HMXType>of(binOp.getLhs());
    var rhs = Symbol.<HMXExpr, HMXType>of(binOp.getRhs());
    var binOpData = new BinOpData(new HMXExpr.ExprVar(lhs), new HMXExpr.ExprVar(rhs), binMode);

    GenerateFunction<BinOpData> infFunc = (eng, env, type, data) -> {
      var lhsTypeVar = new TypeVar<HMXType>();
      var rhsTypeVar = new TypeVar<HMXType>();
      HMXType lhsType = new HMXType.Var(lhsTypeVar);
      HMXType rhsType = new HMXType.Var(rhsTypeVar);
      GenerateResult resLhs = eng.generate(data.lhs, env, lhsType);
      GenerateResult resRhs = eng.generate(data.rhs, env, rhsType);

      DetermineCallback callback = (types) -> {
        try {
          var lhsIrType = types[0].toIrType();
          var rhsIrType = types[1].toIrType();

          var resultIrType = data.binMode.getExpectedResultTypeForParams(lhsIrType, rhsIrType);

          HMXType resultType = (HMXType) engine.generalNominalTypeToInferenceType(
              resultIrType.getAsKnownOrThrow().asParameterizedNominalType(), Optional.empty()).getLeft();

          return resultType;
        } catch (TypingException.NotFullySpecified e) {
          throw new DetermineByCallbackAndUnify.CallbackNotReady();
        }
      };

      return new Constraint.Exists(List.of(lhsTypeVar, rhsTypeVar), new Constraint.And(resLhs.constr(), resRhs.constr(),
          new Constraint.Sub(lhsType, new HMXType.LitType(TypeIdent.from("number"))),
          new Constraint.Sub(rhsType, new HMXType.LitType(TypeIdent.from("number"))),
          new Constraint.DetermineByCallbackAndUnify(callback, type, lhsType, rhsType)));
    };

    GetChildrenFunction<BinOpData> getChildrenFn = (data) -> {
      return List.of(data.lhs, data.rhs);
    };

    InstantiateFunction<BinOpData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new HMXExpr.ExprCustom<BinOpData>(toInstantiate, new BinOpData(
          data.lhs.instantiate(eng, env, solution), data.rhs.instantiate(eng, env, solution), data.binMode));
    };

    ReplaceSymbolFunction<BinOpData> replaceSymbolFn = (oldHMXExpr, original, replacement, data) -> {
      return new HMXExpr.ExprCustom<BinOpData>(oldHMXExpr,
          new BinOpData(data.lhs.replaceSymbol(original, replacement),
              data.rhs.replaceSymbol(original, replacement), data.binMode));
    };

    var result = new HMXExpr.ExprCustom<BinOpData>(binOpData, infFunc, instFn, getChildrenFn, replaceSymbolFn);
    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (HMXExpr.ExprCustom<BinOpData>) instantiatedExpr;

      var lhsValue = custExpr.getData().lhs.getOutputValue();
      var rhsValue = custExpr.getData().rhs.getOutputValue();

      return new BinaryOp(op.getLocation(), lhsValue, rhsValue, custExpr.getData().binMode).getOperation();
    });

    return result;
  }

  public static HMXExpr convertUnaryOp(Operation op, TypeInference engine) {

    record UnaryData(HMXExpr lhs, UnaryMode unaryMode) {
    }
    ;

    ArithOps.UnaryOp unaryOp = (ArithOps.UnaryOp) op.asOp();
    var lhs = Symbol.<HMXExpr, HMXType>of(unaryOp.getOperand());
    var unaryMode = unaryOp.getMode();
    var unaryOpData = new UnaryData(new HMXExpr.ExprVar(lhs), unaryMode);

    GenerateFunction<UnaryData> infFunc = (eng, env, type, data) -> {
      var lhsTypeVar = new TypeVar<HMXType>();
      HMXType lhsType = new HMXType.Var(lhsTypeVar);
      GenerateResult resLhs = eng.generate(data.lhs, env, lhsType);

      DetermineCallback callback = (types) -> {
        try {
          var lhsIrType = types[0].toIrType();

          var resultIrType = data.unaryMode.getExpectedResultTypeForParams(lhsIrType);

          HMXType resultType = (HMXType) engine.generalNominalTypeToInferenceType(
              resultIrType.getAsKnownOrThrow().asParameterizedNominalType(), Optional.empty()).getLeft();

          return resultType;
        } catch (TypingException.NotFullySpecified e) {
          throw new DetermineByCallbackAndUnify.CallbackNotReady();
        }
      };

      return new Constraint.Exists(List.of(lhsTypeVar), new Constraint.And(resLhs.constr(),
          new Constraint.Sub(lhsType, new HMXType.LitType(TypeIdent.from("number"))),
          new Constraint.DetermineByCallbackAndUnify(callback, type, lhsType)));
    };

    GetChildrenFunction<UnaryData> getChildrenFn = (data) -> {
      return List.of(data.lhs);
    };

    InstantiateFunction<UnaryData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new HMXExpr.ExprCustom<UnaryData>(toInstantiate,
          new UnaryData(data.lhs.instantiate(eng, env, solution), data.unaryMode));
    };

    ReplaceSymbolFunction<UnaryData> replaceSymbolFn = (oldHMXExpr, original, replacement, data) -> {
      return new HMXExpr.ExprCustom<UnaryData>(oldHMXExpr,
          new UnaryData(data.lhs.replaceSymbol(original, replacement), data.unaryMode));
    };

    var result = new HMXExpr.ExprCustom<UnaryData>(unaryOpData, infFunc, instFn, getChildrenFn, replaceSymbolFn);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (HMXExpr.ExprCustom<UnaryData>) instantiatedExpr;

      var lhsValue = custExpr.getData().lhs.getOutputValue();

      return new UnaryOp(op.getLocation(), lhsValue, custExpr.getData().unaryMode).getOperation();
    });
    return result;
  }

  public static HMXExpr convertCastOp(Operation op, TypeInference engine) {

    record CastData(HMXExpr value, Type targetType) {
    }
    ;

    ArithOps.CastOp castOp = (ArithOps.CastOp) op.asOp();
    var value = Symbol.<HMXExpr, HMXType>of(castOp.getOperand());
    var targetType = castOp.getTargetType();
    var unaryOpData = new CastData(new HMXExpr.ExprVar(value), targetType);

    GenerateFunction<CastData> infFunc = (eng, env, type, data) -> {
      var lhsTypeVar = new TypeVar<HMXType>();
      HMXType lhsType = new HMXType.Var(lhsTypeVar);
      GenerateResult resLhs = eng.generate(data.value, env, lhsType);

      DetermineCallback callback = (types) -> {
        try {
          types[0].toIrType();

          HMXType resultType = (HMXType) engine.generalNominalTypeToInferenceType(
              data.targetType.getAsKnownOrThrow().asParameterizedNominalType(), Optional.empty()).getLeft();

          return resultType;
        } catch (TypingException.NotFullySpecified e) {
          throw new DetermineByCallbackAndUnify.CallbackNotReady();
        }
      };

      return new Constraint.Exists(List.of(lhsTypeVar), new Constraint.And(resLhs.constr(),
          new Constraint.Sub(lhsType, new HMXType.LitType(TypeIdent.from("number"))),
          new Constraint.DetermineByCallbackAndUnify(callback, type, lhsType)));
    };

    GetChildrenFunction<CastData> getChildrenFn = (data) -> {
      return List.of(data.value);
    };

    InstantiateFunction<CastData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new HMXExpr.ExprCustom<CastData>(toInstantiate,
          new CastData(data.value.instantiate(eng, env, solution), data.targetType));
    };

    ReplaceSymbolFunction<CastData> replaceSymbolFn = (oldHMXExpr, original, replacement, data) -> {
      return new HMXExpr.ExprCustom<CastData>(oldHMXExpr,
          new CastData(data.value.replaceSymbol(original, replacement), data.targetType));
    };

    var result = new HMXExpr.ExprCustom<CastData>(unaryOpData, infFunc, instFn, getChildrenFn, replaceSymbolFn);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprCustom;

      @SuppressWarnings("unchecked")
      var custExpr = (HMXExpr.ExprCustom<CastData>) instantiatedExpr;

      var lhsValue = custExpr.getData().value.getOutputValue();

      return new CastOp(op.getLocation(), lhsValue, custExpr.getData().targetType).getOperation();
    });
    return result;
  }
}

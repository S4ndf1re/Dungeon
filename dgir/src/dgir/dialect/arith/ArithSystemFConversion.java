package dgir.dialect.arith;

import static dgir.dialect.builtin.BuiltinTypes.isNumeric;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.SystemFConversionUtils;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.systemf.Expr;
import dgir.core.ir.types.systemf.SystemFInference;
import dgir.core.ir.types.systemf.SystemFType;
import dgir.core.ir.types.systemf.TypeInference;
import dgir.core.ir.types.systemf.TypeResult;
import dgir.core.ir.types.systemf.Expr.Custom.GetChildrenFunction;
import dgir.core.ir.types.systemf.Expr.Custom.InferFunction;
import dgir.core.ir.types.systemf.Expr.Custom.InstantiateFunction;
import dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;
import dgir.dialect.arith.ArithAttrs.UnaryModeAttr.UnaryMode;
import dgir.dialect.arith.ArithOps.BinaryOp;
import dgir.dialect.arith.ArithOps.CastOp;
import dgir.dialect.arith.ArithOps.UnaryOp;

public final class ArithSystemFConversion {

  public static void registerBuiltinSystemFConversion() {
    ConverterRegistry.<ExprOrOperator<Expr, SystemFType>, Expr, SystemFType, TypeInference>addOperatorsToDialect(
        SystemFInference.class,
        Pair.of(ArithOps.ConstantOp.class, ArithSystemFConversion::convertConstOp),
        Pair.of(BinaryOp.class, ArithSystemFConversion::convertBinOp),
        Pair.of(UnaryOp.class, ArithSystemFConversion::convertUnaryOp),
        Pair.of(CastOp.class, ArithSystemFConversion::convertCastOp));
  }

  public static Expr convertConstOp(
      Operation op,
      TypeInference engine) {
    ArithOps.ConstantOp constOp = (ArithOps.ConstantOp) op.asOp();

    var expr = new Expr.LitExpr(new Literal.Generic(constOp.getResult()));
    expr.setInstantiateOperationCallback(instantiatedExpr -> {
      var inferredType = instantiatedExpr.getInferredType();
      assert inferredType.isPresent();
      assert inferredType.get().isFullySpecified();

      Type t = SystemFConversionUtils.systemFTypeToIrType(inferredType.get());
      assert t.equals(constOp.getValueAttribute().getType());

      return new ArithOps.ConstantOp(constOp.getLocation(), constOp.getValueAttribute()).getOperation();
    });

    return expr;
  }

  /**
   * Derives the IR result type for a known operand type and converts it back
   * into a System F type. Mirrors the algorithm W conversion, where the numeric
   * type lattice is applied to the already known operand types.
   */
  private static SystemFType expectedResultType(TypeInference engine, Type resultIrType) {
    var resultType = engine.generalNominalTypeToInferenceType(
        resultIrType.getAsKnownOrThrow().asParameterizedNominalType(),
        Optional.empty()).getLeft();
    return resultType;
  }

  public static Expr convertBinOp(
      Operation op,
      TypeInference engine) {

    record BinOpData(Expr lhs, Expr rhs, BinMode binMode) {
    }

    ArithOps.BinaryOp binOp = (ArithOps.BinaryOp) op.asOp();
    var binMode = binOp.getMode();

    var lhs = Symbol.<Expr, SystemFType>of(binOp.getLhs());
    var rhs = Symbol.<Expr, SystemFType>of(binOp.getRhs());
    var binOpData = new BinOpData(new Expr.Var(lhs), new Expr.Var(rhs), binMode);

    InferFunction<BinOpData> infFunc = (eng, ctx, data) -> {
      var resLhs = eng.infer(ctx, data.lhs);
      var currentCtx = resLhs.ctx();
      var lhsType = currentCtx.apply(resLhs.type());

      var resRhs = eng.infer(currentCtx, data.rhs);
      currentCtx = resRhs.ctx();
      var rhsType = currentCtx.apply(resRhs.type());

      // NOTE: as a numeric type lattice must be applied, the types must be known
      // and cannot be partially inferred
      assert lhsType.isFullySpecified() : "lhs type must be fully specified";
      assert rhsType.isFullySpecified() : "rhs type must be fully specified";

      var lhsIrType = SystemFConversionUtils.systemFTypeToIrType(lhsType);
      var rhsIrType = SystemFConversionUtils.systemFTypeToIrType(rhsType);

      var resultIrType = data.binMode.getExpectedResultTypeForParams(lhsIrType, rhsIrType).getAsKnownOrThrow();

      var resultType = expectedResultType(eng, resultIrType);

      return new TypeResult(
          resultType,
          currentCtx,
          new dgir.core.ir.types.InferenceTree(
              "InfBinOp",
              ctx + " |- " + data,
              resultType.toString(),
              List.of(resLhs.tree(), resRhs.tree())));
    };

    GetChildrenFunction<BinOpData> getChildrenFn = (data) -> List.of(data.lhs, data.rhs);

    InstantiateFunction<BinOpData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new Expr.Custom<BinOpData>(toInstantiate,
          new BinOpData(
              data.lhs.instantiate(eng, env, solution),
              data.rhs.instantiate(eng, env, solution),
              data.binMode));
    };

    var result = new Expr.Custom<BinOpData>(binOpData, infFunc, null, instFn, getChildrenFn);
    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.Custom;

      @SuppressWarnings("unchecked")
      var custExpr = (Expr.Custom<BinOpData>) instantiatedExpr;

      var lhsOp = custExpr.getData().lhs.getUnderlyingOperation();
      var rhsOp = custExpr.getData().rhs.getUnderlyingOperation();

      assert lhsOp.isPresent();
      assert rhsOp.isPresent();
      assert lhsOp.get().getOutput().isPresent();
      assert rhsOp.get().getOutput().isPresent();

      return new BinaryOp(op.getLocation(), lhsOp.get().getOutputValueOrThrow(),
          rhsOp.get().getOutputValueOrThrow(), custExpr.getData().binMode).getOperation();
    });

    return result;
  }

  public static Expr convertUnaryOp(
      Operation op,
      TypeInference engine) {

    record UnaryData(Expr lhs, UnaryMode unaryMode) {
    }

    ArithOps.UnaryOp unaryOp = (ArithOps.UnaryOp) op.asOp();
    var lhs = Symbol.<Expr, SystemFType>of(unaryOp.getOperand());
    var unaryMode = unaryOp.getMode();
    var unaryOpData = new UnaryData(new Expr.Var(lhs), unaryMode);

    InferFunction<UnaryData> infFunc = (eng, ctx, data) -> {
      var resLhs = eng.infer(ctx, data.lhs);
      var currentCtx = resLhs.ctx();
      var lhsType = currentCtx.apply(resLhs.type());

      assert lhsType.isFullySpecified() : "operand type must be fully specified";

      var lhsIrType = SystemFConversionUtils.systemFTypeToIrType(lhsType);
      var resultIrType = data.unaryMode.getExpectedResultTypeForParams(lhsIrType);

      var resultType = expectedResultType(eng, resultIrType);

      return new TypeResult(
          resultType,
          currentCtx,
          new dgir.core.ir.types.InferenceTree(
              "InfUnaryOp",
              ctx + " |- " + data,
              resultType.toString(),
              List.of(resLhs.tree())));
    };

    GetChildrenFunction<UnaryData> getChildrenFn = (data) -> List.of(data.lhs);

    InstantiateFunction<UnaryData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new Expr.Custom<UnaryData>(toInstantiate,
          new UnaryData(data.lhs.instantiate(eng, env, solution), data.unaryMode));
    };

    var result = new Expr.Custom<UnaryData>(unaryOpData, infFunc, null, instFn, getChildrenFn);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.Custom;

      @SuppressWarnings("unchecked")
      var custExpr = (Expr.Custom<UnaryData>) instantiatedExpr;

      var lhsOp = custExpr.getData().lhs.getUnderlyingOperation();

      assert lhsOp.isPresent();
      assert lhsOp.get().getOutput().isPresent();

      return new UnaryOp(op.getLocation(), lhsOp.get().getOutputValueOrThrow(),
          custExpr.getData().unaryMode).getOperation();
    });

    return result;
  }

  public static Expr convertCastOp(
      Operation op,
      TypeInference engine) {

    record CastData(Expr value, Type targetType) {
    }

    ArithOps.CastOp castOp = (ArithOps.CastOp) op.asOp();
    var value = Symbol.<Expr, SystemFType>of(castOp.getOperand());
    var targetType = castOp.getTargetType();
    var castOpData = new CastData(new Expr.Var(value), targetType);

    InferFunction<CastData> infFunc = (eng, ctx, data) -> {
      var resValue = eng.infer(ctx, data.value);
      var currentCtx = resValue.ctx();
      var valueType = currentCtx.apply(resValue.type());

      assert valueType.isFullySpecified() : "value type must be fully specified";

      var valueIrType = SystemFConversionUtils.systemFTypeToIrType(valueType);
      var resultIrType = data.targetType;

      assert isNumeric(valueIrType);
      assert isNumeric(resultIrType);

      var resultType = expectedResultType(eng, resultIrType);

      return new TypeResult(
          resultType,
          currentCtx,
          new dgir.core.ir.types.InferenceTree(
              "InfCast",
              ctx + " |- " + data,
              resultType.toString(),
              List.of(resValue.tree())));
    };

    GetChildrenFunction<CastData> getChildrenFn = (data) -> List.of(data.value);

    InstantiateFunction<CastData> instFn = (toInstantiate, eng, env, solution, data) -> {
      return new Expr.Custom<CastData>(toInstantiate,
          new CastData(data.value.instantiate(eng, env, solution), data.targetType));
    };

    var result = new Expr.Custom<CastData>(castOpData, infFunc, null, instFn, getChildrenFn);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.Custom;

      @SuppressWarnings("unchecked")
      var custExpr = (Expr.Custom<CastData>) instantiatedExpr;

      var valueOp = custExpr.getData().value.getUnderlyingOperation();

      assert valueOp.isPresent();
      assert valueOp.get().getOutput().isPresent();

      return new CastOp(op.getLocation(), valueOp.get().getOutputValueOrThrow(),
          custExpr.getData().targetType).getOperation();
    });
    return result;
  }
}

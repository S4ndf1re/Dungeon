package dgir.dialect.func;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.Value;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.algorithmw.AlgorithmWType;
import dgir.core.ir.types.algorithmw.Expr;
import dgir.core.ir.types.algorithmw.TypeInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.traits.ISymbol.SymbolTableSymbol;
import dgir.dialect.func.FuncOps.CallIndirectOp;
import dgir.dialect.func.FuncOps.CallOp;
import dgir.dialect.func.FuncTypes.FuncType;

public final class FuncAlgoWConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<ExprOrOperator<Expr, AlgorithmWType>, Expr, AlgorithmWType, TypeInference>addOperatorsToDialect(
        AlgorithmWInference.class,
        Pair.of(FuncOps.FuncOp.class, FuncAlgoWConversion::convertFuncOp),
        Pair.of(FuncOps.ReturnOp.class, FuncAlgoWConversion::convertReturnOp),
        Pair.of(FuncOps.CallOp.class, FuncAlgoWConversion::convertCallOp),
        Pair.of(FuncOps.CallIndirectOp.class, FuncAlgoWConversion::convertCallIndirectOp),
        Pair.of(FuncOps.ConstantOp.class, FuncAlgoWConversion::convertConstantOp));
  }

  public static Expr convertFuncOp(
      Operation op,
      TypeInference engine) {
    FuncOps.FuncOp funcOp = (FuncOps.FuncOp) op.asOp();

    ArrayList<Symbol<Expr, AlgorithmWType>> params = new ArrayList<>();
    for (int i = 0; funcOp.getArgument(i).isPresent(); i++) {
      params.add(Symbol.<Expr, AlgorithmWType>of(funcOp.getArgument(i).get()));
    }

    var block = GeneralBlock.fromBlock(funcOp.getEntryBlock());
    var blockAsExpr = engine.generalBlockToInferenceExpr(block);

    if (!(blockAsExpr instanceof Expr)) {
      throw new IllegalArgumentException("Invalid, as the engine is not of type algorithm w");
    }

    var expr = new Expr.ExprAbs(List.copyOf(params), (Expr) blockAsExpr);

    expr.setInstantiateOperationCallback(instantiatedExpr -> {

      assert instantiatedExpr instanceof Expr.ExprAbs;
      var abs = (Expr.ExprAbs) instantiatedExpr;
      var body = abs.body();
      assert body instanceof Expr.ExprLetRec;

      var let = (Expr.ExprLetRec) body;

      var directChildren = OperationExprConversionUtils.getAllChildrenForScopeExpression(let, let.body());

      var inferredType = abs.getInferredType();
      assert inferredType.isPresent();
      var gpnt = inferredType.get().asTypeParameter();
      assert gpnt.isConcrete();

      Type funcType = Type.fromGeneralParameterizedNominalType(gpnt.getConcrete());
      assert funcType instanceof FuncType;

      var newFuncOp = new FuncOps.FuncOp(op.getLocation(), funcOp.getFuncName(), (FuncType) funcType);
      for (var child : directChildren) {
        var exprOp = child.getUnderlyingOperation();
        assert exprOp.isPresent();

        newFuncOp.addOperation(exprOp.get(), 0);
      }

      var newRegion = newFuncOp.getRegion();
      var oldParams = OperationExprConversionUtils.getAllAbstractedParamters(abs);
      assert oldParams.size() == newRegion.getRegionValues().size();

      for (int i = 0; i < oldParams.size(); i++) {
        var oldParam = oldParams.get(i);
        var newValue = newRegion.getRegionValue(i);

        if (oldParam.isValue() && newValue.isPresent()) {
          oldParam.getValue().replaceAllUsesIn(newValue.get(), newRegion);
        }
      }

      return newFuncOp.getOperation();
    });

    return expr;
  }

  public static Expr convertReturnOp(
      Operation op,
      TypeInference engine) {
    FuncOps.ReturnOp returnOp = (FuncOps.ReturnOp) op.asOp();

    Expr result = null;
    if (returnOp.getReturnValue().isPresent()) {
      result = new Expr.ExprReturn(new Expr.ExprVar(Symbol.of(returnOp.getReturnValue().get())));
    } else {
      result = new Expr.ExprReturn(new Expr.ExprLit(new Literal.Unit()));
    }

    result.setInstantiateOperationCallback(instantiatedExpr -> {

      assert instantiatedExpr instanceof Expr.ExprReturn;
      var retExpr = (Expr.ExprReturn) instantiatedExpr;

      var type = retExpr.getInferredType();
      assert type.isPresent();
      assert type.get().isFullySpecified();

      if (type.get().asTypeParameter().getConcrete().getIdent() == TypeIdent.TYPE_IDENT_UNIT) {
        var newOp = new FuncOps.ReturnOp(returnOp.getLocation());
        return newOp.getOperation();
      } else {

        // TODO(jan): here, some variables cannot get beta reduced, as this would
        // require application beta reduction!
        // That means, that the value might actually be a raw value and not an
        // operation. A helper method is needed to fix this!
        var valueSymbol = OperationExprConversionUtils.getOutputSymbol(retExpr.value());

        assert valueSymbol.isPresent();
        assert valueSymbol.get() instanceof Symbol.ValueSymbol<Expr, AlgorithmWType>;

        var newOp = new FuncOps.ReturnOp(returnOp.getLocation(), valueSymbol.get().getValue());
        return newOp.getOperation();
      }
    });

    return result;
  }

  public static Expr convertCallOp(
      Operation op,
      TypeInference engine) {
    FuncOps.CallOp callOp = (FuncOps.CallOp) op.asOp();

    var result = new Expr.ExprApp(new Expr.ExprVar(Symbol.of(callOp.getCallee())),
        callOp.getOperands().stream()
            .map(operand -> (Expr) new Expr.ExprVar(Symbol.of(operand.getValueOrThrow()))).toList());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprApp;

      var app = (Expr.ExprApp) instantiatedExpr;

      var funcType = app.getInferredFunctionType();
      assert funcType.isPresent();
      assert funcType.get() instanceof AlgorithmWType.Arrow;

      var irType = Type.fromGeneralParameterizedNominalType(funcType.get().asTypeParameter().getConcrete());
      assert irType instanceof FuncTypes.FuncType;

      var applicationArgs = OperationExprConversionUtils.getAllApplicationParameters(app);
      assert applicationArgs.stream().allMatch(arg -> arg.getUnderlyingOperation().isPresent()
          && arg.getUnderlyingOperation().get().getOutput().isPresent());

      var parameterOperations = applicationArgs.stream()
          .map(Expr::getUnderlyingOperation)
          .map(Optional::get)
          .map(Operation::getOutputValueOrThrow)
          .toList();

      return new CallOp(op.getLocation(), callOp.getCalleeName(), parameterOperations, (FuncTypes.FuncType) irType)
          .getOperation();
    });

    return result;
  }

  public static Expr convertCallIndirectOp(
      Operation op,
      TypeInference engine) {
    FuncOps.CallIndirectOp callOp = (FuncOps.CallIndirectOp) op.asOp();

    var functionValue = callOp.getOperandValue(0).get();
    var params = callOp.getOperands().subList(1, callOp.getOperands().size());

    var result = new Expr.ExprApp(new Expr.ExprVar(Symbol.of(functionValue)),
        params.stream()
            .map(operand -> (Expr) new Expr.ExprVar(Symbol.of(operand.getValueOrThrow()))).toList());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprApp;

      var app = (Expr.ExprApp) instantiatedExpr;

      var applicationArgs = OperationExprConversionUtils.getAllApplicationParameters(app);
      assert applicationArgs.stream().allMatch(arg -> arg.getUnderlyingOperation().isPresent()
          && arg.getUnderlyingOperation().get().getOutput().isPresent());

      var parameterOperations = applicationArgs.stream()
          .map(Expr::getUnderlyingOperation)
          .map(Optional::get)
          .map(Operation::getOutputValueOrThrow)
          .toList();

      var funcOp = app.func().getUnderlyingOperation();
      assert funcOp.isPresent();
      assert funcOp.get().asOp() instanceof FuncOps.ConstantOp;

      var constFunc = (FuncOps.ConstantOp) funcOp.get().asOp();

      return new CallIndirectOp(op.getLocation(), constFunc.getResult(), parameterOperations)
          .getOperation();
    });

    return result;
  }

  public static Expr convertConstantOp(
      Operation op,
      TypeInference engine) {
    FuncOps.ConstantOp constOp = (FuncOps.ConstantOp) op.asOp();

    var funcName = constOp.getFuncName();
    assert constOp.getFuncType() instanceof FuncType;
    var funcType = (FuncType) constOp.getFuncType();

    var values = new ArrayList<Symbol<Expr, AlgorithmWType>>(funcType.getInputs().size());

    for (@SuppressWarnings("unused")
    var input : funcType.getInputs()) {
      values.add(Symbol.of(new Value()));
    }

    var result = new Expr.ExprAbs(List.copyOf(values),
        new Expr.ExprApp(new Expr.ExprVar(Symbol.of(new SymbolTableSymbol(funcName, funcType))),
            values.stream().map(arg -> (Expr) new Expr.ExprVar(arg)).toList()));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprAbs;

      var abs = (Expr.ExprAbs) instantiatedExpr;

      assert abs.body() instanceof Expr.ExprApp;
      var app = (Expr.ExprApp) abs.body();

      var newFuncType = app.getInferredFunctionType();
      assert newFuncType.isPresent();
      assert newFuncType.get() instanceof AlgorithmWType.Arrow;

      var irType = Type.fromGeneralParameterizedNominalType(newFuncType.get().asTypeParameter().getConcrete());
      assert irType instanceof FuncTypes.FuncType;

      return new FuncOps.ConstantOp(op.getLocation(), funcName, (FuncTypes.FuncType) irType)
          .getOperation();
    });

    return result;
  }

}

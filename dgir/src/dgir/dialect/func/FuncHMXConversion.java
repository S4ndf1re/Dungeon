package dgir.dialect.func;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.Value;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.traits.ISymbol.SymbolTableSymbol;
import dgir.dialect.func.FuncOps.CallIndirectOp;
import dgir.dialect.func.FuncOps.CallOp;
import dgir.dialect.func.FuncTypes.FuncType;

public final class FuncHMXConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<HMXExpr, HMXType, TypeInference>addOperatorsToDialect(
        HMXInference.class,
        Pair.of(FuncOps.FuncOp.class, FuncHMXConversion::convertFuncOp),
        Pair.of(FuncOps.ReturnOp.class, FuncHMXConversion::convertReturnOp),
        Pair.of(FuncOps.CallOp.class, FuncHMXConversion::convertCallOp),
        Pair.of(FuncOps.CallIndirectOp.class, FuncHMXConversion::convertCallIndirectOp),
        Pair.of(FuncOps.ConstantOp.class, FuncHMXConversion::convertConstantOp));
  }

  public static HMXExpr convertFuncOp(
      Operation op,
      TypeInference engine) {
    FuncOps.FuncOp funcOp = (FuncOps.FuncOp) op.asOp();

    ArrayList<Symbol<HMXExpr, HMXType>> params = new ArrayList<>();
    for (int i = 0; funcOp.getArgument(i).isPresent(); i++) {
      params.add(Symbol.<HMXExpr, HMXType>of(funcOp.getArgument(i).get()));
    }

    var block = GeneralBlock.fromBlock(funcOp.getEntryBlock());
    var blockAsExpr = engine.generalBlockToInferenceExpr(block);

    if (!(blockAsExpr instanceof HMXExpr)) {
      throw new IllegalArgumentException("Invalid, as the engine is not of type hmx");
    }

    var expr = new HMXExpr.ExprAbs(List.copyOf(params), (HMXExpr) blockAsExpr);

    expr.setInstantiateOperationCallback(instantiatedExpr -> {

      assert instantiatedExpr instanceof HMXExpr.ExprAbs;
      var abs = (HMXExpr.ExprAbs) instantiatedExpr;
      var body = abs.body();

      Type funcType = abs.inferredTypeToIrType();
      assert funcType instanceof FuncType;

      var newFuncOp = new FuncOps.FuncOp(op.getLocation(), funcOp.getFuncName(), (FuncType) funcType);

      body.fillOpScoped(newFuncOp.getOperation(), 0);

      var newRegion = newFuncOp.getRegion();
      var oldParams = abs.getAllAbstractedParamters();
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

  public static HMXExpr convertReturnOp(
      Operation op,
      TypeInference engine) {
    FuncOps.ReturnOp returnOp = (FuncOps.ReturnOp) op.asOp();

    HMXExpr result = null;
    if (returnOp.getReturnValue().isPresent()) {
      result = new HMXExpr.ExprReturn(new HMXExpr.ExprVar(Symbol.of(returnOp.getReturnValue().get())));
    } else {
      result = new HMXExpr.ExprReturn(new HMXExpr.ExprLit(new Literal.Unit()));
    }

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprReturn;
      var retExpr = (HMXExpr.ExprReturn) instantiatedExpr;

      var type = retExpr.getInferredType();

      if (type.get().asTypeParameter().getConcrete().getIdent() == TypeIdent.TYPE_IDENT_UNIT) {
        var newOp = new FuncOps.ReturnOp(returnOp.getLocation());
        return newOp.getOperation();
      } else {
        var valueSymbol = retExpr.value().getOutputValue();

        var newOp = new FuncOps.ReturnOp(returnOp.getLocation(), valueSymbol);
        return newOp.getOperation();
      }
    });

    return result;
  }

  public static HMXExpr convertCallOp(
      Operation op,
      TypeInference engine) {
    FuncOps.CallOp callOp = (FuncOps.CallOp) op.asOp();

    var result = new HMXExpr.ExprApp(new HMXExpr.ExprVar(Symbol.of(callOp.getCallee())),
        callOp.getOperands().stream()
            .map(operand -> (HMXExpr) new HMXExpr.ExprVar(Symbol.of(operand.getValueOrThrow()))).toList());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprApp;

      var app = (HMXExpr.ExprApp) instantiatedExpr;

      var funcType = app.getInferredFunctionType();
      assert funcType.isPresent();
      assert funcType.get() instanceof HMXType.Arrow;

      var irType = Type.fromGeneralParameterizedNominalType(funcType.get().asTypeParameter().getConcrete());
      assert irType instanceof FuncTypes.FuncType;

      var applicationArgs = app.getAllApplicationParameters();

      var parameterOperations = applicationArgs.stream()
          .map(e -> e.getOutputValue())
          .toList();

      return new CallOp(op.getLocation(), callOp.getCalleeName(), parameterOperations, (FuncTypes.FuncType) irType)
          .getOperation();
    });

    return result;
  }

  public static HMXExpr convertCallIndirectOp(
      Operation op,
      TypeInference engine) {
    FuncOps.CallIndirectOp callOp = (FuncOps.CallIndirectOp) op.asOp();

    var functionValue = callOp.getOperandValue(0).get();
    var params = callOp.getOperands().subList(1, callOp.getOperands().size());

    var result = new HMXExpr.ExprApp(new HMXExpr.ExprVar(Symbol.of(functionValue)),
        params.stream()
            .map(operand -> (HMXExpr) new HMXExpr.ExprVar(Symbol.of(operand.getValueOrThrow()))).toList());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprApp;

      var app = (HMXExpr.ExprApp) instantiatedExpr;

      var applicationArgs = app.getAllApplicationParameters();

      var parameterOperations = applicationArgs.stream()
          .map(e -> e.getOutputValue())
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

  public static HMXExpr convertConstantOp(
      Operation op,
      TypeInference engine) {
    FuncOps.ConstantOp constOp = (FuncOps.ConstantOp) op.asOp();

    var funcName = constOp.getFuncName();
    assert constOp.getFuncType() instanceof FuncType;
    var funcType = (FuncType) constOp.getFuncType();

    var values = new ArrayList<Symbol<HMXExpr, HMXType>>(funcType.getInputs().size());

    for (@SuppressWarnings("unused")
    var input : funcType.getInputs()) {
      values.add(Symbol.of(new Value()));
    }

    var result = new HMXExpr.ExprAbs(List.copyOf(values),
        new HMXExpr.ExprApp(new HMXExpr.ExprVar(Symbol.of(new SymbolTableSymbol(funcName, funcType))),
            values.stream().map(arg -> (HMXExpr) new HMXExpr.ExprVar(arg)).toList()));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprAbs;

      var abs = (HMXExpr.ExprAbs) instantiatedExpr;

      assert abs.body() instanceof HMXExpr.ExprApp;
      var app = (HMXExpr.ExprApp) abs.body();

      var irType = app.inferredTypeToIrType();
      assert irType instanceof FuncTypes.FuncType;

      return new FuncOps.ConstantOp(op.getLocation(), funcName, (FuncTypes.FuncType) irType)
          .getOperation();
    });

    return result;
  }

}

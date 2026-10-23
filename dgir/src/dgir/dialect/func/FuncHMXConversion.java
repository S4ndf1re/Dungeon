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
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.hmx.expr.Abs;
import dgir.core.ir.types.builtin.hmx.expr.Ann;
import dgir.core.ir.types.builtin.hmx.expr.App;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.expr.Lit;
import dgir.core.ir.types.builtin.hmx.expr.Return;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.builtin.hmx.expr.Var;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.traits.IBlock;
import dgir.core.traits.ISymbol.SymbolTableSymbol;
import dgir.dialect.func.FuncOps.CallIndirectOp;
import dgir.dialect.func.FuncOps.CallOp;
import dgir.dialect.func.FuncTypes.FuncType;

public final class FuncHMXConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinHMXConversion() {
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

    var fnType = funcOp.getType();
    var annotationTypes = new ArrayList<HMXType>();
    // Add all input types
    {
      // TODO: create a sensible helper!!!!! that must happen more than once, i am
      // sure!
      if (fnType.getInputs().size() > 0) {
        for (var argType : fnType.getInputs()) {
          if (argType.isKnown()) {
            var engineType = engine
                .generalNominalTypeToInferenceType(argType.getAsKnownOrThrow().asParameterizedNominalType(), null);
            annotationTypes.add(engineType.getLeft());
          } else {
            annotationTypes.add(new HMXType.Var(new TypeVar<>()));
          }
        }
      } else {
        annotationTypes.add(new HMXType.LitType(TypeIdent.TYPE_IDENT_UNIT));
      }

      // Add the output Type
      annotationTypes.add(new HMXType.Var(new TypeVar<>()));
    }

    var specializedFuncType = engine.ArrowChain(annotationTypes);

    ArrayList<Symbol<HMXExpr, HMXType>> params = new ArrayList<>();
    for (int i = 0; funcOp.getArgument(i).isPresent(); i++) {
      params.add(Symbol.<HMXExpr, HMXType>of(funcOp.getArgument(i).get()));
    }

    var block = GeneralBlock.fromBlock(funcOp.getEntryBlock());
    var blockAsExpr = engine.generalBlockToInferenceExpr(block);

    if (!(blockAsExpr instanceof HMXExpr)) {
      throw new IllegalArgumentException("Invalid, as the engine is not of type hmx");
    }

    var function = new Abs(op.getLocation(), List.copyOf(params), (HMXExpr) blockAsExpr);
    var expr = new Ann(op.getLocation(), function,
        specializedFuncType);

    function.setInstantiateOperationCallback(instantiatedExpr -> {
      // NOTE: the type annotations are dropped during inference!
      assert instantiatedExpr instanceof Abs;
      var abs = (Abs) instantiatedExpr;
      var body = abs.body();
      assert body instanceof IBlock;
      @SuppressWarnings("unchecked")
      var bodyBlock = (IBlock<HMXExpr, HMXType>) body;

      Type funcType = abs.inferredTypeToIrType();
      assert funcType instanceof FuncType;

      var newFuncOp = new FuncOps.FuncOp(op.getLocation(), funcOp.getFuncName(), (FuncType) funcType);
      bodyBlock.fillEntryBlockInOp(newFuncOp, 0);

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
      result = new Return(returnOp.getLocation(),
          new Var(returnOp.getLocation(), Symbol.of(returnOp.getReturnValue().get())));
    } else {
      result = new Return(returnOp.getLocation(), new Lit(returnOp.getLocation(), new Literal.Unit()));
    }

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Return;
      var retExpr = (Return) instantiatedExpr;

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

    var result = new App(callOp.getLocation(), new Var(callOp.getLocation(), Symbol.of(callOp.getCallee())),
        callOp.getOperands().stream()
            .map(operand -> (HMXExpr) new Var(callOp.getLocation(), Symbol.of(operand.getValueOrThrow()))).toList());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof App;

      var app = (App) instantiatedExpr;

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

    var result = new App(callOp.getLocation(), new Var(callOp.getLocation(), Symbol.of(functionValue)),
        params.stream()
            .map(operand -> (HMXExpr) new Var(callOp.getLocation(), Symbol.of(operand.getValueOrThrow()))).toList());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof App;

      var app = (App) instantiatedExpr;

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

    var result = new Abs(constOp.getLocation(), List.copyOf(values),
        new App(constOp.getLocation(),
            new Var(constOp.getLocation(), Symbol.of(new SymbolTableSymbol(funcName, funcType))),
            values.stream().map(arg -> (HMXExpr) new Var(constOp.getLocation(), arg)).toList()));

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Abs;

      var abs = (Abs) instantiatedExpr;

      assert abs.body() instanceof App;
      var app = (App) abs.body();

      var irType = app.inferredTypeToIrType();
      assert irType instanceof FuncTypes.FuncType;

      return new FuncOps.ConstantOp(op.getLocation(), funcName, (FuncTypes.FuncType) irType)
          .getOperation();
    });

    return result;
  }

}

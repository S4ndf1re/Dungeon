package dgir.dialect.func;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.Value;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;

import java.util.Optional;
import dgir.core.ir.types.SystemFConversionUtils;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.systemf.Expr;
import dgir.core.ir.types.systemf.SystemFInference;
import dgir.core.ir.types.systemf.SystemFType;
import dgir.core.ir.types.systemf.TypeInference;
import dgir.core.traits.ISymbol.SymbolTableSymbol;
import dgir.dialect.func.FuncOps.CallIndirectOp;
import dgir.dialect.func.FuncOps.CallOp;
import dgir.dialect.func.FuncOps.ConstantOp;
import dgir.dialect.func.FuncTypes.FuncType;

public final class FuncSystemFConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinSystemFConversion() {
    ConverterRegistry.<ExprOrOperator<Expr, SystemFType>, Expr, SystemFType, TypeInference>addOperatorsToDialect(
        SystemFInference.class,
        Pair.of(FuncOps.FuncOp.class, FuncSystemFConversion::convertFuncOp),
        Pair.of(FuncOps.ReturnOp.class, FuncSystemFConversion::convertReturnOp),
        Pair.of(FuncOps.CallOp.class, FuncSystemFConversion::convertCallOp),
        Pair.of(FuncOps.CallIndirectOp.class, FuncSystemFConversion::convertCallIndirectOp),
        Pair.of(FuncOps.ConstantOp.class, FuncSystemFConversion::convertConstantOp));
  }

  private static SystemFType irTypeToSystemF(TypeInference engine, Type irType) {
    return SystemFConversionUtils.irTypeToSystemF(engine, irType);
  }

  private static FuncType systemFTypeToFuncType(SystemFType ty) {
    var irType = SystemFConversionUtils.systemFTypeToIrType(ty);
    assert irType instanceof FuncType;
    return (FuncType) irType;
  }

  /**
   * Builds a function application from plain `App` nodes; the result type is
   * inferred. A zero argument application supplies the unit input placeholder
   * implicitly.
   */
  private static Expr buildApplication(Expr fun, List<Expr> args) {
    if (args.isEmpty()) {
      return new Expr.App(fun);
    }
    Expr app = fun;
    for (var arg : args) {
      app = new Expr.App(app, arg);
    }
    return app;
  }

  /**
   * Peels an application chain into its head expression and the arguments in
   * application order.
   */
  private static Pair<Expr, List<Expr>> peelApplication(Expr expr) {
    var args = new ArrayDeque<Expr>();
    var current = expr;
    while (current instanceof Expr.App app) {
      app.arg().ifPresent(args::addFirst);
      current = app.fun();
    }
    return Pair.of(current, List.copyOf(args));
  }

  public static Expr convertFuncOp(
      Operation op,
      TypeInference engine) {
    FuncOps.FuncOp funcOp = (FuncOps.FuncOp) op.asOp();

    ArrayList<Symbol<Expr, SystemFType>> params = new ArrayList<>();
    ArrayList<SystemFType> paramTypes = new ArrayList<>();
    for (int i = 0; funcOp.getArgument(i).isPresent(); i++) {
      var argument = funcOp.getArgument(i).get();
      assert argument.getType().isKnown()
          : "function parameters must have a declared type for System F";
      params.add(Symbol.<Expr, SystemFType>of(argument));
      paramTypes.add(irTypeToSystemF(engine, argument.getType().getAsKnownOrThrow()));
    }

    var block = GeneralBlock.fromBlock(funcOp.getEntryBlock());
    var blockAsExpr = engine.generalBlockToInferenceExpr(block);

    Expr expr = blockAsExpr;
    for (int i = params.size() - 1; i >= 0; i--) {
      expr = new Expr.Abs(params.get(i), paramTypes.get(i), expr);
    }
    if (params.isEmpty()) {
      // A zero parameter function is a function taking the unit placeholder:
      // unit -> body
      expr = new Expr.Abs(expr);
    }

    expr.setInstantiateOperationCallback(instantiatedExpr -> {

      assert instantiatedExpr instanceof Expr.Abs;
      var abs = (Expr.Abs) instantiatedExpr;
      assert abs.body() instanceof Expr.Let;
      var let = (Expr.Let) abs.body();

      var directChildren = OperationExprConversionUtils.getAllChildrenForScopeExpression(let, let.body());

      // Reconstruct the function type from inference. For zero parameter
      // functions the expression carries the unit placeholder type
      // (unit -> body), so the originally declared type is kept.
      FuncType funcType = funcOp.getType();
      if (!params.isEmpty()) {
        var inferredType = abs.getInferredType();
        assert inferredType.isPresent();
        assert inferredType.get().isFullySpecified();
        funcType = systemFTypeToFuncType(inferredType.get());
      }

      var newFuncOp = new FuncOps.FuncOp(op.getLocation(), funcOp.getFuncName(), funcType);
      for (var child : directChildren) {
        var exprOp = child.getUnderlyingOperation();
        assert exprOp.isPresent();

        newFuncOp.addOperation(exprOp.get(), 0);
      }

      var newRegion = newFuncOp.getRegion();
      if (!params.isEmpty()) {
        var oldParams = OperationExprConversionUtils.getAllAbstractedParamters(abs);
        assert oldParams.size() == newRegion.getRegionValues().size();

        for (int i = 0; i < oldParams.size(); i++) {
          var oldParam = oldParams.get(i);
          var newValue = newRegion.getRegionValue(i);

          if (oldParam.isValue() && newValue.isPresent()) {
            oldParam.getValue().replaceAllUsesIn(newValue.get(), newRegion);
          }
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

    Expr result;
    if (returnOp.getReturnValue().isPresent()) {
      result = new Expr.Return(new Expr.Var(Symbol.of(returnOp.getReturnValue().get())));
    } else {
      result = new Expr.Return(new Expr.LitExpr(new Literal.Unit()));
    }

    result.setInstantiateOperationCallback(instantiatedExpr -> {

      assert instantiatedExpr instanceof Expr.Return;
      var retExpr = (Expr.Return) instantiatedExpr;

      // Whether a value is returned is statically known from the original op
      if (returnOp.getReturnValue().isEmpty()) {
        return new FuncOps.ReturnOp(returnOp.getLocation()).getOperation();
      }

      var valueSymbol = OperationExprConversionUtils.getOutputSymbol(retExpr.value());

      assert valueSymbol.isPresent();
      assert valueSymbol.get() instanceof Symbol.ValueSymbol<Expr, SystemFType>;

      return new FuncOps.ReturnOp(returnOp.getLocation(), valueSymbol.get().getValue())
          .getOperation();
    });

    return result;
  }

  public static Expr convertCallOp(
      Operation op,
      TypeInference engine) {
    FuncOps.CallOp callOp = (FuncOps.CallOp) op.asOp();
    var calleeType = (FuncType) callOp.getCalleeType();

    var fun = new Expr.Var(Symbol.of(callOp.getCallee()));
    var args = callOp.getOperands().stream()
        .map(operand -> (Expr) new Expr.Var(Symbol.of(operand.getValueOrThrow()))).toList();

    var result = buildApplication(fun, args);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      // The operands are taken from the instantiated application chain, as
      // beta reduction/substitution may have replaced the original values!
      var argExprs = peelApplication(instantiatedExpr).getRight();
      assert argExprs.stream().allMatch(arg -> arg.getUnderlyingOperation().isPresent()
          && arg.getUnderlyingOperation().get().getOutput().isPresent());

      var parameterOperations = argExprs.stream()
          .map(Expr::getUnderlyingOperation)
          .map(Optional::get)
          .map(Operation::getOutputValueOrThrow)
          .toList();

      return new CallOp(op.getLocation(), callOp.getCalleeName(), parameterOperations, calleeType)
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

    var fun = new Expr.Var(Symbol.of(functionValue));
    var args = params.stream()
        .map(operand -> (Expr) new Expr.Var(Symbol.of(operand.getValueOrThrow()))).toList();

    var result = buildApplication(fun, args);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      var application = peelApplication(instantiatedExpr);
      var funExpr = application.getLeft();

      var funcOp = funExpr.getUnderlyingOperation();
      assert funcOp.isPresent();
      assert funcOp.get().asOp() instanceof ConstantOp;

      var constFunc = (ConstantOp) funcOp.get().asOp();

      // The operands are taken from the instantiated application chain, as
      // beta reduction/substitution may have replaced the original values!
      var argExprs = application.getRight();
      assert argExprs.stream().allMatch(arg -> arg.getUnderlyingOperation().isPresent()
          && arg.getUnderlyingOperation().get().getOutput().isPresent());

      var parameterOperations = argExprs.stream()
          .map(Expr::getUnderlyingOperation)
          .map(Optional::get)
          .map(Operation::getOutputValueOrThrow)
          .toList();

      return new CallIndirectOp(op.getLocation(), constFunc.getResult(), parameterOperations)
          .getOperation();
    });

    return result;
  }

  public static Expr convertConstantOp(
      Operation op,
      TypeInference engine) {
    ConstantOp constOp = (ConstantOp) op.asOp();

    var funcName = constOp.getFuncName();
    assert constOp.getFuncType() instanceof FuncType;
    var funcType = (FuncType) constOp.getFuncType();

    var values = new ArrayList<Symbol<Expr, SystemFType>>(funcType.getInputs().size());
    var paramTypes = new ArrayList<SystemFType>(funcType.getInputs().size());

    for (var input : funcType.getInputs()) {
      assert input.isKnown() : "constant function references must have fully typed signatures";
      values.add(Symbol.of(new Value()));
      paramTypes.add(irTypeToSystemF(engine, input.getAsKnownOrThrow()));
    }

    var fun = new Expr.Var(Symbol.of(new SymbolTableSymbol(funcName, funcType)));
    var args = values.stream().map(arg -> (Expr) new Expr.Var(arg)).toList();

    Expr result = buildApplication(fun, args);
    for (int i = values.size() - 1; i >= 0; i--) {
      result = new Expr.Abs(values.get(i), paramTypes.get(i), result);
    }
    if (values.isEmpty()) {
      // A first-class reference to a zero parameter function is a value of
      // function type (unit -> r), not of the result type!
      result = new Expr.Abs(result);
    }

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      // The signature of the referenced function is statically known
      return new ConstantOp(op.getLocation(), funcName, funcType)
          .getOperation();
    });

    return result;
  }
}

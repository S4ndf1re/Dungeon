package dgir.dialect.mem;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.builtin.hmx.expr.App;
import dgir.core.ir.types.builtin.hmx.expr.Lit;
import dgir.core.ir.types.builtin.hmx.expr.Untyped;
import dgir.core.ir.types.compatibility.ConverterRegistry;

public final class MemHMXConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinHMXConversion() {
    ConverterRegistry.<HMXExpr, HMXType, TypeInference>addOperatorsToDialect(
        HMXInference.class,
        Pair.of(MemOps.AllocGcOp.class, MemHMXConversion::convertAllocGcOp),
        Pair.of(MemOps.AllocGcFromElementsOp.class, MemHMXConversion::convertAllocGcFromElementsOp),
        Pair.of(MemOps.SetElementOp.class, MemHMXConversion::convertSetElementOp),
        Pair.of(MemOps.GetElementOp.class, MemHMXConversion::convertGetElementOp));
  }

  public static HMXExpr convertAllocGcOp(
      Operation op,
      TypeInference engine) {

    var allocOp = (MemOps.AllocGcOp) op.asOp();

    HMXExpr result;
    if (allocOp.getOperand().isPresent() && allocOp.getOperand().get().isPresent()) {
      // The dynamic size must be an integer: unlike algorithm W, HM(X) solves
      // applications via subtype constraints, so declaring the parameter as the
      // integer supertype directly accepts any concrete integer width.
      var sizeVar = new TypeVar<HMXType>();
      var fnType = engine.ArrowChain(
          new HMXType.LitType(allocOp.getLocation(), TypeIdent.from("integer")),
          new HMXType.Var(allocOp.getLocation(), sizeVar));

      result = OperationExprConversionUtils.buildAnnotatedApplication(
          engine,
          List.of(engine.Var(allocOp.getLocation(), Symbol.of(allocOp.getOperand().get().get()))),
          new Lit(allocOp.getLocation(), new Literal.Generic(allocOp.getResult())),
          fnType,
          allocOp.getLocation());
    } else {
      result = new Lit(allocOp.getLocation(), new Literal.Generic(allocOp.getResult()));
    }

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      if (instantiatedExpr instanceof Lit lit) {
        var irType = lit.inferredTypeToIrType();
        assert irType instanceof MemTypes.ArrayT;
        var memType = (MemTypes.ArrayT) irType;

        return new MemOps.AllocGcOp(allocOp.getLocation(), memType.getElementType(), memType.getWidth()).getOperation();
      } else if (instantiatedExpr instanceof App app) {
        var irType = app.inferredTypeToIrType();
        assert irType instanceof MemTypes.ArrayT;
        var memType = (MemTypes.ArrayT) irType;
        return new MemOps.AllocGcOp(allocOp.getLocation(), memType.getElementType(),
            app.args().getFirst().getOutputValue()).getOperation();
      } else {
        throw new RuntimeException("Invalid Path, as this path depends on the previous result creation");
      }
    });

    return result;
  }

  public static HMXExpr convertAllocGcFromElementsOp(
      Operation op,
      TypeInference engine) {

    var allocOp = (MemOps.AllocGcFromElementsOp) op.asOp();

    TypeVar<HMXType> elemType = new TypeVar<>();
    TypeVar<HMXType> widthType = new TypeVar<>();

    // Define Type e -> e -> e -> ... -> mem.array<...>, with the array's
    // parameter count mirroring the op result's actual array type
    HMXType resultType = new HMXType.LitType(allocOp.getLocation(), TypeIdent.TYPE_IDENT_ARRAY,
        List.of(new HMXType.Var(elemType), new HMXType.Var(widthType)));
    for (int i = 0; i < allocOp.getOperands().size(); i++) {
      resultType = new HMXType.Arrow(allocOp.getLocation(),
          new HMXType.Var(allocOp.getLocation(), elemType), resultType);
    }

    var result = OperationExprConversionUtils.buildAnnotatedApplication(engine,
        allocOp.getOperands().stream().map(aOp -> engine.Var(allocOp.getLocation(), Symbol.of(aOp.getValueOrThrow())))
            .toList(),
        new Lit(new Literal.Generic(allocOp.getResult())), resultType, allocOp.getLocation());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof App;
      var app = (App) instantiatedExpr;

      var irType = app.inferredTypeToIrType();
      assert irType instanceof MemTypes.ArrayT;
      var memType = (MemTypes.ArrayT) irType;

      return new MemOps.AllocGcFromElementsOp(allocOp.getLocation(), memType.getElementType(),
          app.args().stream().map(e -> e.getOutputValue()).toList(), memType.getWidth().isPresent())
          .getOperation();

    });

    return result;
  }

  public static HMXExpr convertSetElementOp(
      Operation op,
      TypeInference engine) {

    var setElemOp = (MemOps.SetElementOp) op.asOp();

    TypeVar<HMXType> elemTypeVar = new TypeVar<>();
    TypeVar<HMXType> widthTypeVar = new TypeVar<>();
    var arrayType = new HMXType.LitType(setElemOp.getLocation(),
        TypeIdent.TYPE_IDENT_ARRAY,
        List.of(new HMXType.Var(setElemOp.getLocation(), elemTypeVar),
            new HMXType.Var(setElemOp.getLocation(), widthTypeVar)));

    var funcType = engine.ArrowChain(
        arrayType,
        new HMXType.LitType(setElemOp.getLocation(), TypeIdent.from("integer")),
        new HMXType.Var(setElemOp.getLocation(), elemTypeVar),
        new HMXType.LitType(setElemOp.getLocation(), TypeIdent.TYPE_IDENT_UNIT));

    var result = OperationExprConversionUtils.buildAnnotatedApplication(
        engine,
        setElemOp.getOperands().stream().map(aOp -> engine.Var(Symbol.of(aOp.getValueOrThrow()))).toList(),
        new Lit(new Literal.Unit()),
        funcType,
        setElemOp.getLocation());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof App;
      var app = (App) instantiatedExpr;

      return new MemOps.SetElementOp(setElemOp.getLocation(),
          app.args().get(0).getOutputValue(),
          app.args().get(1).getOutputValue(),
          app.args().get(2).getOutputValue()).getOperation();
    });

    return result;
  }

  public static HMXExpr convertGetElementOp(
      Operation op,
      TypeInference engine) {

    var getElemOp = (MemOps.GetElementOp) op.asOp();

    TypeVar<HMXType> elemTypeVar = new TypeVar<>();
    TypeVar<HMXType> widthTypeVar = new TypeVar<>();
    var arrayType = new HMXType.LitType(getElemOp.getLocation(),
        TypeIdent.TYPE_IDENT_ARRAY,
        List.of(new HMXType.Var(getElemOp.getLocation(), elemTypeVar),
            new HMXType.Var(getElemOp.getLocation(), widthTypeVar)));
    var funcType = engine.ArrowChain(
        arrayType,
        new HMXType.LitType(getElemOp.getLocation(), TypeIdent.from("integer")),
        new HMXType.Var(getElemOp.getLocation(), elemTypeVar));

    var result = OperationExprConversionUtils.buildAnnotatedApplication(
        engine,
        getElemOp.getOperands().stream()
            .map(aOp -> engine.Var(getElemOp.getLocation(), Symbol.of(aOp.getValueOrThrow()))).toList(),
        new Untyped(getElemOp.getLocation()),
        funcType,
        getElemOp.getLocation());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof App;
      var app = (App) instantiatedExpr;

      return new MemOps.GetElementOp(getElemOp.getLocation(),
          app.args().get(0).getOutputValue(),
          app.args().get(1).getOutputValue()).getOperation();
    });

    return result;
  }
}

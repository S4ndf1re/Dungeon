package dgir.dialect.mem;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.algorithmw.expr.Abs;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.expr.App;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.expr.Lit;
import dgir.core.ir.types.builtin.algorithmw.expr.Untyped;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;
import dgir.core.ir.types.builtin.algorithmw.expr.Var;
import dgir.core.ir.types.compatibility.ConverterRegistry;

public final class MemAlgoWConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<Expr, AlgorithmWType, TypeInference>addOperatorsToDialect(
        AlgorithmWInference.class,
        Pair.of(MemOps.AllocGcOp.class, MemAlgoWConversion::convertAllocGcOp),
        Pair.of(MemOps.AllocGcFromElementsOp.class, MemAlgoWConversion::convertAllocGcFromElementsOp),
        Pair.of(MemOps.SetElementOp.class, MemAlgoWConversion::convertSetElementOp),
        Pair.of(MemOps.GetElementOp.class, MemAlgoWConversion::convertGetElementOp));
  }

  public static Expr convertAllocGcOp(
      Operation op,
      TypeInference engine) {

    var allocOp = (MemOps.AllocGcOp) op.asOp();

    Expr result = null;
    if (allocOp.getOperand().isPresent() && allocOp.getOperand().get().isPresent()) {
      Symbol<Expr, AlgorithmWType> param = Symbol.of(new Value());
      // FIXME: This must be a subtype of Int, but this cannot be achieved with
      // algorithmw. This will lead to errors in laters check stages, but not directly
      // during type inference
      result = new App(allocOp.getLocation(),
          new Abs(allocOp.getLocation(), param, new Lit(new Literal.Generic(allocOp.getResult()))),
          new Var(Symbol.of(allocOp.getOperand().get().get())));
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

  public static Expr convertAllocGcFromElementsOp(
      Operation op,
      TypeInference engine) {

    var allocOp = (MemOps.AllocGcFromElementsOp) op.asOp();

    TypeVar<AlgorithmWType> elemType = new TypeVar<>();
    TypeVar<AlgorithmWType> widthType = new TypeVar<>();

    // Define Type e -> e -> e -> ... -> mem.array<...>, with the array's
    // parameter count mirroring the op result's actual array type
    AlgorithmWType resultType = new AlgorithmWType.LitType(allocOp.getLocation(), TypeIdent.TYPE_IDENT_ARRAY,
        List.of(new AlgorithmWType.Var(elemType), new AlgorithmWType.Var(widthType)));

    for (int i = 0; i < allocOp.getOperands().size(); i++) {
      resultType = new AlgorithmWType.Arrow(allocOp.getLocation(), new AlgorithmWType.Var(elemType), resultType);
    }

    // Define Expression for type e -> e -> e -> ... -> mem.array<e>
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

  public static Expr convertSetElementOp(
      Operation op,
      TypeInference engine) {

    var setElemOp = (MemOps.SetElementOp) op.asOp();

    // FIXME: since AlgoW doesn't support subtyping, it is unclear how to proceed
    // with the index parameter, as any integer type is accepted.
    // Two Options:
    // 1. Just assume a concrete type (i.e. int32)
    // 2. Let the error occur during operation checking (this will catch typeing
    // errors, but only after all types are already inferred)
    // IN THIS CASE: Option 2 is preferred
    //
    // Construct the function type mem.array<...> -> idx -> elem -> unit.
    // The operand array's arity is not known before inference: accept both the
    // dynamic (array<e>) and static-width (array<e, w>) form.
    TypeVar<AlgorithmWType> elemTypeVar = new TypeVar<>();
    TypeVar<AlgorithmWType> indexTypeVar = new TypeVar<>();
    TypeVar<AlgorithmWType> widthTypeVar = new TypeVar<>();

    var arrayType = new AlgorithmWType.LitType(
        setElemOp.getLocation(),
        TypeIdent.TYPE_IDENT_ARRAY,
        List.of(
            new AlgorithmWType.Var(setElemOp.getLocation(), elemTypeVar),
            new AlgorithmWType.Var(setElemOp.getLocation(), widthTypeVar)));

    var funcType = engine.ArrowChain(
        arrayType,
        new AlgorithmWType.Var(setElemOp.getLocation(), indexTypeVar),
        new AlgorithmWType.Var(setElemOp.getLocation(), elemTypeVar),
        new AlgorithmWType.LitType(setElemOp.getLocation(), TypeIdent.TYPE_IDENT_UNIT));

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

  public static Expr convertGetElementOp(
      Operation op,
      TypeInference engine) {

    var getElement = (MemOps.GetElementOp) op.asOp();

    // FIXME: since AlgoW doesn't support subtyping, it is unclear how to proceed
    // with the index parameter, as any integer type is accepted.
    // Two Options:
    // 1. Just assume a concrete type (i.e. int32)
    // 2. Let the error occur during operation checking (this will catch typeing
    // errors, but only after all types are already inferred)
    // IN THIS CASE: Option 2 is preferred
    //
    // Construct the function type mem.array<...> -> idx -> elem.
    // See convertSetElementOp: accept both array arities.
    TypeVar<AlgorithmWType> elemTypeVar = new TypeVar<>();
    TypeVar<AlgorithmWType> indexTypeVar = new TypeVar<>();
    TypeVar<AlgorithmWType> widthTypeVar = new TypeVar<>();

    var arrayType = new AlgorithmWType.LitType(
        getElement.getLocation(),
        TypeIdent.TYPE_IDENT_ARRAY,
        List.of(
            new AlgorithmWType.Var(getElement.getLocation(), elemTypeVar),
            new AlgorithmWType.Var(getElement.getLocation(), widthTypeVar)));

    var funcType = engine.ArrowChain(
        arrayType,
        new AlgorithmWType.Var(getElement.getLocation(), indexTypeVar),
        new AlgorithmWType.Var(getElement.getLocation(), elemTypeVar));

    var result = OperationExprConversionUtils.buildAnnotatedApplication(
        engine,
        getElement.getOperands().stream()
            .map(aOp -> engine.Var(getElement.getLocation(), Symbol.of(aOp.getValueOrThrow()))).toList(),
        new Untyped(getElement.getLocation()),
        funcType,
        getElement.getLocation());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof App;
      var app = (App) instantiatedExpr;

      return new MemOps.GetElementOp(getElement.getLocation(),
          app.args().get(0).getOutputValue(),
          app.args().get(1).getOutputValue()).getOperation();
    });

    return result;
  }
}

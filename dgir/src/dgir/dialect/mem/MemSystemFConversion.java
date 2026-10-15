package dgir.dialect.mem;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.OperationExprConversionUtils;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.traits.IApplication;
import dgir.core.ir.types.builtin.systemf.Entry;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFInference;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.expr.App;
import dgir.core.ir.types.builtin.systemf.expr.Untyped;

public final class MemSystemFConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinSystemFConversion() {
    ConverterRegistry.<Expr, SystemFType, TypeInference>addOperatorsToDialect(
        SystemFInference.class,
        Pair.of(MemOps.AllocGcOp.class, MemSystemFConversion::convertAllocGcOp),
        Pair.of(MemOps.AllocGcFromElementsOp.class, MemSystemFConversion::convertAllocGcFromElementsOp),
        Pair.of(MemOps.SetElementOp.class, MemSystemFConversion::convertSetElementOp),
        Pair.of(MemOps.GetElementOp.class, MemSystemFConversion::convertGetElementOp));
  }

  /**
   * Introduces a fresh existential type variable into the start context and
   * returns it as an inferable type. Used for parameters whose concrete type
   * is not expressible as a single System F literal (e.g. "any integer
   * width"); the op verifier rejects ill-typed operands after inference.
   */
  private static SystemFType freshEtVar(TypeInference engine) {
    var etvar = new TypeVar<SystemFType>();
    engine.getStartContext().push(new Entry.ETVarBnd(etvar));
    return new SystemFType.EtVar(etvar);
  }

  public static Expr convertAllocGcOp(
      Operation op,
      TypeInference engine) {

    var allocOp = (MemOps.AllocGcOp) op.asOp();

    Expr body = engine.Lit(allocOp.getLocation(), new Literal.Generic(allocOp.getResult()));
    List<Expr> args;
    SystemFType fnType;
    if (allocOp.getOperand().isPresent() && allocOp.getOperand().get().isPresent()) {
      // The dynamic size may be any integer width; System F has no integer
      // supertype literal, so the size is accepted as a fresh existential and
      // checked by the op verifier (same option as AlgoW).
      var sizeEtVar = new TypeVar<SystemFType>();
      engine.getStartContext().push(new Entry.ETVarBnd(sizeEtVar));

      fnType = engine.ArrowChain(new SystemFType.EtVar(sizeEtVar), freshEtVar(engine));
      args = List.of(engine.Var(allocOp.getLocation(), Symbol.of(allocOp.getOperand().get().get())));
    } else {
      // Zero-arg creation: the unit input placeholder is applied implicitly.
      fnType = engine.ArrowChain(new SystemFType.Lit(TypeIdent.TYPE_IDENT_UNIT), freshEtVar(engine));
      args = List.of();
    }

    var result = OperationExprConversionUtils.buildAnnotatedApplication(
        engine,
        args,
        body,
        fnType,
        allocOp.getLocation());

    // NOTE: the callback lives on the application node: apps are never
    // beta-reduced, so the node survives substitution of referencing variables.
    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof App;
      var app = (App) instantiatedExpr;

      var irType = app.inferredTypeToIrType();
      assert irType instanceof MemTypes.ArrayT;
      var memType = (MemTypes.ArrayT) irType;

      if (app.getAllApplicationParameters().isEmpty()) {
        return new MemOps.AllocGcOp(allocOp.getLocation(), memType.getElementType(), memType.getWidth())
            .getOperation();
      }
      return new MemOps.AllocGcOp(allocOp.getLocation(), memType.getElementType(),
          app.getAllApplicationParameters().getFirst().getOutputValue()).getOperation();
    });

    return result;
  }

  public static Expr convertAllocGcFromElementsOp(
      Operation op,
      TypeInference engine) {

    var allocOp = (MemOps.AllocGcFromElementsOp) op.asOp();

    // Define Type e -> e -> e -> ... -> mem.array<...>, with the array's
    // parameter count mirroring the op result's actual array type
    var elemEtVar = new TypeVar<SystemFType>();
    engine.getStartContext().push(new Entry.ETVarBnd(elemEtVar));

    SystemFType resultType = new SystemFType.Lit(allocOp.getLocation(), TypeIdent.TYPE_IDENT_ARRAY,
        List.of(new SystemFType.EtVar(elemEtVar), freshEtVar(engine)));
    for (int i = 0; i < allocOp.getOperands().size(); i++) {
      resultType = new SystemFType.Arrow(allocOp.getLocation(), new SystemFType.EtVar(elemEtVar), resultType);
    }

    var result = OperationExprConversionUtils.buildAnnotatedApplication(
        engine,
        allocOp.getOperands().stream()
            .map(aOp -> engine.Var(allocOp.getLocation(), Symbol.of(aOp.getValueOrThrow()))).toList(),
        engine.Lit(allocOp.getLocation(), new Literal.Generic(allocOp.getResult())),
        resultType,
        allocOp.getLocation());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof IApplication;
      @SuppressWarnings("unchecked")
      var argExprs = ((IApplication<Expr, SystemFType>) instantiatedExpr).getAllApplicationParameters();

      var irType = ((Expr) instantiatedExpr).inferredTypeToIrType();
      assert irType instanceof MemTypes.ArrayT;
      var memType = (MemTypes.ArrayT) irType;

      return new MemOps.AllocGcFromElementsOp(allocOp.getLocation(), memType.getElementType(),
          argExprs.stream()
              .map(Expr::getOutputValue).toList(),
          memType.getWidth().isPresent())
          .getOperation();
    });

    return result;
  }

  public static Expr convertSetElementOp(
      Operation op,
      TypeInference engine) {

    var setElemOp = (MemOps.SetElementOp) op.asOp();

    // Construct the function type mem.array<...> -> idx -> elem -> unit.
    // The operand array's arity is not known before inference: accept both the
    // dynamic (array<e>) and static-width (array<e, w>) form.
    var elemTypeVar = new TypeVar<SystemFType>();
    var widthTypeVar = new TypeVar<SystemFType>();
    engine.getStartContext().push(new Entry.ETVarBnd(elemTypeVar));
    engine.getStartContext().push(new Entry.ETVarBnd(widthTypeVar));

    var arrayType = new SystemFType.Lit(setElemOp.getLocation(), TypeIdent.TYPE_IDENT_ARRAY,
        List.of(new SystemFType.EtVar(elemTypeVar), new SystemFType.EtVar(widthTypeVar)));

    var funcType = engine.ArrowChain(
        arrayType,
        freshEtVar(engine),
        new SystemFType.EtVar(elemTypeVar),
        new SystemFType.Lit(TypeIdent.TYPE_IDENT_UNIT));

    var result = OperationExprConversionUtils.buildAnnotatedApplication(
        engine,
        setElemOp.getOperands().stream()
            .map(aOp -> engine.Var(setElemOp.getLocation(), Symbol.of(aOp.getValueOrThrow()))).toList(),
        engine.Lit(setElemOp.getLocation(), new Literal.Unit()),
        funcType,
        setElemOp.getLocation());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof IApplication;
      @SuppressWarnings("unchecked")
      var argExprs = ((IApplication<Expr, SystemFType>) instantiatedExpr).getAllApplicationParameters();

      return new MemOps.SetElementOp(setElemOp.getLocation(),
          argExprs.get(0).getOutputValue(),
          argExprs.get(1).getOutputValue(),
          argExprs.get(2).getOutputValue()).getOperation();
    });

    return result;
  }

  public static Expr convertGetElementOp(
      Operation op,
      TypeInference engine) {

    var getElemOp = (MemOps.GetElementOp) op.asOp();

    // Construct the function type mem.array<...> -> idx -> elem.
    // See convertSetElementOp: accept both array arities.
    var elemTypeVar = new TypeVar<SystemFType>();
    var widthTypeVar = new TypeVar<SystemFType>();
    engine.getStartContext().push(new Entry.ETVarBnd(elemTypeVar));
    engine.getStartContext().push(new Entry.ETVarBnd(widthTypeVar));

    var arrayType = new SystemFType.Lit(getElemOp.getLocation(), TypeIdent.TYPE_IDENT_ARRAY,
        List.of(new SystemFType.EtVar(elemTypeVar), new SystemFType.EtVar(widthTypeVar)));

    var funcType = engine.ArrowChain(
        arrayType,
        freshEtVar(engine),
        new SystemFType.EtVar(elemTypeVar));

    var result = OperationExprConversionUtils.buildAnnotatedApplication(
        engine,
        getElemOp.getOperands().stream()
            .map(aOp -> engine.Var(getElemOp.getLocation(), Symbol.of(aOp.getValueOrThrow()))).toList(),
        new Untyped(getElemOp.getLocation()),
        funcType,
        getElemOp.getLocation());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof IApplication;
      @SuppressWarnings("unchecked")
      var argExprs = ((IApplication<Expr, SystemFType>) instantiatedExpr).getAllApplicationParameters();

      return new MemOps.GetElementOp(getElemOp.getLocation(),
          argExprs.get(0).getOutputValue(),
          argExprs.get(1).getOutputValue()).getOperation();
    });

    return result;
  }
}

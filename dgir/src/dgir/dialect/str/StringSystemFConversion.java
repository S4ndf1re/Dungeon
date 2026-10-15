package dgir.dialect.str;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.debug.Location;
import dgir.core.ir.Operation;
import dgir.core.ir.Value;
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

public final class StringSystemFConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinSystemFConversion() {
    ConverterRegistry.<Expr, SystemFType, TypeInference>addOperatorsToDialect(
        SystemFInference.class,
        Pair.of(StrOps.ToStringOp.class, StringSystemFConversion::convertToStringOp),
        Pair.of(StrOps.ConcatOp.class, StringSystemFConversion::convertConcatOp),
        Pair.of(StrOps.LengthOp.class, StringSystemFConversion::convertLengthOp),
        Pair.of(StrOps.CharAtOp.class, StringSystemFConversion::convertCharAtOp),
        Pair.of(StrOps.EqualsOp.class, StringSystemFConversion::convertEqualsOp),
        Pair.of(StrOps.IsEmptyOp.class, StringSystemFConversion::convertIsEmptyOp),
        Pair.of(StrOps.ToLowerCaseOp.class, StringSystemFConversion::convertToLowerCase),
        Pair.of(StrOps.ToUpperCaseOp.class, StringSystemFConversion::convertToUpperCase),
        Pair.of(StrOps.TrimOp.class, StringSystemFConversion::convertTrimOp),
        Pair.of(StrOps.SubstringOp.class, StringSystemFConversion::convertSubstringOp),
        Pair.of(StrOps.StartsWithOp.class, StringSystemFConversion::convertStartsWithOp),
        Pair.of(StrOps.EndsWithOp.class, StringSystemFConversion::convertEndsWithOp),
        Pair.of(StrOps.IndexOfOp.class, StringSystemFConversion::convertIndexOfOp),
        Pair.of(StrOps.LastIndexOfOp.class, StringSystemFConversion::convertLastIndexOfOp));
  }

  public static Expr convertToStringOp(
      Operation op,
      TypeInference engine) {
    var valueType = new TypeVar<SystemFType>();
    engine.getStartContext().push(new Entry.ETVarBnd(valueType));

    var fnType = engine
        .ArrowChain(new SystemFType.EtVar(valueType), new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING));
    return convertResultOp(op, engine, fnType, loc -> vals -> new StrOps.ToStringOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertConcatOp(
      Operation op,
      TypeInference engine) {
    var fnType = engine
        .ArrowChain(
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING));

    return convertResultOp(op, engine, fnType,
        loc -> vals -> new StrOps.ConcatOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertLengthOp(
      Operation op,
      TypeInference engine) {
    var fnType = engine
        .ArrowChain(
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.from("int32")));
    return convertResultOp(op, engine, fnType, loc -> vals -> new StrOps.LengthOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertCharAtOp(
      Operation op,
      TypeInference engine) {

    var idxType = new TypeVar<SystemFType>();
    engine.getStartContext().push(new Entry.ETVarBnd(idxType));

    var fnType = engine
        .ArrowChain(
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Var(idxType),
            new SystemFType.Lit(TypeIdent.from("uint16")));

    return convertResultOp(op, engine, fnType,
        loc -> vals -> new StrOps.CharAtOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertEqualsOp(
      Operation op,
      TypeInference engine) {

    var fnType = engine
        .ArrowChain(
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_BOOL));
    return convertResultOp(op, engine, fnType,
        loc -> vals -> new StrOps.EqualsOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertIsEmptyOp(
      Operation op,
      TypeInference engine) {
    var fnType = engine
        .ArrowChain(
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_BOOL));
    return convertResultOp(op, engine, fnType, loc -> vals -> new StrOps.IsEmptyOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertToLowerCase(
      Operation op,
      TypeInference engine) {
    var fnType = engine
        .ArrowChain(
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING));
    return convertResultOp(op, engine, fnType,
        loc -> vals -> new StrOps.ToLowerCaseOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertToUpperCase(
      Operation op,
      TypeInference engine) {
    var fnType = engine
        .ArrowChain(
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING));
    return convertResultOp(op, engine, fnType,
        loc -> vals -> new StrOps.ToUpperCaseOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertTrimOp(
      Operation op,
      TypeInference engine) {
    var fnType = engine
        .ArrowChain(
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING));
    return convertResultOp(op, engine, fnType, loc -> vals -> new StrOps.TrimOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertSubstringOp(
      Operation op,
      TypeInference engine) {
    SystemFType fnType = null;
    if (op.getOperands().size() == 3) {
      fnType = engine
          .ArrowChain(new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
              new SystemFType.Var(new TypeVar<>()),
              new SystemFType.Var(new TypeVar<>()),
              new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING));
    } else {
      fnType = engine
          .ArrowChain(new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
              new SystemFType.Var(new TypeVar<>()),
              new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING));
    }
    return convertResultOp(op, engine, fnType,
        loc -> vals -> vals.size() == 3
            ? new StrOps.SubstringOp(loc, vals.get(0), vals.get(1), vals.get(2)).getOperation()
            : new StrOps.SubstringOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertStartsWithOp(
      Operation op,
      TypeInference engine) {
    var fnType = engine
        .ArrowChain(new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_BOOL));
    return convertResultOp(op, engine, fnType,
        loc -> vals -> new StrOps.StartsWithOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertEndsWithOp(
      Operation op,
      TypeInference engine) {
    var fnType = engine
        .ArrowChain(new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_BOOL));
    return convertResultOp(op, engine, fnType,
        loc -> vals -> new StrOps.EndsWithOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertIndexOfOp(
      Operation op,
      TypeInference engine) {
    var fnType = engine
        .ArrowChain(new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.from("int32")));
    return convertResultOp(op, engine, fnType,
        loc -> vals -> new StrOps.IndexOfOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertLastIndexOfOp(
      Operation op,
      TypeInference engine) {
    var fnType = engine
        .ArrowChain(new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_STRING),
            new SystemFType.Lit(TypeIdent.from("int32")));
    return convertResultOp(op, engine, fnType,
        loc -> vals -> new StrOps.LastIndexOfOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  /**
   * Shared conversion shape for all str dialect ops that carry a result and a
   * fixed list of operand values.
   *
   * <p>
   * The op is lowered to a lambda abstraction over its result value, applied to
   * its operand expressions. During instantiation, the operation is rebuilt from
   * the fully instantiated operand operations' results. Type validation already
   * happens within the operation verification!
   *
   * <p>
   * System F requires every lambda parameter to carry an explicit type, which
   * is taken from the operand's declared type.
   *
   * @param op      the operation to convert
   * @param engine  the system f inference engine, used to translate IR types
   * @param factory given the op's location, yields a function that rebuilds the
   *                concrete operation from the instantiated operand result values
   * @return the application expression representing the operation
   */
  private static Expr convertResultOp(
      Operation op,
      TypeInference engine,
      SystemFType expectedType,
      Function<Location, Function<List<Value>, Operation>> factory) {

    assert op.getOutput().isPresent();

    var args = op.getOperands().stream()
        .map(operand -> engine.Var(op.getLocation(), Symbol.of(operand.getValueOrThrow())))
        .toList();

    var result = OperationExprConversionUtils.buildAnnotatedApplication(
        engine,
        args,
        engine.Lit(op.getLocation(), new Literal.Generic(op.getOutputValueOrThrow())),
        expectedType,
        op.getLocation());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof IApplication;

      @SuppressWarnings("unchecked")
      var argExprs = ((IApplication<Expr, SystemFType>) instantiatedExpr).getAllApplicationParameters();

      List<Value> argResults = argExprs.stream()
          .map(e -> e.getOutputValue())
          .toList();

      return factory.apply(op.getLocation()).apply(argResults);
    });

    return result;
  }
}

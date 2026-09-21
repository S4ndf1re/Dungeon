package dgir.dialect.str;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.debug.Location;
import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;

public final class StringHMXConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<HMXExpr, HMXType, TypeInference>addOperatorsToDialect(
        HMXInference.class,
        Pair.of(StrOps.ToStringOp.class, StringHMXConversion::convertToStringOp),
        Pair.of(StrOps.ConcatOp.class, StringHMXConversion::convertConcatOp),
        Pair.of(StrOps.LengthOp.class, StringHMXConversion::convertLengthOp),
        Pair.of(StrOps.CharAtOp.class, StringHMXConversion::convertCharAtOp),
        Pair.of(StrOps.EqualsOp.class, StringHMXConversion::convertEqualsOp),
        Pair.of(StrOps.IsEmptyOp.class, StringHMXConversion::convertIsEmptyOp),
        Pair.of(StrOps.ToLowerCaseOp.class, StringHMXConversion::convertToLowerCase),
        Pair.of(StrOps.ToUpperCaseOp.class, StringHMXConversion::convertToUpperCase),
        Pair.of(StrOps.TrimOp.class, StringHMXConversion::convertTrimOp),
        Pair.of(StrOps.SubstringOp.class, StringHMXConversion::convertSubstringOp),
        Pair.of(StrOps.StartsWithOp.class, StringHMXConversion::convertStartsWithOp),
        Pair.of(StrOps.EndsWithOp.class, StringHMXConversion::convertEndsWithOp),
        Pair.of(StrOps.IndexOfOp.class, StringHMXConversion::convertIndexOfOp),
        Pair.of(StrOps.LastIndexOfOp.class, StringHMXConversion::convertLastIndexOfOp));
  }

  public static HMXExpr convertToStringOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, loc -> vals -> new StrOps.ToStringOp(loc, vals.get(0)).getOperation());
  }

  public static HMXExpr convertConcatOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op,
        loc -> vals -> new StrOps.ConcatOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static HMXExpr convertLengthOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, loc -> vals -> new StrOps.LengthOp(loc, vals.get(0)).getOperation());
  }

  public static HMXExpr convertCharAtOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op,
        loc -> vals -> new StrOps.CharAtOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static HMXExpr convertEqualsOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op,
        loc -> vals -> new StrOps.EqualsOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static HMXExpr convertIsEmptyOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, loc -> vals -> new StrOps.IsEmptyOp(loc, vals.get(0)).getOperation());
  }

  public static HMXExpr convertToLowerCase(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, loc -> vals -> new StrOps.ToLowerCaseOp(loc, vals.get(0)).getOperation());
  }

  public static HMXExpr convertToUpperCase(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, loc -> vals -> new StrOps.ToUpperCaseOp(loc, vals.get(0)).getOperation());
  }

  public static HMXExpr convertTrimOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, loc -> vals -> new StrOps.TrimOp(loc, vals.get(0)).getOperation());
  }

  public static HMXExpr convertSubstringOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, loc -> vals -> vals.size() == 3
        ? new StrOps.SubstringOp(loc, vals.get(0), vals.get(1), vals.get(2)).getOperation()
        : new StrOps.SubstringOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static HMXExpr convertStartsWithOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op,
        loc -> vals -> new StrOps.StartsWithOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static HMXExpr convertEndsWithOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op,
        loc -> vals -> new StrOps.EndsWithOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static HMXExpr convertIndexOfOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op,
        loc -> vals -> new StrOps.IndexOfOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static HMXExpr convertLastIndexOfOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op,
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
   * @param op      the operation to convert
   * @param factory given the op's location, yields a function that rebuilds the
   *                concrete operation from the instantiated operand result values
   * @return the application expression representing the operation
   */
  private static HMXExpr convertResultOp(
      Operation op,
      Function<Location, Function<List<Value>, Operation>> factory) {

    assert op.getOutput().isPresent();

    List<Symbol<HMXExpr, HMXType>> params = op.getOperands().stream()
        .map(operand -> Symbol.<HMXExpr, HMXType>of(new Value()))
        .toList();

    var result = new HMXExpr.ExprApp(
        new HMXExpr.ExprAbs(params, new HMXExpr.ExprLit(new Literal.Generic(op.getOutputValueOrThrow()))),
        op.getOperands().stream()
            .<HMXExpr>map(operand -> new HMXExpr.ExprVar(Symbol.of(operand.getValueOrThrow())))
            .toList());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof HMXExpr.ExprApp;

      var app = (HMXExpr.ExprApp) instantiatedExpr;

      List<Value> argResults = app.args().stream()
          .map(e -> e.getOutputValue())
          .toList();

      return factory.apply(op.getLocation()).apply(argResults);
    });

    return result;
  }
}

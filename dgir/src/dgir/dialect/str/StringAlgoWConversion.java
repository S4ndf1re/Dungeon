package dgir.dialect.str;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.debug.Location;
import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.algorithmw.AlgorithmWType;
import dgir.core.ir.types.algorithmw.Expr;
import dgir.core.ir.types.algorithmw.TypeInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;

public final class StringAlgoWConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<ExprOrOperator<Expr, AlgorithmWType>, Expr, AlgorithmWType, TypeInference>addOperatorsToDialect(
        AlgorithmWInference.class,
        Pair.of(StrOps.ToStringOp.class, StringAlgoWConversion::convertToStringOp),
        Pair.of(StrOps.ConcatOp.class, StringAlgoWConversion::convertConcatOp),
        Pair.of(StrOps.LengthOp.class, StringAlgoWConversion::convertLengthOp),
        Pair.of(StrOps.CharAtOp.class, StringAlgoWConversion::convertCharAtOp),
        Pair.of(StrOps.EqualsOp.class, StringAlgoWConversion::convertEqualsOp),
        Pair.of(StrOps.IsEmptyOp.class, StringAlgoWConversion::convertIsEmptyOp),
        Pair.of(StrOps.ToLowerCaseOp.class, StringAlgoWConversion::convertToLowerCase),
        Pair.of(StrOps.ToUpperCaseOp.class, StringAlgoWConversion::convertToUpperCase),
        Pair.of(StrOps.TrimOp.class, StringAlgoWConversion::convertTrimOp),
        Pair.of(StrOps.SubstringOp.class, StringAlgoWConversion::convertSubstringOp),
        Pair.of(StrOps.StartsWithOp.class, StringAlgoWConversion::convertStartsWithOp),
        Pair.of(StrOps.EndsWithOp.class, StringAlgoWConversion::convertEndsWithOp),
        Pair.of(StrOps.IndexOfOp.class, StringAlgoWConversion::convertIndexOfOp),
        Pair.of(StrOps.LastIndexOfOp.class, StringAlgoWConversion::convertLastIndexOfOp));
  }

  public static Expr convertToStringOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, loc -> vals -> new StrOps.ToStringOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertConcatOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op,
        loc -> vals -> new StrOps.ConcatOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertLengthOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, loc -> vals -> new StrOps.LengthOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertCharAtOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op,
        loc -> vals -> new StrOps.CharAtOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertEqualsOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op,
        loc -> vals -> new StrOps.EqualsOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertIsEmptyOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, loc -> vals -> new StrOps.IsEmptyOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertToLowerCase(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, loc -> vals -> new StrOps.ToLowerCaseOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertToUpperCase(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, loc -> vals -> new StrOps.ToUpperCaseOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertTrimOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, loc -> vals -> new StrOps.TrimOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertSubstringOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, loc -> vals -> vals.size() == 3
        ? new StrOps.SubstringOp(loc, vals.get(0), vals.get(1), vals.get(2)).getOperation()
        : new StrOps.SubstringOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertStartsWithOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op,
        loc -> vals -> new StrOps.StartsWithOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertEndsWithOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op,
        loc -> vals -> new StrOps.EndsWithOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertIndexOfOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op,
        loc -> vals -> new StrOps.IndexOfOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertLastIndexOfOp(
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
   * @param op     the operation to convert
   * @param factory given the op's location, yields a function that rebuilds the
   *                concrete operation from the instantiated operand result values
   * @return the application expression representing the operation
   */
  private static Expr convertResultOp(
      Operation op,
      Function<Location, Function<List<Value>, Operation>> factory) {

    assert op.getOutput().isPresent();

    List<Symbol<Expr, AlgorithmWType>> params = op.getOperands().stream()
        .map(operand -> Symbol.<Expr, AlgorithmWType>of(new Value()))
        .toList();

    var result = new Expr.ExprApp(
        new Expr.ExprAbs(params, new Expr.ExprLit(new Literal.Generic(op.getOutputValueOrThrow()))),
        op.getOperands().stream()
            .<Expr>map(operand -> new Expr.ExprVar(Symbol.of(operand.getValueOrThrow())))
            .toList());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof Expr.ExprApp;

      var app = (Expr.ExprApp) instantiatedExpr;

      assert app.args().stream().allMatch(e -> e.getUnderlyingOperation().isPresent());
      assert app.args().stream().allMatch(e -> e.getUnderlyingOperation().get().getOutput().isPresent());

      List<Value> argResults = app.args().stream()
          .map(e -> e.getUnderlyingOperation().get().getOutputValueOrThrow())
          .toList();

      return factory.apply(op.getLocation()).apply(argResults);
    });

    return result;
  }
}

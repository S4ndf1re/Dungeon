package dgir.dialect.str;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.debug.Location;
import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.SystemFConversionUtils;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.systemf.Expr;
import dgir.core.ir.types.systemf.SystemFInference;
import dgir.core.ir.types.systemf.SystemFType;
import dgir.core.ir.types.systemf.TypeInference;

public final class StringSystemFConversion {
  // NOTE: this is still very error prone, as the functions and ops must match
  // perfectly. maybe there is a better way to do this in the future.
  public static void registerBuiltinSystemFConversion() {
    ConverterRegistry.<ExprOrOperator<Expr, SystemFType>, Expr, SystemFType, TypeInference>addOperatorsToDialect(
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
    return convertResultOp(op, engine, loc -> vals -> new StrOps.ToStringOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertConcatOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, engine,
        loc -> vals -> new StrOps.ConcatOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertLengthOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, engine, loc -> vals -> new StrOps.LengthOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertCharAtOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, engine,
        loc -> vals -> new StrOps.CharAtOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertEqualsOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, engine,
        loc -> vals -> new StrOps.EqualsOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertIsEmptyOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, engine, loc -> vals -> new StrOps.IsEmptyOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertToLowerCase(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, engine, loc -> vals -> new StrOps.ToLowerCaseOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertToUpperCase(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, engine, loc -> vals -> new StrOps.ToUpperCaseOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertTrimOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, engine, loc -> vals -> new StrOps.TrimOp(loc, vals.get(0)).getOperation());
  }

  public static Expr convertSubstringOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, engine,
        loc -> vals -> vals.size() == 3
            ? new StrOps.SubstringOp(loc, vals.get(0), vals.get(1), vals.get(2)).getOperation()
            : new StrOps.SubstringOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertStartsWithOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, engine,
        loc -> vals -> new StrOps.StartsWithOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertEndsWithOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, engine,
        loc -> vals -> new StrOps.EndsWithOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertIndexOfOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, engine,
        loc -> vals -> new StrOps.IndexOfOp(loc, vals.get(0), vals.get(1)).getOperation());
  }

  public static Expr convertLastIndexOfOp(
      Operation op,
      TypeInference engine) {
    return convertResultOp(op, engine,
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
   * @param op     the operation to convert
   * @param engine the system f inference engine, used to translate IR types
   * @param factory given the op's location, yields a function that rebuilds the
   *        concrete operation from the instantiated operand result values
   * @return the application expression representing the operation
   */
  private static Expr convertResultOp(
      Operation op,
      TypeInference engine,
      Function<Location, Function<List<Value>, Operation>> factory) {

    assert op.getOutput().isPresent();

    List<Symbol<Expr, SystemFType>> params = new ArrayList<>();
    List<SystemFType> paramTypes = new ArrayList<>();
    for (var operand : op.getOperands()) {
      var value = operand.getValueOrThrow();
      assert value.getType().isKnown() : "str operands must have declared types for System F";
      params.add(Symbol.<Expr, SystemFType>of(new Value()));
      paramTypes.add(SystemFConversionUtils.irTypeToSystemF(engine, value.getType().getAsKnownOrThrow()));
    }

    Expr abs = new Expr.LitExpr(new Literal.Generic(op.getOutputValueOrThrow()));
    for (int i = params.size() - 1; i >= 0; i--) {
      abs = new Expr.Abs(params.get(i), paramTypes.get(i), abs);
    }
    if (params.isEmpty()) {
      // A zero operand op is a function taking the unit placeholder
      abs = new Expr.Abs(abs);
    }

    var args = op.getOperands().stream()
        .<Expr>map(operand -> new Expr.Var(Symbol.of(operand.getValueOrThrow())))
        .toList();

    var result = buildApplication(abs, args);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      // The operands are taken from the instantiated application chain, as
      // beta reduction/substitution may have replaced the original values!
      var argExprs = peelApplication(instantiatedExpr).getRight();
      assert argExprs.stream().allMatch(arg -> arg.getUnderlyingOperation().isPresent()
          && arg.getUnderlyingOperation().get().getOutput().isPresent());

      List<Value> argResults = argExprs.stream()
          .map(Expr::getUnderlyingOperation)
          .map(Optional::get)
          .map(Operation::getOutputValueOrThrow)
          .toList();

      return factory.apply(op.getLocation()).apply(argResults);
    });

    return result;
  }

  /**
   * Builds a curried function application from plain `App` nodes. A zero
   * argument application supplies the unit input placeholder implicitly.
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
}

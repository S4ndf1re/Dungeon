import dgir.core.debug.Location;
import dgir.core.debug.ValueDebugInfo;
import dgir.core.ir.Dialect;
import dgir.core.ir.Value;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.builtin.hmx.expr.Abs;
import dgir.core.ir.types.builtin.hmx.expr.Ann;
import dgir.core.ir.types.builtin.hmx.expr.App;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.expr.LetRec;
import dgir.core.ir.types.builtin.hmx.expr.Lit;
import dgir.core.ir.types.builtin.hmx.expr.Tuple;
import dgir.core.ir.types.builtin.hmx.expr.Var;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HMXTest {

  @BeforeEach
  public void setup() {
    Dialect.registerAllDialects();
  }

  @Test
  public void algorithmWSimpleTest() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    var x = Symbol.<HMXExpr, HMXType>of(new Value());

    // let const = \x -> x in const 42
    HMXExpr expr = new App(new Abs(x, new Var(x)),
        new Lit(new Literal.Int(42)));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();

    assert result instanceof HMXType;
    assert result instanceof HMXType.LitType;
    assert ((HMXType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void algorithmWTest() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    var cnst = Symbol.<HMXExpr, HMXType>of(new Value());
    var x = Symbol.<HMXExpr, HMXType>of(new Value());
    var y = Symbol.<HMXExpr, HMXType>of(new Value());

    // let const = \x -> \y -> x in const 42 true
    HMXExpr expr = new LetRec(
        cnst,
        new Abs(x, new Abs(y, new Var(x))),
        new App(
            new App(
                new Var(cnst),
                new Lit(new Literal.Int(42))),
            new Lit(new Literal.Bool(true))));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();

    assert result instanceof HMXType;
    assert result instanceof HMXType.LitType;
    assert ((HMXType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void annotationTest() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    var cnst = Symbol.<HMXExpr, HMXType>of(new Value());
    var x = Symbol.<HMXExpr, HMXType>of(new Value());
    var y = Symbol.<HMXExpr, HMXType>of(new Value());

    // let const = \x -> \y -> x in const 42 true
    HMXExpr expr = new Ann(
        new LetRec(
            cnst,
            new Abs(x, new Abs(y, new Var(x))),
            new App(
                new App(
                    new Var(cnst),
                    new Lit(new Literal.Int(42))),
                new Lit(new Literal.Bool(true)))),
        new HMXType.LitType(TypeIdent.TYPE_IDENT_INT));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof HMXType;
    assert result instanceof HMXType.LitType;
    assert ((HMXType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  /**
   * Annotation tests
   */
  @Test
  public void annotation2Test() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    var cnst = Symbol.<HMXExpr, HMXType>of(new Value());
    var x = Symbol.<HMXExpr, HMXType>of(new Value());
    var y = Symbol.<HMXExpr, HMXType>of(new Value());

    // let const = \x -> \y -> x in const 42 true
    HMXExpr expr = new Ann(
        new LetRec(
            cnst,
            new Ann(
                new Abs(x, new Abs(y, new Var(x))),
                new HMXType.Arrow(
                    new HMXType.LitType(TypeIdent.TYPE_IDENT_INT),
                    new HMXType.Arrow(
                        new HMXType.LitType(TypeIdent.TYPE_IDENT_BOOL),
                        new HMXType.LitType(TypeIdent.TYPE_IDENT_INT)))),
            new App(
                new App(
                    new Var(cnst),
                    new Ann(
                        new Lit(new Literal.Int(42)),
                        new HMXType.LitType(TypeIdent.TYPE_IDENT_INT))),
                new Ann(
                    new Lit(new Literal.Bool(true)),
                    new HMXType.LitType(TypeIdent.TYPE_IDENT_BOOL)))),
        new HMXType.LitType(TypeIdent.TYPE_IDENT_INT)

    );

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof HMXType;
    assert result instanceof HMXType.LitType;
    assert ((HMXType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void cyclicFunctionUse() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    Symbol<HMXExpr, HMXType> a = Symbol.<HMXExpr, HMXType>of(new Value(new ValueDebugInfo(Location.UNKNOWN, "a")));
    Symbol<HMXExpr, HMXType> b = Symbol.<HMXExpr, HMXType>of(new Value(new ValueDebugInfo(Location.UNKNOWN, "b")));
    Symbol<HMXExpr, HMXType> x = Symbol.<HMXExpr, HMXType>of(new Value(new ValueDebugInfo(Location.UNKNOWN, "x")));
    Symbol<HMXExpr, HMXType> y = Symbol.<HMXExpr, HMXType>of(new Value(new ValueDebugInfo(Location.UNKNOWN, "y")));

    // let a : Int -> Int = \x.(b x)
    // b = \y.(a y)
    // in (a 10)

    HMXExpr expr = new LetRec(
        List.of(
            Pair.of(a,
                new Ann(
                    new Abs(x, new App(new Var(b), new Var(x))),
                    new HMXType.Arrow(
                        new HMXType.LitType(TypeIdent.TYPE_IDENT_INT),
                        new HMXType.LitType(TypeIdent.TYPE_IDENT_INT)))),
            Pair.of(b, new Abs(y, new App(new Var(a), new Var(y))))),
        new App(new Var(a), new Lit(new Literal.Int(10))));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof HMXType;
    System.out.println(result);
    assert result instanceof HMXType.LitType;
    assert ((HMXType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void cyclicFunctionUse2() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    Symbol<HMXExpr, HMXType> a = Symbol.<HMXExpr, HMXType>of(new Value());
    Symbol<HMXExpr, HMXType> b = Symbol.<HMXExpr, HMXType>of(new Value());
    Symbol<HMXExpr, HMXType> x = Symbol.<HMXExpr, HMXType>of(new Value());
    Symbol<HMXExpr, HMXType> y = Symbol.<HMXExpr, HMXType>of(new Value());

    // let a : Int -> Int = \x.(b x)
    // b = \y.(a y)
    // in (b 10)

    HMXExpr expr = new LetRec(
        List.of(
            Pair.of(a,
                new Ann(
                    new Abs(x, new App(new Var(b), new Var(x))),
                    new HMXType.Arrow(
                        new HMXType.LitType(TypeIdent.TYPE_IDENT_INT),
                        new HMXType.LitType(TypeIdent.TYPE_IDENT_INT)))),
            Pair.of(b, new Abs(y, new App(new Var(a), new Var(y))))),
        new App(new Var(b), new Lit(new Literal.Int(10))));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof HMXType;
    assert result instanceof HMXType.LitType;
    assert ((HMXType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void recursiveFunctionUse() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    Symbol<HMXExpr, HMXType> f = Symbol.<HMXExpr, HMXType>of(new Value());
    Symbol<HMXExpr, HMXType> x = Symbol.<HMXExpr, HMXType>of(new Value());

    // let f : Int -> Int = \x.(f x)
    // in (f 10)
    //
    // Directly self-recursive: instantiating the application in the body of f
    // re-enters the instantiation of f itself while it is still in progress.
    HMXExpr expr = new LetRec(
        f,
        new Ann(new Abs(x, new App(new Var(f), new Var(x))),
            new HMXType.Arrow(
                new HMXType.LitType(TypeIdent.TYPE_IDENT_INT),
                new HMXType.LitType(TypeIdent.TYPE_IDENT_INT))),
        new App(new Var(f), new Lit(new Literal.Int(10))));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof HMXType;
    assert result instanceof HMXType.LitType;
    assert ((HMXType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void multiParamFunction() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    Symbol<HMXExpr, HMXType> a = Symbol.<HMXExpr, HMXType>of(new Value());
    Symbol<HMXExpr, HMXType> x = Symbol.<HMXExpr, HMXType>of(new Value());
    Symbol<HMXExpr, HMXType> y = Symbol.<HMXExpr, HMXType>of(new Value());

    // let a = \(x,y).(x, y)
    // in (a 10 false)

    HMXExpr expr = new LetRec(a,
        new Abs(List.of(x, y),
            new Tuple(List.of(new Var(x), new Var(y)))),
        new App(new Var(a),
            List.of(new Lit(new Literal.Int(10)), new Lit(new Literal.Bool(false)))));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof HMXType;
    assert result instanceof HMXType.Tuple;
    var resultTuple = (HMXType.Tuple) result;
    assert resultTuple.elements.get(0) instanceof HMXType.LitType;
    assert resultTuple.elements.get(1) instanceof HMXType.LitType;

    assert ((HMXType.LitType) resultTuple.elements.get(0)).tyName == TypeIdent.TYPE_IDENT_INT;
    assert ((HMXType.LitType) resultTuple.elements.get(1)).tyName == TypeIdent.TYPE_IDENT_BOOL;
  }

  @Test
  public void letPolymorphism() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    var cnst = Symbol.<HMXExpr, HMXType>of(new Value());
    var x = Symbol.<HMXExpr, HMXType>of(new Value());
    var y = Symbol.<HMXExpr, HMXType>of(new Value());

    // let const = \x -> \y -> x in (const 42 true, const true 42, const 32 false)
    HMXExpr expr = new LetRec(
        cnst,
        new Abs(x, new Abs(y, new Var(x))),
        new Tuple(new App(
            new App(
                new Var(cnst),
                new Lit(new Literal.Int(42))),
            new Lit(new Literal.Bool(true))),
            new App(
                new App(
                    new Var(cnst),
                    new Lit(new Literal.Bool(false))),
                new Lit(new Literal.Int(24))),
            new App(
                new App(
                    new Var(cnst),
                    new Lit(new Literal.Int(32))),
                new Lit(new Literal.Bool(false)))));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type();
    var inferred = resultPair.instantiated();

    assert result instanceof HMXType;
    assert result instanceof HMXType.Tuple;

    assert inferred instanceof LetRec;
    var exprLet = (LetRec) inferred;

    assert exprLet.body() instanceof Tuple;
    var exprTuple = (Tuple) exprLet.body();

    assert exprTuple.elements().size() == 3;
    var first = exprTuple.elements().get(0);
    var second = exprTuple.elements().get(1);
    var third = exprTuple.elements().get(2);

    Function<HMXExpr, HMXExpr> getInnerAbs = elem -> {
      assert elem instanceof App;
      var inner = ((App) elem).func();

      assert inner instanceof App;

      return ((App) inner).func();
    };

    // TODO: check the instantiation and if replacing Let and Abs bindings actually
    // work. Otherwise the rebinding of let values must be deferered to later
    // stages! One problem arises with the hash-consing of expressions
    var firstAbs = getInnerAbs.apply(first);
    var secondAbs = getInnerAbs.apply(second);
    var thirdAbs = getInnerAbs.apply(third);

    // By reference, check if hash consing worked
    assert firstAbs == thirdAbs;
    assert firstAbs != secondAbs;
    assert thirdAbs != secondAbs;
  }
}

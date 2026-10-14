import dgir.core.ir.Dialect;
import dgir.core.ir.Value;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.builtin.algorithmw.expr.Abs;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.expr.Ann;
import dgir.core.ir.types.builtin.algorithmw.expr.App;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.expr.LetRec;
import dgir.core.ir.types.builtin.algorithmw.expr.Lit;
import dgir.core.ir.types.builtin.algorithmw.expr.Tuple;
import dgir.core.ir.types.builtin.algorithmw.expr.Var;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AlgorithmWTest {

  @BeforeEach
  public void setup() {
    Dialect.registerAllDialects();
  }

  @Test
  public void algorithmWSimpleTest() {
    var inference = new AlgorithmWInference();
    var solver = inference.getNewSolverInstance();

    var x = Symbol.<Expr, AlgorithmWType>of(new Value());

    // let const = \x -> x in const 42
    Expr expr = new App(new Abs(x, new Var(x)), new Lit(new Literal.Int(42)));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();

    assert result instanceof AlgorithmWType;
    assert result instanceof AlgorithmWType.LitType;
    assert ((AlgorithmWType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void algorithmWTest() {
    var inference = new AlgorithmWInference();
    var solver = inference.getNewSolverInstance();

    var cnst = Symbol.<Expr, AlgorithmWType>of(new Value());
    var x = Symbol.<Expr, AlgorithmWType>of(new Value());
    var y = Symbol.<Expr, AlgorithmWType>of(new Value());

    // let const = \x -> \y -> x in const 42 true
    Expr expr = new LetRec(
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

    assert result instanceof AlgorithmWType;
    assert result instanceof AlgorithmWType.LitType;
    assert ((AlgorithmWType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void annotationTest() {
    var inference = new AlgorithmWInference();
    var solver = inference.getNewSolverInstance();

    var cnst = Symbol.<Expr, AlgorithmWType>of(new Value());
    var x = Symbol.<Expr, AlgorithmWType>of(new Value());
    var y = Symbol.<Expr, AlgorithmWType>of(new Value());

    // let const = \x -> \y -> x in const 42 true
    Expr expr = new Ann(
        new LetRec(
            cnst,
            new Abs(x, new Abs(y, new Var(x))),
            new App(
                new App(
                    new Var(cnst),
                    new Lit(new Literal.Int(42))),
                new Lit(new Literal.Bool(true)))),
        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof AlgorithmWType;
    assert result instanceof AlgorithmWType.LitType;
    assert ((AlgorithmWType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  /**
   * Annotation tests
   */
  @Test
  public void annotation2Test() {
    var inference = new AlgorithmWInference();
    var solver = inference.getNewSolverInstance();

    var cnst = Symbol.<Expr, AlgorithmWType>of(new Value());
    var x = Symbol.<Expr, AlgorithmWType>of(new Value());
    var y = Symbol.<Expr, AlgorithmWType>of(new Value());

    // let const = \x -> \y -> x in const 42 true
    Expr expr = new Ann(
        new LetRec(
            cnst,
            new Ann(
                new Abs(x, new Abs(y, new Var(x))),
                new AlgorithmWType.Arrow(
                    new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT),
                    new AlgorithmWType.Arrow(
                        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_BOOL),
                        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT)))),
            new App(
                new App(
                    new Var(cnst),
                    new Ann(
                        new Lit(new Literal.Int(42)),
                        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT))),
                new Ann(
                    new Lit(new Literal.Bool(true)),
                    new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_BOOL)))),
        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT)

    );

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof AlgorithmWType;
    assert result instanceof AlgorithmWType.LitType;
    assert ((AlgorithmWType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void cyclicFunctionUse() {
    var inference = new AlgorithmWInference();
    var solver = inference.getNewSolverInstance();

    Symbol<Expr, AlgorithmWType> a = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> b = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> x = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> y = Symbol.<Expr, AlgorithmWType>of(new Value());

    // let a : Int -> Int = \x.(b x)
    // b = \y.(a y)
    // in (a 10)

    Expr expr = new LetRec(
        List.of(
            Pair.of(a,
                new Ann(new Abs(x, new App(new Var(b), new Var(x))),
                    new AlgorithmWType.Arrow(
                        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT),
                        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT)))),
            Pair.of(b, new Abs(y, new App(new Var(a), new Var(y))))),
        new App(new Var(a), new Lit(new Literal.Int(10))));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof AlgorithmWType;
    System.out.println(result);
    assert result instanceof AlgorithmWType.LitType;
    assert ((AlgorithmWType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void cyclicFunctionUse2() {
    var inference = new AlgorithmWInference();
    var solver = inference.getNewSolverInstance();

    Symbol<Expr, AlgorithmWType> a = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> b = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> x = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> y = Symbol.<Expr, AlgorithmWType>of(new Value());

    // let a : Int -> Int = \x.(b x)
    // b = \y.(a y)
    // in (b 10)

    Expr expr = new LetRec(
        List.of(
            Pair.of(a,
                new Ann(new Abs(x, new App(new Var(b), new Var(x))),
                    new AlgorithmWType.Arrow(
                        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT),
                        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT)))),
            Pair.of(b, new Abs(y, new App(new Var(a), new Var(y))))),
        new App(new Var(b), new Lit(new Literal.Int(10))));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof AlgorithmWType;
    assert result instanceof AlgorithmWType.LitType;
    assert ((AlgorithmWType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void recursiveFunctionUse() {
    var inference = new AlgorithmWInference();
    var solver = inference.getNewSolverInstance();

    Symbol<Expr, AlgorithmWType> f = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> x = Symbol.<Expr, AlgorithmWType>of(new Value());

    // let f : Int -> Int = \x.(f x)
    // in (f 10)
    //
    // Directly self-recursive: instantiating the application in the body of f
    // re-enters the instantiation of f itself while it is still in progress.
    Expr expr = new LetRec(
        f,
        new Ann(new Abs(x, new App(new Var(f), new Var(x))),
            new AlgorithmWType.Arrow(
                new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT),
                new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT))),
        new App(new Var(f), new Lit(new Literal.Int(10))));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof AlgorithmWType;
    assert result instanceof AlgorithmWType.LitType;
    assert ((AlgorithmWType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void multiParamFunction() {
    var inference = new AlgorithmWInference();
    var solver = inference.getNewSolverInstance();

    Symbol<Expr, AlgorithmWType> a = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> x = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> y = Symbol.<Expr, AlgorithmWType>of(new Value());

    // let a = \(x,y).(x, y)
    // in (a 10 false)

    Expr expr = new LetRec(a,
        new Abs(List.of(x, y), new Tuple(List.of(new Var(x), new Var(y)))),
        new App(new Var(a),
            List.of(new Lit(new Literal.Int(10)), new Lit(new Literal.Bool(false)))));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof AlgorithmWType;
    assert result instanceof AlgorithmWType.Tuple;
    var resultTuple = (AlgorithmWType.Tuple) result;
    assert resultTuple.elements.get(0) instanceof AlgorithmWType.LitType;
    assert resultTuple.elements.get(1) instanceof AlgorithmWType.LitType;

    assert ((AlgorithmWType.LitType) resultTuple.elements.get(0)).tyName == TypeIdent.TYPE_IDENT_INT;
    assert ((AlgorithmWType.LitType) resultTuple.elements.get(1)).tyName == TypeIdent.TYPE_IDENT_BOOL;
  }

  @Test
  public void letPolymorphism() {
    var inference = new AlgorithmWInference();
    var solver = inference.getNewSolverInstance();

    var cnst = Symbol.<Expr, AlgorithmWType>of(new Value());
    var x = Symbol.<Expr, AlgorithmWType>of(new Value());
    var y = Symbol.<Expr, AlgorithmWType>of(new Value());

    // let const = \x -> \y -> x in (const 42 true, const true 42, const 32 false)
    Expr expr = new LetRec(
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

    assert result instanceof AlgorithmWType;
    assert result instanceof AlgorithmWType.Tuple;

    assert inferred instanceof LetRec;
    var exprLet = (LetRec) inferred;

    assert exprLet.body() instanceof Tuple;
    var exprTuple = (Tuple) exprLet.body();

    assert exprTuple.elements().size() == 3;
    var first = exprTuple.elements().get(0);
    var second = exprTuple.elements().get(1);
    var third = exprTuple.elements().get(2);

    Function<Expr, Expr> getInnerAbs = elem -> {
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

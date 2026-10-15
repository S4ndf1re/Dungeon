import dgir.core.ir.Value;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.builtin.systemf.expr.Abs;
import dgir.core.ir.types.builtin.systemf.expr.Ann;
import dgir.core.ir.types.builtin.systemf.expr.App;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.expr.Let;
import dgir.core.ir.types.builtin.systemf.expr.LitExpr;
import dgir.core.ir.types.builtin.systemf.SystemFInference;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.expr.TAbs;
import dgir.core.ir.types.builtin.systemf.expr.TApp;
import dgir.core.ir.types.builtin.systemf.expr.Tuple;
import dgir.core.ir.types.builtin.systemf.expr.Var;
import dgir.core.ir.types.TypeVar;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

public class SystemFTest {

  @Test
  public void systemFTest() {
    var inference = new SystemFInference();
    var solver = inference.getNewSolverInstance();

    var x = Symbol.<Expr, SystemFType>of(new Value());

    var expr = new Abs(
        x,
        new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
        new Var(x));

    var resTypePair = solver.solve(expr);
        DgirTestUtils.saveDotExprPreInstantiation(resTypePair.preInstantiation());
        DgirTestUtils.saveDotExprPreInstantiation(resTypePair.preInstantiation());
        DgirTestUtils.saveDotExpr(resTypePair.instantiated());
        DgirTestUtils.saveDotExprScopes(resTypePair.instantiated());
        DgirTestUtils.saveDotType(resTypePair.type());
    var resType = resTypePair.type();
    assert resType instanceof SystemFType;

    assert resType.equals(
        new SystemFType.Arrow(new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT)));
  }

  @Test
  public void systemFTest2() {
    // let add = \x -> \y -> x + y in add 1 2
    var inference = new SystemFInference();
    var solver = inference.getNewSolverInstance();

    var add = Symbol.<Expr, SystemFType>of(new Value());
    var x = Symbol.<Expr, SystemFType>of(new Value());
    var y = Symbol.<Expr, SystemFType>of(new Value());

    var expr = new Let(
        add,
        new Abs(
            x,
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
            new Abs(
                y,
                new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                new Var(x))),
        new App(
            new App(new Var(add), new LitExpr(new Literal.Int(1))),
            new LitExpr(new Literal.Int(2))));

    var resTypePair = solver.solve(expr);
        DgirTestUtils.saveDotExprPreInstantiation(resTypePair.preInstantiation());
        DgirTestUtils.saveDotExprPreInstantiation(resTypePair.preInstantiation());
        DgirTestUtils.saveDotExpr(resTypePair.instantiated());
        DgirTestUtils.saveDotExprScopes(resTypePair.instantiated());
        DgirTestUtils.saveDotType(resTypePair.type());
    var resType = resTypePair.type();
    assert resType instanceof SystemFType;

    assert resType.equals(new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT));
  }

  /**
   * Test if nominal types with generic parameters work in SystemF
   *
   */
  @Test
  public void systemFTest3() {
    // let add = \x -> List()
    var inference = new SystemFInference();
    var solver = inference.getNewSolverInstance();

    var add = Symbol.<Expr, SystemFType>of(new Value());
    var x = Symbol.<Expr, SystemFType>of(new Value());

    var expr = new Ann(new Let(
        add,
        new Abs(
            x,
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
            new LitExpr(new Literal.MyList())),
        new App(new Var(add), new LitExpr(new Literal.Int(1)))),
        new SystemFType.Lit(TypeIdent.TYPE_IDENT_LIST, List.of(new SystemFType.Lit(TypeIdent.TYPE_IDENT_BOOL))));

    var resTypePair = solver.solve(expr);
        DgirTestUtils.saveDotExprPreInstantiation(resTypePair.preInstantiation());
        DgirTestUtils.saveDotExprPreInstantiation(resTypePair.preInstantiation());
        DgirTestUtils.saveDotExpr(resTypePair.instantiated());
        DgirTestUtils.saveDotExprScopes(resTypePair.instantiated());
        DgirTestUtils.saveDotType(resTypePair.type());
    var resType = resTypePair.type();
    assert resType instanceof SystemFType;

    assert resType.equals(
        new SystemFType.Lit(TypeIdent.TYPE_IDENT_LIST, List.of(new SystemFType.Lit(TypeIdent.TYPE_IDENT_BOOL))));
  }

  @Test
  public void multiLet() {
    // let add = \x -> \y -> x + y
    // a = 1
    // b = 2
    // in add 1 2
    var inference = new SystemFInference();
    var solver = inference.getNewSolverInstance();

    var add = Symbol.<Expr, SystemFType>of(new Value());
    var a = Symbol.<Expr, SystemFType>of(new Value());
    var b = Symbol.<Expr, SystemFType>of(new Value());
    var x = Symbol.<Expr, SystemFType>of(new Value());
    var y = Symbol.<Expr, SystemFType>of(new Value());

    var expr = new Let(
        List.of(
            Pair.of(add, new Abs(
                x,
                new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                new Abs(
                    y,
                    new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                    new Var(x)))),

            Pair.of(a, new LitExpr(new Literal.Int(10))),
            Pair.of(b, new LitExpr(new Literal.Int(20)))),
        new App(
            new App(new Var(add), new Var(a)),
            new Var(b)));

    var resTypePair = solver.solve(expr);
        DgirTestUtils.saveDotExprPreInstantiation(resTypePair.preInstantiation());
        DgirTestUtils.saveDotExprPreInstantiation(resTypePair.preInstantiation());
        DgirTestUtils.saveDotExpr(resTypePair.instantiated());
        DgirTestUtils.saveDotExprScopes(resTypePair.instantiated());
        DgirTestUtils.saveDotType(resTypePair.type());
    var resType = resTypePair.type();
    assert resType instanceof SystemFType;

    assert resType.equals(new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT));
  }

  @Test
  public void cyclicFunctionUse() {
    var inference = new SystemFInference();
    var solver = inference.getNewSolverInstance();

    Symbol<Expr, SystemFType> a = Symbol.<Expr, SystemFType>of(new Value());
    Symbol<Expr, SystemFType> b = Symbol.<Expr, SystemFType>of(new Value());
    Symbol<Expr, SystemFType> x = Symbol.<Expr, SystemFType>of(new Value());
    Symbol<Expr, SystemFType> y = Symbol.<Expr, SystemFType>of(new Value());

    // let a : Int -> Int = \x.(b x)
    // b = \y.(a y)
    // in (a 10)

    Expr expr = new Let(
        List.of(
            Pair.of(a,
                new Ann(
                    new Abs(x, new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                        new App(new Var(b), new Var(x))),
                    new SystemFType.Arrow(
                        new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                        new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT)))),
            Pair.of(b,
                new Abs(y, new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                    new App(new Var(a), new Var(y))))),
        new App(new Var(a), new LitExpr(new Literal.Int(10))));

    var resTypePair = solver.solve(expr);
        DgirTestUtils.saveDotExprPreInstantiation(resTypePair.preInstantiation());
        DgirTestUtils.saveDotExprPreInstantiation(resTypePair.preInstantiation());
        DgirTestUtils.saveDotExpr(resTypePair.instantiated());
        DgirTestUtils.saveDotExprScopes(resTypePair.instantiated());
        DgirTestUtils.saveDotType(resTypePair.type());
    var resType = resTypePair.type();
    assert resType instanceof SystemFType;
    assert resType instanceof SystemFType.Lit;
    assert ((SystemFType.Lit) resType).ident.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void cyclicFunctionUse2() {
    var inference = new SystemFInference();
    var solver = inference.getNewSolverInstance();

    Symbol<Expr, SystemFType> a = Symbol.<Expr, SystemFType>of(new Value());
    Symbol<Expr, SystemFType> b = Symbol.<Expr, SystemFType>of(new Value());
    Symbol<Expr, SystemFType> x = Symbol.<Expr, SystemFType>of(new Value());
    Symbol<Expr, SystemFType> y = Symbol.<Expr, SystemFType>of(new Value());

    // let a : Int -> Int = \x.(b x)
    // b = \y.(a y)
    // in (a 10)

    Expr expr = new Let(
        List.of(
            Pair.of(a,
                new Ann(
                    new Abs(x, new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                        new App(new Var(b), new Var(x))),
                    new SystemFType.Arrow(
                        new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                        new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT)))),
            Pair.of(b,
                new Abs(y, new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                    new App(new Var(a), new Var(y))))),
        new App(new Var(b), new LitExpr(new Literal.Int(10))));

    var resTypePair = solver.solve(expr);
        DgirTestUtils.saveDotExprPreInstantiation(resTypePair.preInstantiation());
        DgirTestUtils.saveDotExprPreInstantiation(resTypePair.preInstantiation());
        DgirTestUtils.saveDotExpr(resTypePair.instantiated());
        DgirTestUtils.saveDotExprScopes(resTypePair.instantiated());
        DgirTestUtils.saveDotType(resTypePair.type());
    var resType = resTypePair.type();
    assert resType instanceof SystemFType;
    assert resType instanceof SystemFType.Lit;
    assert ((SystemFType.Lit) resType).ident.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void polymorphicConst() {
    var inference = new SystemFInference();
    var solver = inference.getNewSolverInstance();

    var a = new TypeVar<SystemFType>();
    var b = new TypeVar<SystemFType>();
    var x = Symbol.<Expr, SystemFType>of(new Value());
    var y = Symbol.<Expr, SystemFType>of(new Value());

    var cnst = new TAbs(a,
        new TAbs(b,
            new Abs(x, new SystemFType.Var(a),
                new Abs(y, new SystemFType.Var(b), new Var(x)))));

    var expr = new App(
        new App(
            new TApp(
                new TApp(cnst,
                    new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT)),
                new SystemFType.Lit(TypeIdent.TYPE_IDENT_BOOL)),
            new LitExpr(new Literal.Int(42))),
        new LitExpr(new Literal.Bool(true)));

    var resultPair = solver.solve(expr);
        DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
        DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
        DgirTestUtils.saveDotExpr(resultPair.instantiated());
        DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
        DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type();

    assert result.equals(new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT));
  }

  @Test
  public void polymorphicUses() {
    var inference = new SystemFInference();
    var solver = inference.getNewSolverInstance();

    var a = new TypeVar<SystemFType>();
    var b = new TypeVar<SystemFType>();
    var x = Symbol.<Expr, SystemFType>of(new Value());
    var y = Symbol.<Expr, SystemFType>of(new Value());

    var t = new TAbs(a,
        new TAbs(b,
            new Abs(x, new SystemFType.Var(a),
                new Abs(y, new SystemFType.Var(b), new Var(x)))));

    var intTy = new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT);
    var boolTy = new SystemFType.Lit(TypeIdent.TYPE_IDENT_BOOL);

    var use1 = new App(
        new App(new TApp(new TApp(t, intTy), boolTy),
            new LitExpr(new Literal.Int(42))),
        new LitExpr(new Literal.Bool(true)));
    var use2 = new App(
        new App(new TApp(new TApp(t, boolTy), intTy),
            new LitExpr(new Literal.Bool(true))),
        new LitExpr(new Literal.Int(42)));
    var use3 = new App(
        new App(new TApp(new TApp(t, intTy), boolTy),
            new LitExpr(new Literal.Int(32))),
        new LitExpr(new Literal.Bool(false)));

    var expr = new Tuple(use1, use2, use3);

    var resultPair = solver.solve(expr);
        DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
        DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
        DgirTestUtils.saveDotExpr(resultPair.instantiated());
        DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
        DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type();

    assert result instanceof SystemFType.Tuple;
    var resultTuple = (SystemFType.Tuple) result;
    assert resultTuple.elements.size() == 3;
    assert resultTuple.elements.get(0).equals(intTy);
    assert resultTuple.elements.get(1).equals(boolTy);
    assert resultTuple.elements.get(2).equals(intTy);

    var instantiated = resultPair.instantiated();
    assert instantiated instanceof Tuple;
    var tupleExpr = (Tuple) instantiated;
    assert tupleExpr.elements().size() == 3;

    Function<Expr, Expr> getInnerAbs = elem -> {
      assert elem instanceof App;
      var inner = ((App) elem).fun();
      assert inner instanceof App;
      return ((App) inner).fun();
    };

    var firstAbs = getInnerAbs.apply(tupleExpr.elements().get(0));
    var secondAbs = getInnerAbs.apply(tupleExpr.elements().get(1));
    var thirdAbs = getInnerAbs.apply(tupleExpr.elements().get(2));

    // By reference, check if hash consing worked
    assert firstAbs == thirdAbs;
    assert firstAbs != secondAbs;
    assert thirdAbs != secondAbs;
  }
}

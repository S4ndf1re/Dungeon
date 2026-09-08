import dgir.core.ir.Value;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.systemf.Expr;
import dgir.core.ir.types.systemf.SystemFInference;
import dgir.core.ir.types.systemf.SystemFType;

import dgir.core.ir.types.TypeVar;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

public class SystemFTest {

  @Test
  public void systemFTest() {
    var inference = new SystemFInference();
    var solver = inference.getSolverInstance();

    var x = Symbol.<Expr, SystemFType>of(new Value());

    var expr = new Expr.Abs(
        x,
        new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
        new Expr.Var(x));

    var resTypePair = solver.solve(expr);
        DgirTestUtils.saveDotExpr(resTypePair.getRight());
        DgirTestUtils.saveDotType(resTypePair.getLeft());
    var resType = resTypePair.getLeft();
    assert resType instanceof SystemFType;

    assert resType.equals(
        new SystemFType.Arrow(new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT)));
  }

  @Test
  public void systemFTest2() {
    // let add = \x -> \y -> x + y in add 1 2
    var inference = new SystemFInference();
    var solver = inference.getSolverInstance();

    var add = Symbol.<Expr, SystemFType>of(new Value());
    var x = Symbol.<Expr, SystemFType>of(new Value());
    var y = Symbol.<Expr, SystemFType>of(new Value());

    var expr = new Expr.Let(
        add,
        new Expr.Abs(
            x,
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
            new Expr.Abs(
                y,
                new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                new Expr.Var(x))),
        new Expr.App(
            new Expr.App(new Expr.Var(add), new Expr.LitExpr(new Literal.Int(1))),
            new Expr.LitExpr(new Literal.Int(2))));

    var resTypePair = solver.solve(expr);
        DgirTestUtils.saveDotExpr(resTypePair.getRight());
        DgirTestUtils.saveDotType(resTypePair.getLeft());
    var resType = resTypePair.getLeft();
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
    var solver = inference.getSolverInstance();

    var add = Symbol.<Expr, SystemFType>of(new Value());
    var x = Symbol.<Expr, SystemFType>of(new Value());

    var expr = new Expr.Ann(new Expr.Let(
        add,
        new Expr.Abs(
            x,
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
            new Expr.LitExpr(new Literal.MyList())),
        new Expr.App(new Expr.Var(add), new Expr.LitExpr(new Literal.Int(1)))),
        new SystemFType.Lit(TypeIdent.TYPE_IDENT_LIST, List.of(new SystemFType.Lit(TypeIdent.TYPE_IDENT_BOOL))));

    var resTypePair = solver.solve(expr);
        DgirTestUtils.saveDotExpr(resTypePair.getRight());
        DgirTestUtils.saveDotType(resTypePair.getLeft());
    var resType = resTypePair.getLeft();
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
    var solver = inference.getSolverInstance();

    var add = Symbol.<Expr, SystemFType>of(new Value());
    var a = Symbol.<Expr, SystemFType>of(new Value());
    var b = Symbol.<Expr, SystemFType>of(new Value());
    var x = Symbol.<Expr, SystemFType>of(new Value());
    var y = Symbol.<Expr, SystemFType>of(new Value());

    var expr = new Expr.Let(
        List.of(
            Pair.of(add, new Expr.Abs(
                x,
                new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                new Expr.Abs(
                    y,
                    new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                    new Expr.Var(x)))),

            Pair.of(a, new Expr.LitExpr(new Literal.Int(10))),
            Pair.of(b, new Expr.LitExpr(new Literal.Int(20)))),
        new Expr.App(
            new Expr.App(new Expr.Var(add), new Expr.Var(a)),
            new Expr.Var(b)));

    var resTypePair = solver.solve(expr);
        DgirTestUtils.saveDotExpr(resTypePair.getRight());
        DgirTestUtils.saveDotType(resTypePair.getLeft());
    var resType = resTypePair.getLeft();
    assert resType instanceof SystemFType;

    assert resType.equals(new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT));
  }

  @Test
  public void cyclicFunctionUse() {
    var inference = new SystemFInference();
    var solver = inference.getSolverInstance();

    Symbol<Expr, SystemFType> a = Symbol.<Expr, SystemFType>of(new Value());
    Symbol<Expr, SystemFType> b = Symbol.<Expr, SystemFType>of(new Value());
    Symbol<Expr, SystemFType> x = Symbol.<Expr, SystemFType>of(new Value());
    Symbol<Expr, SystemFType> y = Symbol.<Expr, SystemFType>of(new Value());

    // let a : Int -> Int = \x.(b x)
    // b = \y.(a y)
    // in (a 10)

    Expr expr = new Expr.Let(
        List.of(
            Pair.of(a,
                new Expr.Ann(
                    new Expr.Abs(x, new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                        new Expr.App(new Expr.Var(b), new Expr.Var(x))),
                    new SystemFType.Arrow(
                        new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                        new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT)))),
            Pair.of(b,
                new Expr.Abs(y, new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                    new Expr.App(new Expr.Var(a), new Expr.Var(y))))),
        new Expr.App(new Expr.Var(a), new Expr.LitExpr(new Literal.Int(10))));

    var resTypePair = solver.solve(expr);
        DgirTestUtils.saveDotExpr(resTypePair.getRight());
        DgirTestUtils.saveDotType(resTypePair.getLeft());
    var resType = resTypePair.getLeft();
    assert resType instanceof SystemFType;
    assert resType instanceof SystemFType.Lit;
    assert ((SystemFType.Lit) resType).ident.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void cyclicFunctionUse2() {
    var inference = new SystemFInference();
    var solver = inference.getSolverInstance();

    Symbol<Expr, SystemFType> a = Symbol.<Expr, SystemFType>of(new Value());
    Symbol<Expr, SystemFType> b = Symbol.<Expr, SystemFType>of(new Value());
    Symbol<Expr, SystemFType> x = Symbol.<Expr, SystemFType>of(new Value());
    Symbol<Expr, SystemFType> y = Symbol.<Expr, SystemFType>of(new Value());

    // let a : Int -> Int = \x.(b x)
    // b = \y.(a y)
    // in (a 10)

    Expr expr = new Expr.Let(
        List.of(
            Pair.of(a,
                new Expr.Ann(
                    new Expr.Abs(x, new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                        new Expr.App(new Expr.Var(b), new Expr.Var(x))),
                    new SystemFType.Arrow(
                        new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                        new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT)))),
            Pair.of(b,
                new Expr.Abs(y, new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                    new Expr.App(new Expr.Var(a), new Expr.Var(y))))),
        new Expr.App(new Expr.Var(b), new Expr.LitExpr(new Literal.Int(10))));

    var resTypePair = solver.solve(expr);
        DgirTestUtils.saveDotExpr(resTypePair.getRight());
        DgirTestUtils.saveDotType(resTypePair.getLeft());
    var resType = resTypePair.getLeft();
    assert resType instanceof SystemFType;
    assert resType instanceof SystemFType.Lit;
    assert ((SystemFType.Lit) resType).ident.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void polymorphicConst() {
    var inference = new SystemFInference();
    var solver = inference.getSolverInstance();

    var a = new TypeVar();
    var b = new TypeVar();
    var x = Symbol.<Expr, SystemFType>of(new Value());
    var y = Symbol.<Expr, SystemFType>of(new Value());

    var cnst = new Expr.TAbs(a,
        new Expr.TAbs(b,
            new Expr.Abs(x, new SystemFType.Var(a),
                new Expr.Abs(y, new SystemFType.Var(b), new Expr.Var(x)))));

    var expr = new Expr.App(
        new Expr.App(
            new Expr.TApp(
                new Expr.TApp(cnst,
                    new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT)),
                new SystemFType.Lit(TypeIdent.TYPE_IDENT_BOOL)),
            new Expr.LitExpr(new Literal.Int(42))),
        new Expr.LitExpr(new Literal.Bool(true)));

    var resultPair = solver.solve(expr);
        DgirTestUtils.saveDotExpr(resultPair.getRight());
        DgirTestUtils.saveDotType(resultPair.getLeft());
    var result = resultPair.getLeft();

    assert result.equals(new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT));
  }

  @Test
  public void polymorphicUses() {
    var inference = new SystemFInference();
    var solver = inference.getSolverInstance();

    var a = new TypeVar();
    var b = new TypeVar();
    var x = Symbol.<Expr, SystemFType>of(new Value());
    var y = Symbol.<Expr, SystemFType>of(new Value());

    var t = new Expr.TAbs(a,
        new Expr.TAbs(b,
            new Expr.Abs(x, new SystemFType.Var(a),
                new Expr.Abs(y, new SystemFType.Var(b), new Expr.Var(x)))));

    var intTy = new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT);
    var boolTy = new SystemFType.Lit(TypeIdent.TYPE_IDENT_BOOL);

    var use1 = new Expr.App(
        new Expr.App(new Expr.TApp(new Expr.TApp(t, intTy), boolTy),
            new Expr.LitExpr(new Literal.Int(42))),
        new Expr.LitExpr(new Literal.Bool(true)));
    var use2 = new Expr.App(
        new Expr.App(new Expr.TApp(new Expr.TApp(t, boolTy), intTy),
            new Expr.LitExpr(new Literal.Bool(true))),
        new Expr.LitExpr(new Literal.Int(42)));
    var use3 = new Expr.App(
        new Expr.App(new Expr.TApp(new Expr.TApp(t, intTy), boolTy),
            new Expr.LitExpr(new Literal.Int(32))),
        new Expr.LitExpr(new Literal.Bool(false)));

    var expr = new Expr.Tuple(use1, use2, use3);

    var resultPair = solver.solve(expr);
        DgirTestUtils.saveDotExpr(resultPair.getRight());
        DgirTestUtils.saveDotType(resultPair.getLeft());
    var result = resultPair.getLeft();

    assert result instanceof SystemFType.Tuple;
    var resultTuple = (SystemFType.Tuple) result;
    assert resultTuple.elements.size() == 3;
    assert resultTuple.elements.get(0).equals(intTy);
    assert resultTuple.elements.get(1).equals(boolTy);
    assert resultTuple.elements.get(2).equals(intTy);

    var instantiated = resultPair.getRight();
    assert instantiated instanceof Expr.Tuple;
    var tupleExpr = (Expr.Tuple) instantiated;
    assert tupleExpr.elements().size() == 3;

    Function<Expr, Expr> getInnerAbs = elem -> {
      assert elem instanceof Expr.App;
      var inner = ((Expr.App) elem).fun();
      assert inner instanceof Expr.App;
      return ((Expr.App) inner).fun();
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

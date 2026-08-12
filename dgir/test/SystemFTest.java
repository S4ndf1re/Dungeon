import dgir.core.ir.Value;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.systemf.SystemFInference;
import dgir.core.ir.types.systemf.SystemFInference.Expr;
import dgir.core.ir.types.systemf.SystemFInference.SystemFType.Lit;
import dgir.core.ir.types.systemf.SystemFInference.SystemFType;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

public class SystemFTest {

  @Test
  public void systemFTest() {
    var inference = new SystemFInference();
    var solver = inference.getSolverInstance();

    var x = Symbol.of(new Value());

    var expr = new Expr.Abs(
        x,
        new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
        new Expr.Var(x));

    var resType = solver.solve(expr);
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

    var add = Symbol.of(new Value());
    var x = Symbol.of(new Value());
    var y = Symbol.of(new Value());

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

    var resType = solver.solve(expr);
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

    var add = Symbol.of(new Value());
    var x = Symbol.of(new Value());

    var expr = new Expr.Ann(new Expr.Let(
        add,
        new Expr.Abs(
            x,
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
            new Expr.LitExpr(new Literal.MyList())),
        new Expr.App(new Expr.Var(add), new Expr.LitExpr(new Literal.Int(1)))),
        new SystemFType.Lit(TypeIdent.TYPE_IDENT_LIST, List.of(new SystemFType.Lit(TypeIdent.TYPE_IDENT_BOOL))));

    var resType = solver.solve(expr);
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

    var add = Symbol.of(new Value());
    var a = Symbol.of(new Value());
    var b = Symbol.of(new Value());
    var x = Symbol.of(new Value());
    var y = Symbol.of(new Value());

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

    var resType = solver.solve(expr);
    assert resType instanceof SystemFType;

    assert resType.equals(new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT));
  }

  @Test
  public void cyclicFunctionUse() {
    var inference = new SystemFInference();
    var solver = inference.getSolverInstance();

    Symbol a = Symbol.of(new Value());
    Symbol b = Symbol.of(new Value());
    Symbol x = Symbol.of(new Value());
    Symbol y = Symbol.of(new Value());

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

    var result = solver.solve(expr);
    assert result instanceof SystemFType;
    assert result instanceof SystemFType.Lit;
    assert ((Lit) result).ident.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void cyclicFunctionUse2() {
    var inference = new SystemFInference();
    var solver = inference.getSolverInstance();

    Symbol a = Symbol.of(new Value());
    Symbol b = Symbol.of(new Value());
    Symbol x = Symbol.of(new Value());
    Symbol y = Symbol.of(new Value());

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

    var result = solver.solve(expr);
    assert result instanceof SystemFType;
    assert result instanceof SystemFType.Lit;
    assert ((Lit) result).ident.equals(TypeIdent.TYPE_IDENT_INT);
  }
}

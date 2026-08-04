import dgir.core.ir.Value;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.systemf.SystemFInference;
import dgir.core.ir.types.systemf.SystemFInference.Expr;
import dgir.core.ir.types.systemf.SystemFInference.Expr.BinOp.BinOpKind;
import dgir.core.ir.types.systemf.SystemFInference.SystemFType;

import java.util.List;

import org.junit.jupiter.api.Test;

public class SystemFTest {

  @Test
  public void systemFTest() {
    var inference = new SystemFInference();
    var solver = inference.getSolverInstance();

    var x = new Value();

    var expr = new Expr.Abs(
        x,
        new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
        new Expr.BinOp(BinOpKind.ADD, new Expr.Var(x), new Expr.Var(x)));

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

    var add = new Value();
    var x = new Value();
    var y = new Value();

    var expr = new Expr.Let(
        add,
        new Expr.Abs(
            x,
            new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
            new Expr.Abs(
                y,
                new SystemFType.Lit(TypeIdent.TYPE_IDENT_INT),
                new Expr.BinOp(BinOpKind.ADD, new Expr.Var(x), new Expr.Var(y)))),
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

    var add = new Value();
    var x = new Value();

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
}

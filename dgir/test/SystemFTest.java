import dgir.core.ir.types.systemf.SystemFInference;
import dgir.core.ir.types.systemf.SystemFInference.Expr;
import dgir.core.ir.types.systemf.SystemFInference.Expr.BinOp.BinOpKind;
import dgir.core.ir.types.systemf.SystemFInference.Expr.Lit;
import dgir.core.ir.types.systemf.SystemFInference.SystemFType;
import org.junit.jupiter.api.Test;

public class SystemFTest {

  @Test
  public void systemFTest() {
    var inference = new SystemFInference();
    var solver = inference.getSolverInstance();

    var expr = new Expr.Abs(
        "x",
        new SystemFType.Int(),
        new Expr.BinOp(BinOpKind.ADD, new Expr.Var("x"), new Expr.Var("x")));

    var resType = solver.solve(expr);
    assert resType instanceof SystemFType;

    assert resType.equals(
        new SystemFType.Arrow(new SystemFType.Int(), new SystemFType.Int()));
  }

  @Test
  public void systemFTest2() {
    // let add = \x -> \y -> x + y in add 1 2
    var inference = new SystemFInference();
    var solver = inference.getSolverInstance();

    var expr = new Expr.Let(
        "add",
        new Expr.Abs(
            "x",
            new SystemFType.Int(),
            new Expr.Abs(
                "y",
                new SystemFType.Int(),
                new Expr.BinOp(BinOpKind.ADD, new Expr.Var("x"), new Expr.Var("y")))),
        new Expr.App(
            new Expr.App(new Expr.Var("add"), new Expr.LitExpr(new Lit.Int(1))),
            new Expr.LitExpr(new Lit.Int(2))));

    var resType = solver.solve(expr);
    assert resType instanceof SystemFType;

    assert resType.equals(new SystemFType.Int());
  }
}

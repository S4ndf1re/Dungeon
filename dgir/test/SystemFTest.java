import dgir.core.ir.types.SystemFInference;
import dgir.core.ir.types.SystemFInference.Expr;
import dgir.core.ir.types.SystemFInference.Expr.BinOp.BinOpKind;
import dgir.core.ir.types.SystemFInference.Expr.Lit;
import dgir.core.ir.types.SystemFInference.SystemFType;
import org.junit.jupiter.api.Test;

public class SystemFTest {

  @Test
  public void systemFTest() {
    var inference = new SystemFInference.TypeInference();
    var expr = new Expr.Abs(
      "x",
      new SystemFType.Int(),
      new Expr.BinOp(BinOpKind.ADD, new Expr.Var("x"), new Expr.Var("x"))
    );

    var resType = inference.inferType(expr);

    assert resType.equals(
      new SystemFType.Arrow(new SystemFType.Int(), new SystemFType.Int())
    );
  }

  @Test
  public void systemFTest2() {
    // let add = \x -> \y -> x + y in add 1 2
    var inference = new SystemFInference.TypeInference();
    var expr = new Expr.Let(
      "add",
      new Expr.Abs(
        "x",
        new SystemFType.Int(),
        new Expr.Abs(
          "y",
          new SystemFType.Int(),
          new Expr.BinOp(BinOpKind.ADD, new Expr.Var("x"), new Expr.Var("y"))
        )
      ),
      new Expr.App(
        new Expr.App(new Expr.Var("add"), new Expr.LitExpr(new Lit.Int(1))),
        new Expr.LitExpr(new Lit.Int(2))
      )
    );

    var resType = inference.inferType(expr);
    assert resType.equals(new SystemFType.Int());
  }
}

import dgir.core.ir.types.AlgorithmWInference.Expr;
import dgir.core.ir.types.AlgorithmWInference.Expr.Lit;
import dgir.core.ir.types.AlgorithmWInference.TypeInference;
import dgir.core.ir.types.AlgorithmWInference.AlgorithmWType.Integer;

import org.junit.jupiter.api.Test;

public class AlgorithmWTest {

  @Test
  public void algorithmWTest() {
    var inference = new TypeInference();

    // let const = \x -> \y -> x in const 42 true
    Expr expr = new Expr.ExprLet(
      "const",
      new Expr.ExprAbs("x", new Expr.ExprAbs("y", new Expr.ExprVar("x"))),
      new Expr.ExprApp(
        new Expr.ExprApp(
          new Expr.ExprVar("const"),
          new Expr.ExprLit(new Lit.LitInt(42))
        ),
        new Expr.ExprLit(new Lit.LitBool(true))
      )
    );

    var result = inference.inferType(expr);
    System.out.println(result + "");
    assert result instanceof Integer;
  }
}

import dgir.core.ir.types.AlgorithmWInference;
import dgir.core.ir.types.AlgorithmWInference.AlgorithmWType;
import dgir.core.ir.types.AlgorithmWInference.AlgorithmWType.Integer;
import dgir.core.ir.types.AlgorithmWInference.Expr;
import dgir.core.ir.types.AlgorithmWInference.Expr.Lit;
import java.util.List;
import org.junit.jupiter.api.Test;

public class AlgorithmWTest {

  @Test
  public void algorithmWDialectTest() {
    AlgorithmWInference inference = new AlgorithmWInference();
    List<Class<? extends dgir.core.ir.types.Type>> allowedTypes =
      inference.getAllowedTypes();
    assert allowedTypes.contains(
      AlgorithmWInference.AlgorithmWType.Integer.class
    );
    assert allowedTypes.contains(
      AlgorithmWInference.AlgorithmWType.Boolean.class
    );
    assert allowedTypes.contains(
      AlgorithmWInference.AlgorithmWType.Arrow.class
    );
    assert allowedTypes.contains(AlgorithmWInference.AlgorithmWType.Var.class);

    List<Class<? extends dgir.core.ir.types.Expression>> allowedExpression =
      inference.getAllowedExpressions();
    assert allowedExpression.contains(AlgorithmWInference.Expr.ExprLit.class);
    assert allowedExpression.contains(AlgorithmWInference.Expr.ExprAbs.class);
    assert allowedExpression.contains(AlgorithmWInference.Expr.ExprApp.class);
    assert allowedExpression.contains(AlgorithmWInference.Expr.ExprTuple.class);
    assert allowedExpression.contains(AlgorithmWInference.Expr.ExprLet.class);
    assert allowedExpression.contains(AlgorithmWInference.Expr.ExprVar.class);

    var solver = inference.getSolverInstance();
    assert solver != null;
    assert solver.getClass().equals(AlgorithmWInference.TypeInference.class);
  }

  @Test
  public void algorithmWTest() {
    var inference = new AlgorithmWInference();
    var solver = inference.getSolverInstance();

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

    var result = solver.solve(expr);
    assert result instanceof AlgorithmWType;
    assert result instanceof Integer;
  }
}

import dgir.core.ir.types.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.AlgorithmWType;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.AlgorithmWType.LitType;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.Expr;
import java.util.List;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.Expr.Lit;
import org.junit.jupiter.api.Test;

public class AlgorithmWTest {

  @Test
  public void algorithmWDialectTest() {
    AlgorithmWInference inference = new AlgorithmWInference();
    List<Class<? extends dgir.core.ir.types.Type>> allowedTypes = inference.getAllowedTypes();
    assert allowedTypes.contains(
        AlgorithmWInference.AlgorithmWType.LitType.class);
    assert allowedTypes.contains(
        AlgorithmWInference.AlgorithmWType.Arrow.class);
    assert allowedTypes.contains(AlgorithmWInference.AlgorithmWType.Var.class);

    List<Class<? extends dgir.core.ir.types.Expression>> allowedExpression = inference.getAllowedExpressions();
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
                new Expr.ExprLit(new Lit.LitInt(42))),
            new Expr.ExprLit(new Lit.LitBool(true))));

    var result = solver.solve(expr);
    assert result instanceof AlgorithmWType;
    assert result instanceof LitType;
    assert ((LitType) result).tyName.equals("Int");
  }

  @Test
  public void annotationTest() {
    var inference = new AlgorithmWInference();
    var solver = inference.getSolverInstance();

    // let const = \x -> \y -> x in const 42 true
    Expr expr = new Expr.ExprAnn(
        new Expr.ExprLet(
            "const",
            new Expr.ExprAbs("x", new Expr.ExprAbs("y", new Expr.ExprVar("x"))),
            new Expr.ExprApp(
                new Expr.ExprApp(
                    new Expr.ExprVar("const"),
                    new Expr.ExprLit(new Lit.LitInt(42))),
                new Expr.ExprLit(new Lit.LitBool(true)))),
        new AlgorithmWType.LitType("Int"));

    var result = solver.solve(expr);
    assert result instanceof AlgorithmWType;
    assert result instanceof LitType;
    assert ((LitType) result).tyName.equals("Int");
  }

  /**
   * Annotation tests
   */
  @Test
  public void annotation2Test() {
    var inference = new AlgorithmWInference();
    var solver = inference.getSolverInstance();

    // let const = \x -> \y -> x in const 42 true
    Expr expr = new Expr.ExprAnn(
        new Expr.ExprLet(
            "const",
            new Expr.ExprAnn(
                new Expr.ExprAbs("x", new Expr.ExprAbs("y", new Expr.ExprVar("x"))),
                new AlgorithmWType.Arrow(
                    new AlgorithmWType.LitType("Int"),
                    new AlgorithmWType.Arrow(
                        new AlgorithmWType.LitType("Bool"),
                        new AlgorithmWType.LitType("Int")))),
            new Expr.ExprApp(
                new Expr.ExprApp(
                    new Expr.ExprVar("const"),
                    new Expr.ExprAnn(
                        new Expr.ExprLit(new Lit.LitInt(42)),
                        new AlgorithmWType.LitType("Int"))),
                new Expr.ExprAnn(
                    new Expr.ExprLit(new Lit.LitBool(true)),
                    new AlgorithmWType.LitType("Bool")))),
        new AlgorithmWType.LitType("Int")

    );

    var result = solver.solve(expr);
    assert result instanceof AlgorithmWType;
    assert result instanceof AlgorithmWType.LitType;
    assert ((LitType) result).tyName.equals("Int");
  }
}

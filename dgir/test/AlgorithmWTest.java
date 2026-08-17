import dgir.core.ir.Value;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.AlgorithmWType;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.AlgorithmWType.LitType;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.Expr;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

public class AlgorithmWTest {

  @Test
  public void algorithmWDialectTest() {
    AlgorithmWInference inference = new AlgorithmWInference();
    List<Class<? extends dgir.core.ir.types.Type>> allowedTypes = inference.getAllowedTypes();
    assert allowedTypes.contains(AlgorithmWInference.AlgorithmWType.LitType.class);
    assert allowedTypes.contains(AlgorithmWInference.AlgorithmWType.Arrow.class);
    assert allowedTypes.contains(AlgorithmWInference.AlgorithmWType.Var.class);
    assert allowedTypes.contains(AlgorithmWInference.AlgorithmWType.NumericType.class);
    assert allowedTypes.contains(AlgorithmWInference.AlgorithmWType.Tuple.class);

    List<Class<? extends dgir.core.ir.types.Expression<AlgorithmWType>>> allowedExpression = inference
        .getAllowedExpressions();
    assert allowedExpression.contains(AlgorithmWInference.Expr.ExprLit.class);
    assert allowedExpression.contains(AlgorithmWInference.Expr.ExprAbs.class);
    assert allowedExpression.contains(AlgorithmWInference.Expr.ExprApp.class);
    assert allowedExpression.contains(AlgorithmWInference.Expr.ExprAnn.class);
    assert allowedExpression.contains(AlgorithmWInference.Expr.ExprTuple.class);
    assert allowedExpression.contains(AlgorithmWInference.Expr.ExprLet.class);
    assert allowedExpression.contains(AlgorithmWInference.Expr.ExprVar.class);
    assert allowedExpression.contains(AlgorithmWInference.Expr.ExprReturn.class);
    assert allowedExpression.contains(AlgorithmWInference.Expr.ExprCustom.class);

    var solver = inference.getSolverInstance();
    assert solver != null;
    assert solver.getClass().equals(AlgorithmWInference.TypeInference.class);
  }

  @Test
  public void algorithmWTest() {
    var inference = new AlgorithmWInference();
    var solver = inference.getSolverInstance();

    var cnst = Symbol.<Expr, AlgorithmWType>of(new Value());
    var x = Symbol.<Expr, AlgorithmWType>of(new Value());
    var y = Symbol.<Expr, AlgorithmWType>of(new Value());

    // let const = \x -> \y -> x in (const 42 true, const true 42)
    Expr expr = new Expr.ExprLet(
        cnst,
        new Expr.ExprAbs(x, new Expr.ExprAbs(y, new Expr.ExprVar(x))),
        new Expr.ExprApp(
            new Expr.ExprApp(
                new Expr.ExprVar(cnst),
                new Expr.ExprLit(new Literal.Int(42))),
            new Expr.ExprLit(new Literal.Bool(true))));

    var resultPair = solver.solve(expr);
    var result = resultPair.getLeft();

    assert result instanceof AlgorithmWType;
    assert result instanceof LitType;
    assert ((LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void annotationTest() {
    var inference = new AlgorithmWInference();
    var solver = inference.getSolverInstance();

    var cnst = Symbol.<Expr, AlgorithmWType>of(new Value());
    var x = Symbol.<Expr, AlgorithmWType>of(new Value());
    var y = Symbol.<Expr, AlgorithmWType>of(new Value());

    // let const = \x -> \y -> x in const 42 true
    Expr expr = new Expr.ExprAnn(
        new Expr.ExprLet(
            cnst,
            new Expr.ExprAbs(x, new Expr.ExprAbs(y, new Expr.ExprVar(x))),
            new Expr.ExprApp(
                new Expr.ExprApp(
                    new Expr.ExprVar(cnst),
                    new Expr.ExprLit(new Literal.Int(42))),
                new Expr.ExprLit(new Literal.Bool(true)))),
        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT));

    var resultPair = solver.solve(expr);
    var result = resultPair.getLeft();
    assert result instanceof AlgorithmWType;
    assert result instanceof LitType;
    assert ((LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  /**
   * Annotation tests
   */
  @Test
  public void annotation2Test() {
    var inference = new AlgorithmWInference();
    var solver = inference.getSolverInstance();

    var cnst = Symbol.<Expr, AlgorithmWType>of(new Value());
    var x = Symbol.<Expr, AlgorithmWType>of(new Value());
    var y = Symbol.<Expr, AlgorithmWType>of(new Value());

    // let const = \x -> \y -> x in const 42 true
    Expr expr = new Expr.ExprAnn(
        new Expr.ExprLet(
            cnst,
            new Expr.ExprAnn(
                new Expr.ExprAbs(x, new Expr.ExprAbs(y, new Expr.ExprVar(x))),
                new AlgorithmWType.Arrow(
                    new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT),
                    new AlgorithmWType.Arrow(
                        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_BOOL),
                        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT)))),
            new Expr.ExprApp(
                new Expr.ExprApp(
                    new Expr.ExprVar(cnst),
                    new Expr.ExprAnn(
                        new Expr.ExprLit(new Literal.Int(42)),
                        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT))),
                new Expr.ExprAnn(
                    new Expr.ExprLit(new Literal.Bool(true)),
                    new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_BOOL)))),
        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT)

    );

    var resultPair = solver.solve(expr);
    var result = resultPair.getLeft();
    assert result instanceof AlgorithmWType;
    assert result instanceof AlgorithmWType.LitType;
    assert ((LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void cyclicFunctionUse() {
    var inference = new AlgorithmWInference();
    var solver = inference.getSolverInstance();

    Symbol<Expr, AlgorithmWType> a = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> b = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> x = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> y = Symbol.<Expr, AlgorithmWType>of(new Value());

    // let a : Int -> Int = \x.(b x)
    // b = \y.(a y)
    // in (a 10)

    Expr expr = new Expr.ExprLet(
        List.of(
            Pair.of(a,
                new Expr.ExprAnn(new Expr.ExprAbs(x, new Expr.ExprApp(new Expr.ExprVar(b), new Expr.ExprVar(x))),
                    new AlgorithmWType.Arrow(
                        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT),
                        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT)))),
            Pair.of(b, new Expr.ExprAbs(y, new Expr.ExprApp(new Expr.ExprVar(a), new Expr.ExprVar(y))))),
        new Expr.ExprApp(new Expr.ExprVar(a), new Expr.ExprLit(new Literal.Int(10))));

    var resultPair = solver.solve(expr);
    var result = resultPair.getLeft();
    assert result instanceof AlgorithmWType;
    System.out.println(result);
    assert result instanceof AlgorithmWType.LitType;
    assert ((LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void cyclicFunctionUse2() {
    var inference = new AlgorithmWInference();
    var solver = inference.getSolverInstance();

    Symbol<Expr, AlgorithmWType> a = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> b = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> x = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> y = Symbol.<Expr, AlgorithmWType>of(new Value());

    // let a : Int -> Int = \x.(b x)
    // b = \y.(a y)
    // in (b 10)

    Expr expr = new Expr.ExprLet(
        List.of(
            Pair.of(a,
                new Expr.ExprAnn(new Expr.ExprAbs(x, new Expr.ExprApp(new Expr.ExprVar(b), new Expr.ExprVar(x))),
                    new AlgorithmWType.Arrow(
                        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT),
                        new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_INT)))),
            Pair.of(b, new Expr.ExprAbs(y, new Expr.ExprApp(new Expr.ExprVar(a), new Expr.ExprVar(y))))),
        new Expr.ExprApp(new Expr.ExprVar(b), new Expr.ExprLit(new Literal.Int(10))));

    var resultPair = solver.solve(expr);
    var result = resultPair.getLeft();
    assert result instanceof AlgorithmWType;
    assert result instanceof AlgorithmWType.LitType;
    assert ((LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void multiParamFunction() {
    var inference = new AlgorithmWInference();
    var solver = inference.getSolverInstance();

    Symbol<Expr, AlgorithmWType> a = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> x = Symbol.<Expr, AlgorithmWType>of(new Value());
    Symbol<Expr, AlgorithmWType> y = Symbol.<Expr, AlgorithmWType>of(new Value());

    // let a = \(x,y).(x, y)
    // in (a 10 false)

    Expr expr = new Expr.ExprLet(a,
        new Expr.ExprAbs(List.of(x, y), new Expr.ExprTuple(List.of(new Expr.ExprVar(x), new Expr.ExprVar(y)))),
        new Expr.ExprApp(new Expr.ExprVar(a),
            List.of(new Expr.ExprLit(new Literal.Int(10)), new Expr.ExprLit(new Literal.Bool(false)))));

    var resultPair = solver.solve(expr);
    var result = resultPair.getLeft();
    assert result instanceof AlgorithmWType;
    System.out.println(result);
    assert result instanceof AlgorithmWType.Tuple;
    var resultTuple = (AlgorithmWType.Tuple) result;
    assert resultTuple.elements.get(0) instanceof AlgorithmWType.LitType;
    assert resultTuple.elements.get(1) instanceof AlgorithmWType.LitType;

    assert ((AlgorithmWType.LitType) resultTuple.elements.get(0)).tyName == TypeIdent.TYPE_IDENT_INT;
    assert ((AlgorithmWType.LitType) resultTuple.elements.get(1)).tyName == TypeIdent.TYPE_IDENT_BOOL;
  }
}

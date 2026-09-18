import dgir.core.debug.Location;
import dgir.core.debug.ValueDebugInfo;
import dgir.core.ir.Dialect;
import dgir.core.ir.Value;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.TypeInference;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HMXTest {

  @BeforeEach
  public void setup() {
    Dialect.registerAllDialects();
  }

  @Test
  public void algorithmWDialectTest() {
    HMXInference inference = new HMXInference();
    List<Class<? extends dgir.core.ir.types.Type<?>>> allowedTypes = inference.getAllowedTypes();
    assert allowedTypes.contains(HMXType.LitType.class);
    assert allowedTypes.contains(HMXType.Arrow.class);
    assert allowedTypes.contains(HMXType.Var.class);
    assert allowedTypes.contains(HMXType.NumericType.class);
    assert allowedTypes.contains(HMXType.Tuple.class);

    List<Class<? extends dgir.core.ir.types.Expression<HMXExpr, HMXType>>> allowedExpression = inference
        .getAllowedExpressions();
    assert allowedExpression.contains(HMXExpr.ExprLit.class);
    assert allowedExpression.contains(HMXExpr.ExprAbs.class);
    assert allowedExpression.contains(HMXExpr.ExprApp.class);
    assert allowedExpression.contains(HMXExpr.ExprAnn.class);
    assert allowedExpression.contains(HMXExpr.ExprTuple.class);
    assert allowedExpression.contains(HMXExpr.ExprLetRec.class);
    assert allowedExpression.contains(HMXExpr.ExprVar.class);
    assert allowedExpression.contains(HMXExpr.ExprReturn.class);
    assert allowedExpression.contains(HMXExpr.ExprCustom.class);

    var solver = inference.getNewSolverInstance();
    assert solver != null;
    assert solver.getClass().equals(TypeInference.class);
  }

  @Test
  public void algorithmWSimpleTest() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    var x = Symbol.<HMXExpr, HMXType>of(new Value());

    // let const = \x -> x in const 42
    HMXExpr expr = new HMXExpr.ExprApp(new HMXExpr.ExprAbs(x, new HMXExpr.ExprVar(x)),
        new HMXExpr.ExprLit(new Literal.Int(42)));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();

    assert result instanceof HMXType;
    assert result instanceof HMXType.LitType;
    assert ((HMXType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void algorithmWTest() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    var cnst = Symbol.<HMXExpr, HMXType>of(new Value());
    var x = Symbol.<HMXExpr, HMXType>of(new Value());
    var y = Symbol.<HMXExpr, HMXType>of(new Value());

    // let const = \x -> \y -> x in const 42 true
    HMXExpr expr = new HMXExpr.ExprLetRec(
        cnst,
        new HMXExpr.ExprAbs(x, new HMXExpr.ExprAbs(y, new HMXExpr.ExprVar(x))),
        new HMXExpr.ExprApp(
            new HMXExpr.ExprApp(
                new HMXExpr.ExprVar(cnst),
                new HMXExpr.ExprLit(new Literal.Int(42))),
            new HMXExpr.ExprLit(new Literal.Bool(true))));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();

    assert result instanceof HMXType;
    assert result instanceof HMXType.LitType;
    assert ((HMXType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void annotationTest() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    var cnst = Symbol.<HMXExpr, HMXType>of(new Value());
    var x = Symbol.<HMXExpr, HMXType>of(new Value());
    var y = Symbol.<HMXExpr, HMXType>of(new Value());

    // let const = \x -> \y -> x in const 42 true
    HMXExpr expr = new HMXExpr.ExprAnn(
        new HMXExpr.ExprLetRec(
            cnst,
            new HMXExpr.ExprAbs(x, new HMXExpr.ExprAbs(y, new HMXExpr.ExprVar(x))),
            new HMXExpr.ExprApp(
                new HMXExpr.ExprApp(
                    new HMXExpr.ExprVar(cnst),
                    new HMXExpr.ExprLit(new Literal.Int(42))),
                new HMXExpr.ExprLit(new Literal.Bool(true)))),
        new HMXType.LitType(TypeIdent.TYPE_IDENT_INT));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof HMXType;
    assert result instanceof HMXType.LitType;
    assert ((HMXType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  /**
   * Annotation tests
   */
  @Test
  public void annotation2Test() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    var cnst = Symbol.<HMXExpr, HMXType>of(new Value());
    var x = Symbol.<HMXExpr, HMXType>of(new Value());
    var y = Symbol.<HMXExpr, HMXType>of(new Value());

    // let const = \x -> \y -> x in const 42 true
    HMXExpr expr = new HMXExpr.ExprAnn(
        new HMXExpr.ExprLetRec(
            cnst,
            new HMXExpr.ExprAnn(
                new HMXExpr.ExprAbs(x, new HMXExpr.ExprAbs(y, new HMXExpr.ExprVar(x))),
                new HMXType.Arrow(
                    new HMXType.LitType(TypeIdent.TYPE_IDENT_INT),
                    new HMXType.Arrow(
                        new HMXType.LitType(TypeIdent.TYPE_IDENT_BOOL),
                        new HMXType.LitType(TypeIdent.TYPE_IDENT_INT)))),
            new HMXExpr.ExprApp(
                new HMXExpr.ExprApp(
                    new HMXExpr.ExprVar(cnst),
                    new HMXExpr.ExprAnn(
                        new HMXExpr.ExprLit(new Literal.Int(42)),
                        new HMXType.LitType(TypeIdent.TYPE_IDENT_INT))),
                new HMXExpr.ExprAnn(
                    new HMXExpr.ExprLit(new Literal.Bool(true)),
                    new HMXType.LitType(TypeIdent.TYPE_IDENT_BOOL)))),
        new HMXType.LitType(TypeIdent.TYPE_IDENT_INT)

    );

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof HMXType;
    assert result instanceof HMXType.LitType;
    assert ((HMXType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void cyclicFunctionUse() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    Symbol<HMXExpr, HMXType> a = Symbol.<HMXExpr, HMXType>of(new Value(new ValueDebugInfo(Location.UNKNOWN, "a")));
    Symbol<HMXExpr, HMXType> b = Symbol.<HMXExpr, HMXType>of(new Value(new ValueDebugInfo(Location.UNKNOWN, "b")));
    Symbol<HMXExpr, HMXType> x = Symbol.<HMXExpr, HMXType>of(new Value(new ValueDebugInfo(Location.UNKNOWN, "x")));
    Symbol<HMXExpr, HMXType> y = Symbol.<HMXExpr, HMXType>of(new Value(new ValueDebugInfo(Location.UNKNOWN, "y")));

    // let a : Int -> Int = \x.(b x)
    // b = \y.(a y)
    // in (a 10)

    HMXExpr expr = new HMXExpr.ExprLetRec(
        List.of(
            Pair.of(a,
                new HMXExpr.ExprAnn(
                    new HMXExpr.ExprAbs(x, new HMXExpr.ExprApp(new HMXExpr.ExprVar(b), new HMXExpr.ExprVar(x))),
                    new HMXType.Arrow(
                        new HMXType.LitType(TypeIdent.TYPE_IDENT_INT),
                        new HMXType.LitType(TypeIdent.TYPE_IDENT_INT)))),
            Pair.of(b, new HMXExpr.ExprAbs(y, new HMXExpr.ExprApp(new HMXExpr.ExprVar(a), new HMXExpr.ExprVar(y))))),
        new HMXExpr.ExprApp(new HMXExpr.ExprVar(a), new HMXExpr.ExprLit(new Literal.Int(10))));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof HMXType;
    System.out.println(result);
    assert result instanceof HMXType.LitType;
    assert ((HMXType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void cyclicFunctionUse2() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    Symbol<HMXExpr, HMXType> a = Symbol.<HMXExpr, HMXType>of(new Value());
    Symbol<HMXExpr, HMXType> b = Symbol.<HMXExpr, HMXType>of(new Value());
    Symbol<HMXExpr, HMXType> x = Symbol.<HMXExpr, HMXType>of(new Value());
    Symbol<HMXExpr, HMXType> y = Symbol.<HMXExpr, HMXType>of(new Value());

    // let a : Int -> Int = \x.(b x)
    // b = \y.(a y)
    // in (b 10)

    HMXExpr expr = new HMXExpr.ExprLetRec(
        List.of(
            Pair.of(a,
                new HMXExpr.ExprAnn(
                    new HMXExpr.ExprAbs(x, new HMXExpr.ExprApp(new HMXExpr.ExprVar(b), new HMXExpr.ExprVar(x))),
                    new HMXType.Arrow(
                        new HMXType.LitType(TypeIdent.TYPE_IDENT_INT),
                        new HMXType.LitType(TypeIdent.TYPE_IDENT_INT)))),
            Pair.of(b, new HMXExpr.ExprAbs(y, new HMXExpr.ExprApp(new HMXExpr.ExprVar(a), new HMXExpr.ExprVar(y))))),
        new HMXExpr.ExprApp(new HMXExpr.ExprVar(b), new HMXExpr.ExprLit(new Literal.Int(10))));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof HMXType;
    assert result instanceof HMXType.LitType;
    assert ((HMXType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void recursiveFunctionUse() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    Symbol<HMXExpr, HMXType> f = Symbol.<HMXExpr, HMXType>of(new Value());
    Symbol<HMXExpr, HMXType> x = Symbol.<HMXExpr, HMXType>of(new Value());

    // let f : Int -> Int = \x.(f x)
    // in (f 10)
    //
    // Directly self-recursive: instantiating the application in the body of f
    // re-enters the instantiation of f itself while it is still in progress.
    HMXExpr expr = new HMXExpr.ExprLetRec(
        f,
        new HMXExpr.ExprAnn(new HMXExpr.ExprAbs(x, new HMXExpr.ExprApp(new HMXExpr.ExprVar(f), new HMXExpr.ExprVar(x))),
            new HMXType.Arrow(
                new HMXType.LitType(TypeIdent.TYPE_IDENT_INT),
                new HMXType.LitType(TypeIdent.TYPE_IDENT_INT))),
        new HMXExpr.ExprApp(new HMXExpr.ExprVar(f), new HMXExpr.ExprLit(new Literal.Int(10))));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof HMXType;
    assert result instanceof HMXType.LitType;
    assert ((HMXType.LitType) result).tyName.equals(TypeIdent.TYPE_IDENT_INT);
  }

  @Test
  public void multiParamFunction() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    Symbol<HMXExpr, HMXType> a = Symbol.<HMXExpr, HMXType>of(new Value());
    Symbol<HMXExpr, HMXType> x = Symbol.<HMXExpr, HMXType>of(new Value());
    Symbol<HMXExpr, HMXType> y = Symbol.<HMXExpr, HMXType>of(new Value());

    // let a = \(x,y).(x, y)
    // in (a 10 false)

    HMXExpr expr = new HMXExpr.ExprLetRec(a,
        new HMXExpr.ExprAbs(List.of(x, y),
            new HMXExpr.ExprTuple(List.of(new HMXExpr.ExprVar(x), new HMXExpr.ExprVar(y)))),
        new HMXExpr.ExprApp(new HMXExpr.ExprVar(a),
            List.of(new HMXExpr.ExprLit(new Literal.Int(10)), new HMXExpr.ExprLit(new Literal.Bool(false)))));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type().deref();
    assert result instanceof HMXType;
    assert result instanceof HMXType.Tuple;
    var resultTuple = (HMXType.Tuple) result;
    assert resultTuple.elements.get(0) instanceof HMXType.LitType;
    assert resultTuple.elements.get(1) instanceof HMXType.LitType;

    assert ((HMXType.LitType) resultTuple.elements.get(0)).tyName == TypeIdent.TYPE_IDENT_INT;
    assert ((HMXType.LitType) resultTuple.elements.get(1)).tyName == TypeIdent.TYPE_IDENT_BOOL;
  }

  @Test
  public void letPolymorphism() {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();

    var cnst = Symbol.<HMXExpr, HMXType>of(new Value());
    var x = Symbol.<HMXExpr, HMXType>of(new Value());
    var y = Symbol.<HMXExpr, HMXType>of(new Value());

    // let const = \x -> \y -> x in (const 42 true, const true 42, const 32 false)
    HMXExpr expr = new HMXExpr.ExprLetRec(
        cnst,
        new HMXExpr.ExprAbs(x, new HMXExpr.ExprAbs(y, new HMXExpr.ExprVar(x))),
        new HMXExpr.ExprTuple(new HMXExpr.ExprApp(
            new HMXExpr.ExprApp(
                new HMXExpr.ExprVar(cnst),
                new HMXExpr.ExprLit(new Literal.Int(42))),
            new HMXExpr.ExprLit(new Literal.Bool(true))),
            new HMXExpr.ExprApp(
                new HMXExpr.ExprApp(
                    new HMXExpr.ExprVar(cnst),
                    new HMXExpr.ExprLit(new Literal.Bool(false))),
                new HMXExpr.ExprLit(new Literal.Int(24))),
            new HMXExpr.ExprApp(
                new HMXExpr.ExprApp(
                    new HMXExpr.ExprVar(cnst),
                    new HMXExpr.ExprLit(new Literal.Int(32))),
                new HMXExpr.ExprLit(new Literal.Bool(false)))));

    var resultPair = solver.solve(expr);
    DgirTestUtils.saveDotExprPreInstantiation(resultPair.preInstantiation());
    DgirTestUtils.saveDotExpr(resultPair.instantiated());
    DgirTestUtils.saveDotExprScopes(resultPair.instantiated());
    DgirTestUtils.saveDotType(resultPair.type());
    var result = resultPair.type();
    var inferred = resultPair.instantiated();

    assert result instanceof HMXType;
    assert result instanceof HMXType.Tuple;

    assert inferred instanceof HMXExpr.ExprLetRec;
    var exprLet = (HMXExpr.ExprLetRec) inferred;

    assert exprLet.body() instanceof HMXExpr.ExprTuple;
    var exprTuple = (HMXExpr.ExprTuple) exprLet.body();

    assert exprTuple.elements().size() == 3;
    var first = exprTuple.elements().get(0);
    var second = exprTuple.elements().get(1);
    var third = exprTuple.elements().get(2);

    Function<HMXExpr, HMXExpr> getInnerAbs = elem -> {
      assert elem instanceof HMXExpr.ExprApp;
      var inner = ((HMXExpr.ExprApp) elem).func();

      assert inner instanceof HMXExpr.ExprApp;

      return ((HMXExpr.ExprApp) inner).func();
    };

    // TODO: check the instantiation and if replacing Let and Abs bindings actually
    // work. Otherwise the rebinding of let values must be deferered to later
    // stages! One problem arises with the hash-consing of expressions
    var firstAbs = getInnerAbs.apply(first);
    var secondAbs = getInnerAbs.apply(second);
    var thirdAbs = getInnerAbs.apply(third);

    // By reference, check if hash consing worked
    assert firstAbs == thirdAbs;
    assert firstAbs != secondAbs;
    assert thirdAbs != secondAbs;
  }
}

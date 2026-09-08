import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dgir.core.ir.Dialect;
import dgir.core.ir.Value;
import dgir.core.analysis.DotExpression;
import dgir.core.analysis.DotType;
import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.algorithmw.AlgorithmWType;
import dgir.core.ir.types.algorithmw.Expr;

public class DotTests {

  @BeforeEach
  public void setup() {
    Dialect.registerAllDialects();
  }

  private Expr buildConstExample() {
    var cnst = Symbol.<Expr, AlgorithmWType>of(new Value());
    var x = Symbol.<Expr, AlgorithmWType>of(new Value());
    var y = Symbol.<Expr, AlgorithmWType>of(new Value());

    // let const = \x -> \y -> x in (const 42 true)
    return new Expr.ExprLetRec(
        cnst,
        new Expr.ExprAbs(x, new Expr.ExprAbs(y, new Expr.ExprVar(x))),
        new Expr.ExprApp(
            new Expr.ExprApp(
                new Expr.ExprVar(cnst),
                new Expr.ExprLit(new Literal.Int(42))),
            new Expr.ExprLit(new Literal.Bool(true))));
  }

  @Test
  public void expressionDotRenders() throws Exception {
    var inference = new AlgorithmWInference();
    var solver = inference.getSolverInstance();

    Expr expr = this.buildConstExample();
    solver.solve(expr);

    String dot = DotExpression.toDot(expr);
    System.out.println(dot);

    assertTrue(dot.startsWith("digraph expr {"));
    assertTrue(dot.contains("ExprLetRec"));
    assertTrue(dot.contains("ExprAbs"));
    assertTrue(dot.contains("ExprApp"));
    assertTrue(dot.contains(" -> "));

    DgirTestUtils.saveDotExpr(expr);
  }

  @Test
  public void typeDotRenders() throws Exception {
    // list<int32[5]>
    var listType = new GeneralParameterizedNominalType(TypeIdent.TYPE_IDENT_LIST, List.of(
        GeneralTypeParameter.of(new GeneralParameterizedNominalType(TypeIdent.TYPE_IDENT_INT)),
        GeneralTypeParameter.of(5)));

    String dot = DotType.toDot(listType);
    System.out.println(dot);

    assertTrue(dot.startsWith("digraph type {"));
    assertTrue(dot.contains("list"));
    assertTrue(dot.contains("int32"));
    assertTrue(dot.contains(" -> "));

    DgirTestUtils.saveDotAndPng(".type", DotType.toDot(listType));
  }

  @Test
  public void unresolvedTypeDotRendersAsLeaf() {
    var unresolved = new AlgorithmWType.Var(new TypeVar());
    String dot = DotType.toDot(unresolved);
    System.out.println(dot);

    assertTrue(dot.contains(unresolved.toString()));
    assertTrue(!dot.contains(" -> "));

    DgirTestUtils.saveDotType(unresolved);
  }
}

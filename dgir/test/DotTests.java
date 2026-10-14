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
import dgir.core.ir.types.builtin.algorithmw.expr.Abs;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.expr.App;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.expr.LetRec;
import dgir.core.ir.types.builtin.algorithmw.expr.Lit;
import dgir.core.ir.types.builtin.algorithmw.expr.Var;

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
    return new LetRec(
        cnst,
        new Abs(x, new Abs(y, new Var(x))),
        new App(
            new App(
                new Var(cnst),
                new Lit(new Literal.Int(42))),
            new Lit(new Literal.Bool(true))));
  }

  @Test
  public void expressionDotRenders() throws Exception {
    var inference = new AlgorithmWInference();
    var solver = inference.getNewSolverInstance();

    Expr expr = this.buildConstExample();
    solver.solve(expr);

    String dot = DotExpression.toDot(expr);
    System.out.println(dot);

    assertTrue(dot.startsWith("digraph expr {"));
    assertTrue(dot.contains("LetRec"));
    assertTrue(dot.contains("Abs"));
    assertTrue(dot.contains("App"));
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
    var unresolved = new AlgorithmWType.Var(new TypeVar<>());
    String dot = DotType.toDot(unresolved);
    System.out.println(dot);

    assertTrue(dot.contains(unresolved.toString()));
    assertTrue(!dot.contains(" -> "));

    DgirTestUtils.saveDotType(unresolved);
  }
}

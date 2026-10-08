import static dgir.dialect.builtin.BuiltinTypes.IntegerT;
import static dgir.dialect.cell.CellOps.CreateCellOp;
import static dgir.dialect.cell.CellOps.SetCellOp;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dgir.core.debug.Location;
import dgir.core.ir.Dialect;
import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression.ExpressionVisitor;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitGetChildrenOption;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.dialect.arith.ArithAlgoWConversion;
import dgir.dialect.arith.ArithOps.ConstantOp;
import dgir.dialect.builtin.BuiltinAlgoWConversion;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.cell.CellAlgoWConversion;
import dgir.dialect.func.FuncAlgoWConversion;
import dgir.dialect.func.FuncOps.FuncOp;
import dgir.dialect.func.FuncOps.ReturnOp;

public class CellAlgoWConversionTest {
  static final Location LOC = Location.UNKNOWN;

  @BeforeEach
  public void setup() {
    ConverterRegistry.registerDialect(AlgorithmWInference.class);
    Dialect.registerAllDialects();
    FuncAlgoWConversion.registerBuiltinAlgoWConversion();
    BuiltinAlgoWConversion.registerBuiltinAlgoWConversion();
    ArithAlgoWConversion.registerBuiltinAlgoWConversion();
    CellAlgoWConversion.registerBuiltinAlgoWConversion();
  }

  private static Pair<AlgorithmWType, List<Operation>> solve(ProgramOp programOp) {
    var inference = new AlgorithmWInference();
    var solver = inference.getNewSolverInstance();
    var solvedPair = solver.solve(ExprOrOperator.of(programOp.getOperation()));
    DgirTestUtils.saveDotExprPreInstantiation(solvedPair.preInstantiation());
    DgirTestUtils.saveInferenceCfg("", programOp.getOperation(), solvedPair.instantiated());
    DgirTestUtils.saveDotExpr(solvedPair.instantiated());
    DgirTestUtils.saveDotExprScopes(solvedPair.instantiated());
    DgirTestUtils.saveDotType(solvedPair.type());

    List<Operation> ops = new ArrayList<>();
    new ExpressionVisitor<Expr, AlgorithmWType>(VisitOrder.POST_ORDER, VisitGetChildrenOption.ALL_CHILDREN)
        .visit(solvedPair.instantiated(), e -> e.getUnderlyingOperation().ifPresent(ops::add));

    return Pair.of(solvedPair.type(), ops);
  }

  private static long countOps(List<Operation> ops, Class<? extends dgir.core.ir.Op> clazz) {
    return ops.stream().filter(op -> clazz.isInstance(op.asOp())).count();
  }

  @Test
  public void createCellFromOperandInfersOperandType() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var text = funcOp.addOperation(new ConstantOp(LOC, "cell"), 0);
    var cell = funcOp.addOperation(new CreateCellOp(LOC, text.getResult()), 0);

    funcOp.addOperation(new ReturnOp(LOC, cell.getResult()), 0);

    var solved = solve(programOp);

    assertEquals(1, countOps(solved.getRight(), CreateCellOp.class), "rebuilt create-cell op missing from result tree");

    assertTrue(solved.getLeft() instanceof AlgorithmWType.LitType);
    assertEquals(TypeIdent.from("string"), ((AlgorithmWType.LitType) solved.getLeft()).tyName);
  }

  @Test
  public void declaredCellWithSetCellSurvivesRebuild() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var cell = funcOp.addOperation(new CreateCellOp(LOC, IntegerT.INT64()), 0);
    var value = funcOp.addOperation(new ConstantOp(LOC, 42L), 0);
    funcOp.addOperation(new SetCellOp(LOC, cell.getResult(), value.getResult()), 0);

    funcOp.addOperation(new ReturnOp(LOC, value.getResult()), 0);

    var solved = solve(programOp);

    assertEquals(1, countOps(solved.getRight(), CreateCellOp.class), "rebuilt create-cell op missing from result tree");
    assertEquals(1, countOps(solved.getRight(), SetCellOp.class), "rebuilt set-cell op missing from result tree");

    assertTrue(solved.getLeft() instanceof AlgorithmWType.LitType);
    assertEquals(TypeIdent.from("int64"), ((AlgorithmWType.LitType) solved.getLeft()).tyName);
  }
}

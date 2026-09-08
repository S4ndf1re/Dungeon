import static dgir.dialect.io.IoOps.PrintOp;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dgir.core.ir.Dialect;
import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression.ExpressionVisitor;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitGetChildrenOption;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.algorithmw.AlgorithmWType;
import dgir.core.ir.types.algorithmw.Expr;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.debug.Location;
import dgir.dialect.arith.ArithAlgoWConversion;
import dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;
import dgir.dialect.arith.ArithOps.BinaryOp;
import dgir.dialect.arith.ArithOps.ConstantOp;
import dgir.dialect.builtin.BuiltinAlgoWConversion;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.func.FuncAlgoWConversion;
import dgir.dialect.func.FuncOps.FuncOp;
import dgir.dialect.func.FuncOps.ReturnOp;
import dgir.dialect.scf.ScfAlgoWConversion;
import dgir.dialect.scf.ScfOps.ContinueOp;
import dgir.dialect.scf.ScfOps.EndOp;
import dgir.dialect.scf.ScfOps.ForOp;
import dgir.dialect.scf.ScfOps.IfOp;
import dgir.dialect.scf.ScfOps.SelectOp;
import dgir.dialect.scf.ScfOps.WhileOp;
import dgir.dialect.scf.ScfOps.YieldOp;

public class ScfInferenceTest {
  static final Location LOC = Location.UNKNOWN;

  @BeforeEach
  public void setup() {
    ConverterRegistry.registerDialect(AlgorithmWInference.class);
    Dialect.registerAllDialects();
    FuncAlgoWConversion.registerBuiltinAlgoWConversion();
    BuiltinAlgoWConversion.registerBuiltinAlgoWConversion();
    ArithAlgoWConversion.registerBuiltinAlgoWConversion();
    dgir.dialect.io.IoAlgoWConversion.registerBuiltinAlgoWConversion();
    ScfAlgoWConversion.registerBuiltinAlgoWConversion();
  }

  private static Pair<Type, List<Operation>> solve(ProgramOp programOp) {
    var inference = new AlgorithmWInference();
    var solver = inference.getSolverInstance();
    var solvedPair = solver.solve(ExprOrOperator.of(programOp.getOperation()));
        DgirTestUtils.saveInferenceCfg("", programOp.getOperation(), solvedPair.getRight());
        DgirTestUtils.saveDotExpr(solvedPair.getRight());
        DgirTestUtils.saveDotType(solvedPair.getLeft());

    // Collect every operation that made it into the reconstructed tree.
    List<Operation> ops = new ArrayList<>();
    new ExpressionVisitor<Expr, AlgorithmWType>(VisitOrder.POST_ORDER, VisitGetChildrenOption.ALL_CHILDREN)
        .visit(solvedPair.getRight(), e -> e.getUnderlyingOperation().ifPresent(ops::add));

    return Pair.of(solvedPair.getLeft(), ops);
  }
  private static long countOps(List<Operation> ops, Class<? extends dgir.core.ir.Op> clazz) {
    return ops.stream().filter(op -> clazz.isInstance(op.asOp())).count();
  }

  @Test
  public void ifWithResultInfersInt() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var condOp = funcOp.addOperation(new ConstantOp(LOC, true), 0);
    IfOp ifOp = funcOp.addOperation(
        new IfOp(LOC, condOp.getResult(), true, dgir.dialect.builtin.BuiltinTypes.IntegerT.INT32()), 0);

    var thenConst = ifOp.getThenRegion().getEntryBlock().addOperation(new ConstantOp(LOC, 42));
    ifOp.getThenRegion().getEntryBlock().addOperation(new YieldOp(LOC, thenConst.getResult()));

    var elseConst = ifOp.getElseRegion().orElseThrow().getEntryBlock().addOperation(new ConstantOp(LOC, 43));
    ifOp.getElseRegion().orElseThrow().getEntryBlock().addOperation(new YieldOp(LOC, elseConst.getResult()));

    funcOp.addOperation(new ReturnOp(LOC, ifOp.getOperation().getOutputValueOrThrow()), 0);

    var solved = solve(programOp);

    // The reconstructed tree must contain the rebuilt if together with its yields.
    assertEquals(1, countOps(solved.getRight(), IfOp.class), "rebuilt if op missing from result tree");
    assertEquals(2, countOps(solved.getRight(), YieldOp.class), "rebuilt yields missing from result tree");

    assertTrue(solved.getLeft() instanceof AlgorithmWType.LitType);
    assertEquals(TypeIdent.from("int32"), ((AlgorithmWType.LitType) solved.getLeft()).tyName);
  }

  @Test
  public void ifWithoutResultInfersUnit() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var condOp = funcOp.addOperation(new ConstantOp(LOC, true), 0);
    IfOp ifOp = funcOp.addOperation(new IfOp(LOC, condOp.getResult(), false), 0);

    var thenConst = ifOp.getThenRegion().getEntryBlock().addOperation(new ConstantOp(LOC, 42));
    ifOp.getThenRegion().getEntryBlock().addOperation(new PrintOp(LOC, thenConst.getResult()));
    ifOp.getThenRegion().getEntryBlock().addOperation(new EndOp(LOC));

    funcOp.addOperation(new ReturnOp(LOC), 0);

    var solved = solve(programOp);

    // The if and the print inside its then region must both survive the rebuild.
    assertEquals(1, countOps(solved.getRight(), IfOp.class), "rebuilt if op missing from result tree");
    assertEquals(1, countOps(solved.getRight(), PrintOp.class), "rebuilt print missing from result tree");

    assertTrue(solved.getLeft() instanceof AlgorithmWType.LitType);
    assertEquals(TypeIdent.from("unit"), ((AlgorithmWType.LitType) solved.getLeft()).tyName);
  }

  @Test
  public void selectInfersOperandType() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var condOp = funcOp.addOperation(new ConstantOp(LOC, true), 0);
    var trueOp = funcOp.addOperation(new ConstantOp(LOC, 1), 0);
    var falseOp = funcOp.addOperation(new ConstantOp(LOC, 2), 0);
    SelectOp selectOp = funcOp.addOperation(
        new SelectOp(LOC, condOp.getResult(), trueOp.getResult(), falseOp.getResult()), 0);

    funcOp.addOperation(new ReturnOp(LOC, selectOp.getResult()), 0);

    var solved = solve(programOp);

    assertEquals(1, countOps(solved.getRight(), SelectOp.class), "rebuilt select op missing from result tree");

    assertTrue(solved.getLeft() instanceof AlgorithmWType.LitType);
    assertEquals(TypeIdent.from("int32"), ((AlgorithmWType.LitType) solved.getLeft()).tyName);
  }

  @Test
  public void simpleForLoopInfers() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var initValue = funcOp.addOperation(new ConstantOp(LOC, 0), 0);
    var lowerBound = funcOp.addOperation(new ConstantOp(LOC, 0), 0);
    var upperBound = funcOp.addOperation(new ConstantOp(LOC, 10), 0);
    var step = funcOp.addOperation(new ConstantOp(LOC, 1), 0);

    ForOp forOp = funcOp.addOperation(
        new ForOp(LOC, initValue.getResult(), lowerBound.getResult(), upperBound.getResult(), step.getResult()), 0);

    forOp.getRegion().getEntryBlock().addOperation(new ContinueOp(LOC));

    funcOp.addOperation(new ReturnOp(LOC), 0);

    var solved = solve(programOp);

    // The for loop must be part of the reconstructed function body.
    assertEquals(1, countOps(solved.getRight(), ForOp.class), "rebuilt for op missing from result tree");

    assertTrue(solved.getLeft() instanceof AlgorithmWType.LitType);
    assertEquals(TypeIdent.TYPE_IDENT_UNIT, ((AlgorithmWType.LitType) solved.getLeft()).tyName);
  }

  @Test
  public void forLoopUsingInductionValueInfers() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var initValue = funcOp.addOperation(new ConstantOp(LOC, 0), 0);
    var lowerBound = funcOp.addOperation(new ConstantOp(LOC, 0), 0);
    var upperBound = funcOp.addOperation(new ConstantOp(LOC, 10), 0);
    var step = funcOp.addOperation(new ConstantOp(LOC, 1), 0);

    ForOp forOp = funcOp.addOperation(
        new ForOp(LOC, initValue.getResult(), lowerBound.getResult(), upperBound.getResult(), step.getResult()), 0);

    // Use the induction variable inside the body: this exercises the re-pointing
    // of region-value uses to the freshly instantiated loop.
    forOp.getRegion().getEntryBlock().addOperation(new PrintOp(LOC, forOp.getInductionValue()));
    forOp.getRegion().getEntryBlock().addOperation(new ContinueOp(LOC));

    funcOp.addOperation(new ReturnOp(LOC), 0);

    var solved = solve(programOp);

    assertEquals(1, countOps(solved.getRight(), ForOp.class), "rebuilt for op missing from result tree");

    // The rebuilt body must reference the new induction region value, not the
    // original one.
    var rebuiltFor = solved.getRight().stream().map(Operation::asOp)
        .filter(o -> o instanceof ForOp).map(o -> (ForOp) o).findFirst().orElseThrow();
    var newInduction = rebuiltFor.getInductionValue();
    var print = rebuiltFor.getRegion().getEntryBlock().getOperations().getFirst();
    assertEquals(newInduction, print.getOperandValue(0).orElseThrow());

    assertEquals(TypeIdent.TYPE_IDENT_UNIT, ((AlgorithmWType.LitType) solved.getLeft()).tyName);
  }

  @Test
  public void sequentialForLoopsSurvive() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var initValue = funcOp.addOperation(new ConstantOp(LOC, 0), 0);
    var lowerBound = funcOp.addOperation(new ConstantOp(LOC, 0), 0);
    var upperBound = funcOp.addOperation(new ConstantOp(LOC, 10), 0);
    var step = funcOp.addOperation(new ConstantOp(LOC, 1), 0);

    ForOp for1 = funcOp.addOperation(
        new ForOp(LOC, initValue.getResult(), lowerBound.getResult(), upperBound.getResult(), step.getResult()), 0);
    for1.getRegion().getEntryBlock().addOperation(new ContinueOp(LOC));

    ForOp for2 = funcOp.addOperation(
        new ForOp(LOC, initValue.getResult(), lowerBound.getResult(), upperBound.getResult(), step.getResult()), 0);
    for2.getRegion().getEntryBlock().addOperation(new ContinueOp(LOC));

    funcOp.addOperation(new ReturnOp(LOC), 0);

    var solved = solve(programOp);

    // Neither loop is referenced by a later value; both must still be placed
    // back into the reconstructed tree.
    assertEquals(2, countOps(solved.getRight(), ForOp.class),
        "both sequential for loops must be present in the result tree");

    assertEquals(TypeIdent.TYPE_IDENT_UNIT, ((AlgorithmWType.LitType) solved.getLeft()).tyName);
  }

  @Test
  public void simpleWhileLoopInfers() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    WhileOp whileOp = funcOp.addOperation(new WhileOp(LOC), 0);

    var counter = whileOp.getConditionRegion().getEntryBlock().addOperation(new ConstantOp(LOC, 5));
    var zero = whileOp.getConditionRegion().getEntryBlock().addOperation(new ConstantOp(LOC, 0));
    whileOp.getConditionRegion().getEntryBlock()
        .addOperation(new BinaryOp(LOC, counter.getResult(), zero.getResult(), BinMode.GT));
    whileOp.getConditionRegion().getEntryBlock().addOperation(new ContinueOp(LOC));

    whileOp.getBodyRegion().getEntryBlock().addOperation(new ContinueOp(LOC));

    funcOp.addOperation(new ReturnOp(LOC), 0);

    var solved = solve(programOp);

    // The while loop and its condition must be part of the reconstructed tree.
    assertEquals(1, countOps(solved.getRight(), WhileOp.class), "rebuilt while op missing from result tree");
    assertEquals(1, countOps(solved.getRight(), BinaryOp.class), "rebuilt condition missing from result tree");

    assertEquals(TypeIdent.TYPE_IDENT_UNIT, ((AlgorithmWType.LitType) solved.getLeft()).tyName);
  }
}

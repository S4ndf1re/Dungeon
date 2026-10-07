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
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.debug.Location;
import dgir.dialect.arith.ArithHMXConversion;
import dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;
import dgir.dialect.arith.ArithOps.BinaryOp;
import dgir.dialect.arith.ArithOps.ConstantOp;
import dgir.dialect.builtin.BuiltinHMXConversion;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.func.FuncHMXConversion;
import dgir.dialect.func.FuncOps.FuncOp;
import dgir.dialect.func.FuncOps.ReturnOp;
import dgir.dialect.func.FuncOps.CallOp;
import dgir.dialect.func.FuncTypes.FuncType;
import dgir.dialect.scf.ScfHMXConversion;
import dgir.dialect.scf.ScfOps.ContinueOp;
import dgir.dialect.scf.ScfOps.EndOp;
import dgir.dialect.scf.ScfOps.ForOp;
import dgir.dialect.scf.ScfOps.IfOp;
import dgir.dialect.scf.ScfOps.SelectOp;
import dgir.dialect.scf.ScfOps.WhileOp;
import dgir.dialect.scf.ScfOps.YieldOp;

public class ScfHMXInferenceTest {
  static final Location LOC = Location.UNKNOWN;

  @BeforeEach
  public void setup() {
    ConverterRegistry.registerDialect(HMXInference.class);
    Dialect.registerAllDialects();
    FuncHMXConversion.registerBuiltinAlgoWConversion();
    BuiltinHMXConversion.registerBuiltinAlgoWConversion();
    ArithHMXConversion.registerBuiltinAlgoWConversion();
    dgir.dialect.io.IoHMXConversion.registerBuiltinAlgoWConversion();
    ScfHMXConversion.registerBuiltinAlgoWConversion();
  }

  private static Pair<Type<HMXType>, List<Operation>> solve(ProgramOp programOp) {
    var inference = new HMXInference();
    var solver = inference.getNewSolverInstance();
    var solvedPair = solver.solve(ExprOrOperator.of(programOp.getOperation()));
        DgirTestUtils.saveDotExprPreInstantiation(solvedPair.preInstantiation());
        DgirTestUtils.saveInferenceCfg("", programOp.getOperation(), solvedPair.instantiated());
        DgirTestUtils.saveDotExpr(solvedPair.instantiated());
        DgirTestUtils.saveDotExprScopes(solvedPair.instantiated());
        DgirTestUtils.saveDotType(solvedPair.type());

    // Collect every operation that made it into the reconstructed tree.
    List<Operation> ops = new ArrayList<>();
    new ExpressionVisitor<HMXExpr, HMXType>(VisitOrder.POST_ORDER, VisitGetChildrenOption.ALL_CHILDREN)
        .visit(solvedPair.instantiated(), e -> e.getUnderlyingOperation().ifPresent(ops::add));

    return Pair.of(solvedPair.type(), ops);
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

    assertTrue(solved.getLeft() instanceof HMXType.LitType);
    assertEquals(TypeIdent.from("int32"), ((HMXType.LitType) solved.getLeft()).tyName);
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

    assertTrue(solved.getLeft() instanceof HMXType.LitType);
    assertEquals(TypeIdent.from("unit"), ((HMXType.LitType) solved.getLeft()).tyName);
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

    assertTrue(solved.getLeft() instanceof HMXType.LitType);
    assertEquals(TypeIdent.from("int32"), ((HMXType.LitType) solved.getLeft()).tyName);
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

    assertTrue(solved.getLeft() instanceof HMXType.LitType);
    assertEquals(TypeIdent.TYPE_IDENT_UNIT, ((HMXType.LitType) solved.getLeft()).tyName);
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

    assertEquals(TypeIdent.TYPE_IDENT_UNIT, ((HMXType.LitType) solved.getLeft()).tyName);
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

    assertEquals(TypeIdent.TYPE_IDENT_UNIT, ((HMXType.LitType) solved.getLeft()).tyName);
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

    assertEquals(TypeIdent.TYPE_IDENT_UNIT, ((HMXType.LitType) solved.getLeft()).tyName);
  }

  /**
   * Two globally defined functions, one called from main. The called function
   * must keep its full body after type inference + instantiation. Regression
   * test: the function whose call produces a Sub constraint lost its entire
   * body (empty block) in the reconstructed program.
   *
   * NOTE: the program order matters here (abc first, main second)! This is a
   * workaround for a known solver bug: {@code ExprLetRec.solve} generalizes its
   * bindings strictly in order. When {@code main} comes first, its constraints
   * are solved while {@code abc} is still an env shell with a fresh, unbound
   * type var, leaving abc's return type unresolved (fails with
   * "Type int32 -> int32 -> tXX is not fully specified" or an unbound variable
   * in convertReturnOp). See {@link #mainBeforeCalleeBreaksInference}.
   */
  @Test
  public void calledFunctionKeepsBodyAfterSolve() {
    ProgramOp programOp = new ProgramOp(LOC);

    FuncOp abcFuncOp = programOp.addOperation(
        new FuncOp(LOC, "abc", FuncType.of(List.of(dgir.dialect.builtin.BuiltinTypes.IntegerT.INT32(),
            dgir.dialect.builtin.BuiltinTypes.IntegerT.INT32()),
            dgir.dialect.builtin.BuiltinTypes.IntegerT.INT32())));
    var binOp = abcFuncOp.addOperation(
        new BinaryOp(LOC, abcFuncOp.getArgument(0).orElseThrow(), abcFuncOp.getArgument(1).orElseThrow(),
            BinMode.ADD),
        0);
    abcFuncOp.addOperation(new ReturnOp(LOC, binOp.getResult()), 0);

    // int32 main() { abc(1, 2); return; }
    FuncOp funcMainOp = programOp.addOperation(
        new FuncOp(LOC, "main", FuncType.of(List.of(), dgir.core.ir.MaybeType.of())));
    var one = funcMainOp.addOperation(new ConstantOp(LOC, 1), 0);
    var two = funcMainOp.addOperation(new ConstantOp(LOC, 2), 0);
    funcMainOp.addOperation(new CallOp(LOC, abcFuncOp, one.getResult(), two.getResult()), 0);
    funcMainOp.addOperation(new ReturnOp(LOC), 0);
    var solved = solve(programOp);
    var rebuiltAbc = solved.getRight().stream().map(Operation::asOp)
        .filter(o -> o instanceof FuncOp).map(o -> (FuncOp) o)
        .filter(o -> "abc".equals(o.getFuncName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("rebuilt abc function missing from result tree"));

    var abcBody = rebuiltAbc.getEntryBlock().getOperations();
    assertTrue(abcBody.stream().anyMatch(o -> o.asOp() instanceof BinaryOp),
        "abc lost its body after instantiation: " + abcBody);
    assertTrue(abcBody.stream().anyMatch(o -> o.asOp() instanceof ReturnOp),
        "abc lost its return after instantiation: " + abcBody);
  }

  /**
   * BUG (this test is INTENTIONALLY failing until the solver is fixed): the SAME
   * defining order flipped — {@code main} first, {@code abc} second.
   *
   * ExprLetRec.solve generalizes its bindings strictly in definition order.
   * main is generalized first while abc is still only an env shell with a
   * fresh, unbound type var; the Sub constraint of main's call then leaves
   * abc's return type var unresolved. Depending on the declared types this
   * surfaces as either
   * "Type int32 -> int32 -> tXX is not fully specified" (TypingException)
   * or an unbound variable thrown from convertReturnOp during instantiation.
   *
   * Fix direction: generalize in dependency order (or defer/iterate instead of
   * a single ordered pass). The dsl never hits this only because its source
   * order always lists callees before callers.
   */
  @Test
  public void mainBeforeCalleeBreaksInference() {
    ProgramOp programOp = new ProgramOp(LOC);

    // int32 main() { abc(1, 2); return; }
    FuncOp funcMainOp = programOp.addOperation(
        new FuncOp(LOC, "main", FuncType.of(List.of(), dgir.core.ir.MaybeType.of())));

    // int32 abc(int32 a, int32 b) { return a + b; }
    FuncOp abcFuncOp = programOp.addOperation(
        new FuncOp(LOC, "abc", FuncType.of(List.of(dgir.dialect.builtin.BuiltinTypes.IntegerT.INT32(),
            dgir.dialect.builtin.BuiltinTypes.IntegerT.INT32()),
            dgir.dialect.builtin.BuiltinTypes.IntegerT.INT32())));
    var binOp = abcFuncOp.addOperation(
        new BinaryOp(LOC, abcFuncOp.getArgument(0).orElseThrow(), abcFuncOp.getArgument(1).orElseThrow(),
            BinMode.ADD),
        0);
    abcFuncOp.addOperation(new ReturnOp(LOC, binOp.getResult()), 0);

    var one = funcMainOp.addOperation(new ConstantOp(LOC, 1), 0);
    var two = funcMainOp.addOperation(new ConstantOp(LOC, 2), 0);
    funcMainOp.addOperation(new CallOp(LOC, abcFuncOp, one.getResult(), two.getResult()), 0);
    funcMainOp.addOperation(new ReturnOp(LOC), 0);

    var solved = solve(programOp);

    var rebuiltAbc = solved.getRight().stream().map(Operation::asOp)
        .filter(o -> o instanceof FuncOp).map(o -> (FuncOp) o)
        .filter(o -> "abc".equals(o.getFuncName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("rebuilt abc function missing from result tree"));

    var abcBody = rebuiltAbc.getEntryBlock().getOperations();
    assertTrue(abcBody.stream().anyMatch(o -> o.asOp() instanceof BinaryOp),
        "abc lost its body after instantiation: " + abcBody);
    assertTrue(abcBody.stream().anyMatch(o -> o.asOp() instanceof ReturnOp),
        "abc lost its return after instantiation: " + abcBody);
  }
}

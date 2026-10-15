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
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFInference;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.debug.Location;
import dgir.dialect.arith.ArithOps.ConstantOp;
import dgir.dialect.arith.ArithSystemFConversion;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.builtin.BuiltinSystemFConversion;
import dgir.dialect.builtin.BuiltinTypes.IntegerT;
import dgir.dialect.cell.CellOps.CreateCellOp;
import dgir.dialect.cell.CellOps.SetCellOp;
import dgir.dialect.cell.CellSystemFConversion;
import dgir.dialect.func.FuncOps.FuncOp;
import dgir.dialect.func.FuncOps.ReturnOp;
import dgir.dialect.func.FuncSystemFConversion;
import dgir.dialect.io.IoOps.PrintOp;
import dgir.dialect.io.IoSystemFConversion;
import dgir.dialect.scf.ScfOps.EndOp;
import dgir.dialect.scf.ScfOps.ForOp;
import dgir.dialect.scf.ScfOps.IfOp;
import dgir.dialect.scf.ScfOps.ContinueOp;
import dgir.dialect.scf.ScfSystemFConversion;

public class SystemFDialectsConversionTest {
  static final Location LOC = Location.UNKNOWN;

  @BeforeEach
  public void setup() {
    ConverterRegistry.registerDialect(SystemFInference.class);
    Dialect.registerAllDialects();
    BuiltinSystemFConversion.registerBuiltinSystemFConversion();
    FuncSystemFConversion.registerBuiltinSystemFConversion();
    ArithSystemFConversion.registerBuiltinSystemFConversion();
    CellSystemFConversion.registerBuiltinSystemFConversion();
    IoSystemFConversion.registerBuiltinSystemFConversion();
    ScfSystemFConversion.registerBuiltinSystemFConversion();
  }

  private static Pair<SystemFType, List<Operation>> solve(ProgramOp programOp) {
    var inference = new SystemFInference();
    var solver = inference.getNewSolverInstance();
    var solvedPair = solver.solve(ExprOrOperator.of(programOp.getOperation()));

    List<Operation> ops = new ArrayList<>();
    new ExpressionVisitor<Expr, SystemFType>(VisitOrder.POST_ORDER, VisitGetChildrenOption.ALL_CHILDREN)
        .visit(solvedPair.instantiated(), e -> e.getUnderlyingOperation().ifPresent(ops::add));

    return Pair.of(solvedPair.type(), ops);
  }

  private static long countOps(List<Operation> ops, Class<? extends dgir.core.ir.Op> clazz) {
    return ops.stream().filter(op -> clazz.isInstance(op.asOp())).count();
  }

  @Test
  public void createCellFromOperandSurvivesRebuild() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var text = funcOp.addOperation(new ConstantOp(LOC, "cell"), 0);
    var cell = funcOp.addOperation(new CreateCellOp(LOC, text.getResult()), 0);
    funcOp.addOperation(new ReturnOp(LOC, cell.getResult()), 0);

    var solved = solve(programOp);

    assertEquals(1, countOps(solved.getRight(), CreateCellOp.class), "rebuilt create-cell op missing");
    assertTrue(solved.getLeft() instanceof SystemFType.Lit);
    assertEquals(TypeIdent.TYPE_IDENT_STRING, ((SystemFType.Lit) solved.getLeft()).ident);
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

    assertEquals(1, countOps(solved.getRight(), CreateCellOp.class), "rebuilt create-cell op missing");
    assertEquals(1, countOps(solved.getRight(), SetCellOp.class), "rebuilt set-cell op missing");
    assertTrue(solved.getLeft() instanceof SystemFType.Lit);
    assertEquals(TypeIdent.from("int64"), ((SystemFType.Lit) solved.getLeft()).ident);
  }

  @Test
  public void printInsideIfSurvivesRebuild() {
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

    assertEquals(1, countOps(solved.getRight(), IfOp.class), "rebuilt if op missing");
    assertEquals(1, countOps(solved.getRight(), PrintOp.class), "rebuilt print op missing");
    assertEquals(1, countOps(solved.getRight(), EndOp.class), "rebuilt end op missing");
  }

  @Test
  public void simpleForLoopSurvivesRebuild() {
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

    assertEquals(1, countOps(solved.getRight(), ForOp.class), "rebuilt for op missing");
    assertEquals(1, countOps(solved.getRight(), ContinueOp.class), "rebuilt continue op missing");
  }
}

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
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFInference;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.debug.Location;
import dgir.dialect.arith.ArithOps.ConstantOp;
import dgir.dialect.arith.ArithSystemFConversion;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.builtin.BuiltinTypes.IntegerT;
import dgir.dialect.func.FuncOps.FuncOp;
import dgir.dialect.func.FuncOps.ReturnOp;
import dgir.dialect.builtin.BuiltinSystemFConversion;
import dgir.dialect.func.FuncSystemFConversion;
import dgir.dialect.mem.MemOps.AllocGcFromElementsOp;
import dgir.dialect.mem.MemOps.AllocGcOp;
import dgir.dialect.mem.MemOps.GetElementOp;
import dgir.dialect.mem.MemOps.SetElementOp;
import dgir.dialect.mem.MemSystemFConversion;

public class MemSystemFConversionTest {
  static final Location LOC = Location.UNKNOWN;

  @BeforeEach
  public void setup() {
    ConverterRegistry.registerDialect(SystemFInference.class);
    Dialect.registerAllDialects();
    FuncSystemFConversion.registerBuiltinSystemFConversion();
    BuiltinSystemFConversion.registerBuiltinSystemFConversion();
    ArithSystemFConversion.registerBuiltinSystemFConversion();
    MemSystemFConversion.registerBuiltinSystemFConversion();
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
  public void allocGcWithDynamicSizeSurvivesRebuild() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var size = funcOp.addOperation(new ConstantOp(LOC, 4), 0);
    funcOp.addOperation(new AllocGcOp(LOC, IntegerT.INT32(), size.getResult()), 0);
    funcOp.addOperation(new ReturnOp(LOC), 0);

    var solved = solve(programOp);

    assertEquals(1, countOps(solved.getRight(), AllocGcOp.class), "rebuilt alloc op missing");
    assertEquals(1, countOps(solved.getRight(), ConstantOp.class), "size constant missing");
    assertTrue(solved.getLeft() instanceof SystemFType.Lit);
  }

  @Test
  public void allocGcFromElementsSurvivesRebuild() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var a = funcOp.addOperation(new ConstantOp(LOC, 1), 0);
    var b = funcOp.addOperation(new ConstantOp(LOC, 2), 0);
    funcOp.addOperation(new AllocGcFromElementsOp(LOC, IntegerT.INT32(), List.of(a.getResult(), b.getResult()), true),
        0);
    funcOp.addOperation(new ReturnOp(LOC), 0);

    var solved = solve(programOp);

    assertEquals(1, countOps(solved.getRight(), AllocGcFromElementsOp.class), "rebuilt alloc-from-elements missing");
    assertEquals(2, countOps(solved.getRight(), ConstantOp.class), "element constants missing");
    assertTrue(solved.getLeft() instanceof SystemFType.Lit);
  }

  @Test
  public void getSetElementSurviveRebuild() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var size = funcOp.addOperation(new ConstantOp(LOC, 4), 0);
    var alloc = funcOp.addOperation(new AllocGcOp(LOC, IntegerT.INT32(), size.getResult()), 0);
    var idx = funcOp.addOperation(new ConstantOp(LOC, 1), 0);
    var value = funcOp.addOperation(new ConstantOp(LOC, 42), 0);
    funcOp.addOperation(new SetElementOp(LOC, alloc.getResult(), idx.getResult(), value.getResult()), 0);
    var loaded = funcOp.addOperation(new GetElementOp(LOC, alloc.getResult(), idx.getResult()), 0);
    funcOp.addOperation(new ReturnOp(LOC, loaded.getResult()), 0);

    var solved = solve(programOp);

    assertEquals(1, countOps(solved.getRight(), AllocGcOp.class), "rebuilt alloc op missing");
    assertEquals(1, countOps(solved.getRight(), SetElementOp.class), "rebuilt set-element missing");
    assertEquals(1, countOps(solved.getRight(), GetElementOp.class), "rebuilt get-element missing");
    assertEquals(3, countOps(solved.getRight(), ConstantOp.class), "constants missing");
    assertTrue(solved.getLeft() instanceof SystemFType.Lit);
  }
}

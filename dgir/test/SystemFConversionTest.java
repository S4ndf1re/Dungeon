import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dgir.core.ir.Block;
import dgir.core.ir.Dialect;
import dgir.core.ir.Operation;
import dgir.core.ir.types.Expression.ExpressionVisitor;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitGetChildrenOption;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.systemf.Expr;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.systemf.SystemFInference;
import dgir.core.ir.types.systemf.SystemFType;
import dgir.core.debug.Location;
import dgir.dialect.arith.ArithOps.ConstantOp;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.arith.ArithSystemFConversion;
import dgir.dialect.builtin.BuiltinSystemFConversion;
import dgir.dialect.cf.CfOps.AssertOp;
import dgir.dialect.cf.CfOps.BranchCondOp;
import dgir.dialect.cf.CfOps.BranchOp;
import dgir.dialect.cf.CfSystemFConversion;
import dgir.dialect.func.FuncOps.CallIndirectOp;
import dgir.dialect.func.FuncOps.CallOp;

import dgir.dialect.func.FuncOps.FuncOp;
import dgir.dialect.func.FuncOps.ReturnOp;
import dgir.dialect.func.FuncSystemFConversion;
import dgir.dialect.func.FuncTypes.FuncType;

public class SystemFConversionTest {
  static final Location LOC = Location.UNKNOWN;

  @BeforeEach
  public void setup() {
    ConverterRegistry.registerDialect(SystemFInference.class);
    Dialect.registerAllDialects();
    FuncSystemFConversion.registerBuiltinSystemFConversion();
    BuiltinSystemFConversion.registerBuiltinSystemFConversion();
    ArithSystemFConversion.registerBuiltinSystemFConversion();
    CfSystemFConversion.registerBuiltinSystemFConversion();
  }

  private static Pair<Type, List<Operation>> solve(ProgramOp programOp) {
    var inference = new SystemFInference();
    var solver = inference.getSolverInstance();
    var solvedPair = solver.solve(ExprOrOperator.of(programOp.getOperation()));
        DgirTestUtils.saveInferenceCfg("", programOp.getOperation(), solvedPair.getRight());
        DgirTestUtils.saveDotExpr(solvedPair.getRight());
        DgirTestUtils.saveDotType(solvedPair.getLeft());

    List<Operation> ops = new ArrayList<>();
    new ExpressionVisitor<Expr, SystemFType>(VisitOrder.POST_ORDER, VisitGetChildrenOption.ALL_CHILDREN)
        .visit(solvedPair.getRight(), e -> e.getUnderlyingOperation().ifPresent(ops::add));

    return Pair.of(solvedPair.getLeft(), ops);
  }

  private static long countOps(List<Operation> ops, Class<? extends dgir.core.ir.Op> clazz) {
    return ops.stream().filter(op -> clazz.isInstance(op.asOp())).count();
  }

  @Test
  public void simpleReturnInfersUnit() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var numberOp = funcOp.addOperation(new ConstantOp(LOC, 42), 0);
    funcOp.addOperation(new ReturnOp(LOC, numberOp.getResult()), 0);

    var solved = solve(programOp);

    assertTrue(solved.getLeft() instanceof SystemFType.Lit);
    assertEquals(TypeIdent.TYPE_IDENT_INT, ((SystemFType.Lit) solved.getLeft()).ident);
  }

  @Test
  public void assertInfersUnit() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var condOp = funcOp.addOperation(new ConstantOp(LOC, true), 0);
    funcOp.addOperation(new AssertOp(LOC, condOp.getResult()), 0);

    funcOp.addOperation(new ReturnOp(LOC), 0);

    var solved = solve(programOp);

    assertEquals(1, countOps(solved.getRight(), AssertOp.class), "rebuilt assert op missing from result tree");

    assertTrue(solved.getLeft() instanceof SystemFType.Lit);
    assertEquals(TypeIdent.TYPE_IDENT_UNIT, ((SystemFType.Lit) solved.getLeft()).ident);
  }

  @Test
  public void branchCondReconstructsSuccessorBlocks() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    Block entryBlock = funcOp.getEntryBlock();
    Block leftBlock = funcOp.addBlock(new Block());
    Block rightBlock = funcOp.addBlock(new Block());
    Block endBlock = funcOp.addBlock(new Block());

    var cond = entryBlock.addOperation(new ConstantOp(LOC, true));
    entryBlock.addOperation(new BranchCondOp(LOC, cond.getResult(), leftBlock, rightBlock));

    leftBlock.addOperation(new ConstantOp(LOC, 1L));
    leftBlock.addOperation(new BranchOp(LOC, endBlock));

    rightBlock.addOperation(new ConstantOp(LOC, 2L));
    rightBlock.addOperation(new BranchOp(LOC, endBlock));

    endBlock.addOperation(new ReturnOp(LOC));

    var solved = solve(programOp);

    assertEquals(1, countOps(solved.getRight(), BranchCondOp.class));
    assertEquals(2, countOps(solved.getRight(), BranchOp.class));
    assertEquals(TypeIdent.TYPE_IDENT_UNIT, ((SystemFType.Lit) solved.getLeft()).ident);
  }

  @Test
  public void directCallReconstructsCallOp() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMainOp = entry.getRight();

    var helperOp = programOp.addOperation(new FuncOp(LOC, "helper", FuncType.empty()));
    helperOp.addOperation(new ReturnOp(LOC), 0);

    funcMainOp.addOperation(new CallOp(LOC, "helper", FuncType.empty()), 0);
    funcMainOp.addOperation(new ReturnOp(LOC), 0);

    var solved = solve(programOp);

    // One call to helper + the synthetic call to main inserted by the
    // ProgramOp conversion
    assertEquals(2, countOps(solved.getRight(), CallOp.class), "rebuilt call ops missing from result tree");
    assertTrue(solved.getLeft() instanceof SystemFType.Lit);
  }

  @Test
  public void indirectCallReconstructsCallIndirectOp() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMainOp = entry.getRight();

    var helperOp = programOp.addOperation(new FuncOp(LOC, "helper", FuncType.empty()));
    helperOp.addOperation(new ReturnOp(LOC), 0);

    var constFn = funcMainOp.addOperation(new dgir.dialect.func.FuncOps.ConstantOp(LOC, "helper", FuncType.empty()), 0);
    funcMainOp.addOperation(new CallIndirectOp(LOC, constFn.getResult(), List.of()), 0);
    funcMainOp.addOperation(new ReturnOp(LOC), 0);

    var solved = solve(programOp);

    assertEquals(1, countOps(solved.getRight(), CallIndirectOp.class),
        "rebuilt indirect call op missing from result tree");
    assertTrue(solved.getLeft() instanceof SystemFType.Lit);
  }
}

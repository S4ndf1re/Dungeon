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
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitGetChildrenOption;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.systemf.Expr;
import dgir.core.ir.types.systemf.SystemFInference;
import dgir.core.ir.types.systemf.SystemFType;
import dgir.core.debug.Location;
import dgir.dialect.arith.ArithOps.ConstantOp;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.arith.ArithSystemFConversion;
import dgir.dialect.builtin.BuiltinSystemFConversion;
import dgir.dialect.func.FuncOps.FuncOp;
import dgir.dialect.func.FuncOps.ReturnOp;
import dgir.dialect.func.FuncSystemFConversion;
import dgir.dialect.str.StrOps.ConcatOp;
import dgir.dialect.str.StrOps.LengthOp;
import dgir.dialect.str.StrOps.ToStringOp;
import dgir.dialect.str.StrOps.TrimOp;
import dgir.dialect.str.StringSystemFConversion;

public class StrSystemFConversionTest {
  static final Location LOC = Location.UNKNOWN;

  @BeforeEach
  public void setup() {
    ConverterRegistry.registerDialect(SystemFInference.class);
    Dialect.registerAllDialects();
    FuncSystemFConversion.registerBuiltinSystemFConversion();
    BuiltinSystemFConversion.registerBuiltinSystemFConversion();
    ArithSystemFConversion.registerBuiltinSystemFConversion();
    StringSystemFConversion.registerBuiltinSystemFConversion();
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
  public void concatInfersString() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var a = funcOp.addOperation(new ConstantOp(LOC, "Hello "), 0);
    var b = funcOp.addOperation(new ConstantOp(LOC, "World"), 0);
    ConcatOp concat = funcOp.addOperation(new ConcatOp(LOC, a.getResult(), b.getResult()), 0);

    funcOp.addOperation(new ReturnOp(LOC, concat.getResult()), 0);

    var solved = solve(programOp);

    assertEquals(1, countOps(solved.getRight(), ConcatOp.class), "rebuilt concat op missing from result tree");

    assertTrue(solved.getLeft() instanceof SystemFType.Lit);
    assertEquals(TypeIdent.from("string"), ((SystemFType.Lit) solved.getLeft()).ident);
  }

  @Test
  public void chainedStrOpsSurviveRebuild() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var text = funcOp.addOperation(new ConstantOp(LOC, "  Hello  "), 0);
    var number = funcOp.addOperation(new ConstantOp(LOC, 42), 0);
    var asText = funcOp.addOperation(new ToStringOp(LOC, number.getResult()), 0);
    var trimmed = funcOp.addOperation(new TrimOp(LOC, text.getResult()), 0);
    var combined = funcOp.addOperation(new ConcatOp(LOC, trimmed.getResult(), asText.getResult()), 0);
    var length = funcOp.addOperation(new LengthOp(LOC, combined.getResult()), 0);

    funcOp.addOperation(new ReturnOp(LOC, length.getResult()), 0);

    var solved = solve(programOp);

    // Every str op of the chain must be part of the reconstructed tree.
    assertEquals(1, countOps(solved.getRight(), ToStringOp.class), "rebuilt toString op missing");
    assertEquals(1, countOps(solved.getRight(), TrimOp.class), "rebuilt trim op missing");
    assertEquals(1, countOps(solved.getRight(), ConcatOp.class), "rebuilt concat op missing");
    assertEquals(1, countOps(solved.getRight(), LengthOp.class), "rebuilt length op missing");

    assertTrue(solved.getLeft() instanceof SystemFType.Lit);
    assertEquals(TypeIdent.from("int32"), ((SystemFType.Lit) solved.getLeft()).ident);
  }
}

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static dgir.dialect.builtin.BuiltinAttrs.IntegerAttribute;
import static dgir.dialect.builtin.BuiltinTypes.IntegerT;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dgir.core.ir.Block;
import dgir.core.ir.Dialect;
import dgir.core.ir.MaybeType;
import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.types.Expression.ExpressionVisitor;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitGetChildrenOption;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.builtin.systemf.SystemFInference;
import dgir.core.ir.types.builtin.systemf.SystemFType;
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

  private static Pair<SystemFType, List<Operation>> solve(ProgramOp programOp) {
    var inference = new SystemFInference();
    var solver = inference.getNewSolverInstance();
    var solvedPair = solver.solve(ExprOrOperator.of(programOp.getOperation()));
    DgirTestUtils.saveDotExprPreInstantiation(solvedPair.preInstantiation());
    DgirTestUtils.saveInferenceCfg("", programOp.getOperation(), solvedPair.instantiated());
    DgirTestUtils.saveDotExpr(solvedPair.instantiated());
    DgirTestUtils.saveDotExprScopes(solvedPair.instantiated());
    DgirTestUtils.saveDotType(solvedPair.type());

    List<Operation> ops = new ArrayList<>();
    new ExpressionVisitor<Expr, SystemFType>(VisitOrder.POST_ORDER, VisitGetChildrenOption.ALL_CHILDREN)
        .visit(solvedPair.instantiated(), e -> e.getUnderlyingOperation().ifPresent(ops::add));

    return Pair.of(solvedPair.type(), ops);
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

  @Test
  public void polymorphicIdentityCallWithTypeApplication() {
    ProgramOp programOp = new ProgramOp(LOC);

    // id :: forall a. a -> a — the unknown parameter type becomes a TAbs
    var idOp = programOp.addOperation(
        new FuncOp(LOC, "id", FuncType.of(List.of(MaybeType.of()), MaybeType.of())));
    FuncOp funcMainOp = programOp.addOperation(new FuncOp(LOC, "main"));
    Value x = idOp.getRegion().getRegionValue(0).orElseThrow();
    idOp.addOperation(new ReturnOp(LOC, x), 0);

    // id[int32](42) — the type parameter becomes a TApp on the callee
    var arg = funcMainOp.addOperation(new ConstantOp(LOC, new IntegerAttribute(42, IntegerT.INT32())), 0);
    var call = funcMainOp.addOperation(
        new CallOp(LOC, "id", List.of(arg.getResult()), List.of(Optional.of(IntegerT.INT32())), MaybeType.of()), 0);
    funcMainOp.addOperation(new ReturnOp(LOC, call.getOutputValue().orElseThrow()), 0);

    var solved = solve(programOp);

    // One call to id + the synthetic call to main inserted by the ProgramOp
    // conversion
    assertEquals(2, countOps(solved.getRight(), CallOp.class), "rebuilt call ops missing from result tree");

    var rebuiltId = solved.getRight().stream()
        .filter(op -> op.asOp() instanceof FuncOp f && f.getFuncName().equals("id"))
        .map(op -> (FuncOp) op.asOp())
        .findFirst()
        .orElseThrow(() -> new AssertionError("rebuilt id func op missing from result tree"));
    assertEquals(FuncType.of(List.of(MaybeType.of(IntegerT.INT32())), MaybeType.of(IntegerT.INT32())),
        rebuiltId.getType());

    assertTrue(solved.getLeft() instanceof SystemFType.Lit);
    assertEquals(TypeIdent.TYPE_IDENT_INT, ((SystemFType.Lit) solved.getLeft()).ident);
  }

  private static FuncOp findFunc(List<Operation> ops, String name) {
    return ops.stream()
        .filter(op -> op.asOp() instanceof FuncOp f && f.getFuncName().equals(name))
        .map(op -> (FuncOp) op.asOp())
        .findFirst()
        .orElseThrow(() -> new AssertionError("rebuilt " + name + " func op missing from result tree"));
  }

  @Test
  public void polymorphicIdentityCallWithInferredTypeApplication() {
    ProgramOp programOp = new ProgramOp(LOC);

    var idOp = programOp.addOperation(
        new FuncOp(LOC, "id", FuncType.of(List.of(MaybeType.of()), MaybeType.of())));
    FuncOp funcMainOp = programOp.addOperation(new FuncOp(LOC, "main"));
    Value x = idOp.getRegion().getRegionValue(0).orElseThrow();
    idOp.addOperation(new ReturnOp(LOC, x), 0);

    // id[_](42) — the hole is inferred from the value argument
    var arg = funcMainOp.addOperation(new ConstantOp(LOC, new IntegerAttribute(42, IntegerT.INT32())), 0);
    var call = funcMainOp.addOperation(
        new CallOp(LOC, "id", List.of(arg.getResult()), List.of(Optional.empty()), MaybeType.of()), 0);
    funcMainOp.addOperation(new ReturnOp(LOC, call.getOutputValue().orElseThrow()), 0);

    var solved = solve(programOp);

    assertEquals(FuncType.of(List.of(MaybeType.of(IntegerT.INT32())), MaybeType.of(IntegerT.INT32())),
        findFunc(solved.getRight(), "id").getType());
    assertEquals(TypeIdent.TYPE_IDENT_INT, ((SystemFType.Lit) solved.getLeft()).ident);
  }

  @Test
  public void mixedAnnotatedAndInferredTypeApplications() {
    ProgramOp programOp = new ProgramOp(LOC);

    // first :: forall a. forall b. a -> int32 -> b -> a — the known int32
    // parameter gets no type abstraction
    var firstOp = programOp.addOperation(new FuncOp(LOC, "first",
        FuncType.of(List.of(MaybeType.of(), MaybeType.of(IntegerT.INT32()), MaybeType.of()), MaybeType.of())));
    FuncOp funcMainOp = programOp.addOperation(new FuncOp(LOC, "main"));
    Value x = firstOp.getRegion().getRegionValue(0).orElseThrow();
    firstOp.addOperation(new ReturnOp(LOC, x), 0);

    // first[int32][_](42, 1, true) — a is annotated, b is inferred as bool
    var a = funcMainOp.addOperation(new ConstantOp(LOC, new IntegerAttribute(42, IntegerT.INT32())), 0);
    var n = funcMainOp.addOperation(new ConstantOp(LOC, new IntegerAttribute(1, IntegerT.INT32())), 0);
    var b = funcMainOp.addOperation(new ConstantOp(LOC, true), 0);
    var call = funcMainOp.addOperation(new CallOp(LOC, "first",
        List.of(a.getResult(), n.getResult(), b.getResult()),
        List.of(Optional.of(IntegerT.INT32()), Optional.empty()), MaybeType.of()), 0);
    funcMainOp.addOperation(new ReturnOp(LOC, call.getOutputValue().orElseThrow()), 0);

    var solved = solve(programOp);

    assertEquals(FuncType.of(
        List.of(MaybeType.of(IntegerT.INT32()), MaybeType.of(IntegerT.INT32()), MaybeType.of(IntegerT.BOOL())),
        MaybeType.of(IntegerT.INT32())),
        findFunc(solved.getRight(), "first").getType());
    assertEquals(TypeIdent.TYPE_IDENT_INT, ((SystemFType.Lit) solved.getLeft()).ident);
  }

  @Test
  public void polymorphicIdentityCallWithImplicitInstantiation() {
    ProgramOp programOp = new ProgramOp(LOC);

    var idOp = programOp.addOperation(
        new FuncOp(LOC, "id", FuncType.of(List.of(MaybeType.of()), MaybeType.of())));
    FuncOp funcMainOp = programOp.addOperation(new FuncOp(LOC, "main"));
    Value x = idOp.getRegion().getRegionValue(0).orElseThrow();
    idOp.addOperation(new ReturnOp(LOC, x), 0);

    // id(42) — no type arguments at all, the application instantiates them
    var arg = funcMainOp.addOperation(new ConstantOp(LOC, new IntegerAttribute(42, IntegerT.INT32())), 0);
    var call = funcMainOp.addOperation(
        new CallOp(LOC, "id", List.of(arg.getResult()), List.of(), MaybeType.of()), 0);
    funcMainOp.addOperation(new ReturnOp(LOC, call.getOutputValue().orElseThrow()), 0);

    var solved = solve(programOp);

    assertEquals(FuncType.of(List.of(MaybeType.of(IntegerT.INT32())), MaybeType.of(IntegerT.INT32())),
        findFunc(solved.getRight(), "id").getType());
    assertEquals(TypeIdent.TYPE_IDENT_INT, ((SystemFType.Lit) solved.getLeft()).ident);
  }

  private static FuncOp addFirstFunc(ProgramOp programOp) {
    // first :: forall a. forall b. a -> int32 -> b -> a
    var firstOp = programOp.addOperation(new FuncOp(LOC, "first",
        FuncType.of(List.of(MaybeType.of(), MaybeType.of(IntegerT.INT32()), MaybeType.of()), MaybeType.of())));
    Value x = firstOp.getRegion().getRegionValue(0).orElseThrow();
    firstOp.addOperation(new ReturnOp(LOC, x), 0);
    return firstOp;
  }

  private static void assertFirstInstance(Pair<SystemFType, List<Operation>> solved) {
    assertEquals(FuncType.of(
        List.of(MaybeType.of(IntegerT.INT32()), MaybeType.of(IntegerT.INT32()), MaybeType.of(IntegerT.BOOL())),
        MaybeType.of(IntegerT.INT32())),
        findFunc(solved.getRight(), "first").getType());
    assertEquals(TypeIdent.TYPE_IDENT_INT, ((SystemFType.Lit) solved.getLeft()).ident);
  }

  private static void addFirstCall(FuncOp funcMainOp, List<Optional<dgir.core.ir.Type>> typeArgs) {
    var a = funcMainOp.addOperation(new ConstantOp(LOC, new IntegerAttribute(42, IntegerT.INT32())), 0);
    var n = funcMainOp.addOperation(new ConstantOp(LOC, new IntegerAttribute(1, IntegerT.INT32())), 0);
    var b = funcMainOp.addOperation(new ConstantOp(LOC, true), 0);
    var call = funcMainOp.addOperation(new CallOp(LOC, "first",
        List.of(a.getResult(), n.getResult(), b.getResult()), typeArgs, MaybeType.of()), 0);
    funcMainOp.addOperation(new ReturnOp(LOC, call.getOutputValue().orElseThrow()), 0);
  }

  @Test
  public void multiArgumentCallWithImplicitInstantiation() {
    ProgramOp programOp = new ProgramOp(LOC);
    addFirstFunc(programOp);
    FuncOp funcMainOp = programOp.addOperation(new FuncOp(LOC, "main"));

    // first(42, 1, true) — the innermost application instantiates both type
    // abstractions, b is only solved by the outermost one
    addFirstCall(funcMainOp, List.of());

    assertFirstInstance(solve(programOp));
  }

  @Test
  public void explicitAndImplicitInstantiationCompose() {
    ProgramOp programOp = new ProgramOp(LOC);
    addFirstFunc(programOp);
    FuncOp funcMainOp = programOp.addOperation(new FuncOp(LOC, "main"));

    // first[int32](42, 1, true) — a is applied explicitly, the remaining b is
    // instantiated implicitly by the application
    addFirstCall(funcMainOp, List.of(Optional.of(IntegerT.INT32())));

    assertFirstInstance(solve(programOp));
  }

  @Test
  public void annotatedTypeApplicationMismatchIsRejected() {
    ProgramOp programOp = new ProgramOp(LOC);

    var idOp = programOp.addOperation(
        new FuncOp(LOC, "id", FuncType.of(List.of(MaybeType.of()), MaybeType.of())));
    FuncOp funcMainOp = programOp.addOperation(new FuncOp(LOC, "main"));
    Value x = idOp.getRegion().getRegionValue(0).orElseThrow();
    idOp.addOperation(new ReturnOp(LOC, x), 0);

    // id[bool](42) — the annotation contradicts the value argument
    var arg = funcMainOp.addOperation(new ConstantOp(LOC, new IntegerAttribute(42, IntegerT.INT32())), 0);
    var call = funcMainOp.addOperation(
        new CallOp(LOC, "id", List.of(arg.getResult()), List.of(Optional.of(IntegerT.BOOL())), MaybeType.of()), 0);
    funcMainOp.addOperation(new ReturnOp(LOC, call.getOutputValue().orElseThrow()), 0);

    assertThrows(TypingException.class, () -> solve(programOp));
  }
}

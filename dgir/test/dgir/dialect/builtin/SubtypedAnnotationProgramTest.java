package dgir.dialect.builtin;

import static dgir.dialect.arith.ArithOps.BinaryOp;
import static dgir.dialect.arith.ArithOps.ConstantOp;
import static dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;
import static dgir.dialect.builtin.BuiltinAttrs.IntegerAttribute;
import static dgir.dialect.builtin.BuiltinOps.ProgramOp;
import static dgir.dialect.builtin.BuiltinTypes.IntegerT;
import static dgir.dialect.cf.CfOps.AssertOp;
import static dgir.dialect.cf.CfOps.BranchCondOp;
import static dgir.dialect.func.FuncOps.CallOp;
import static dgir.dialect.func.FuncOps.FuncOp;
import static dgir.dialect.func.FuncOps.ReturnOp;
import static dgir.dialect.func.FuncTypes.FuncType;

import dgir.core.analysis.OperationVerifier.VerifyOptions;
import dgir.core.debug.Location;
import dgir.core.ir.Block;
import dgir.core.ir.Dialect;
import dgir.core.ir.MaybeType;
import dgir.core.ir.Type;
import dgir.core.ir.TypeUniquer;
import dgir.core.ir.Value;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.dialect.arith.ArithHMXConversion;
import dgir.dialect.builtin.BuiltinTypes.IntegerT;
import dgir.dialect.builtin.BuiltinTypes.UnitType;
import dgir.dialect.cf.CfHMXConversion;
import dgir.dialect.func.FuncHMXConversion;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reconstructs the dsl {@code subtyped-annotation-function-test}:
 *
 * <pre>
 * (defn foo [a : integer] (if (&lt; a 10) (return (bar (+ a 1))) (return a)))
 * (defn bar [a : integer] (return (foo a)))
 * (defn bar2 [a : int64] (return (foo a)))
 * (defn main [] (assert (= (bar2 11l) 11)))
 * </pre>
 *
 * {@code foo} is called with {@code int32} (through {@code bar}'s own chain)
 * and with {@code int64} (through {@code bar2}) — the scheme instances must
 * stay independent.
 */
public class SubtypedAnnotationProgramTest {
  private static final Location LOC = Location.UNKNOWN;

  @BeforeAll
  public static void setup() {
    ConverterRegistry.registerDialect(HMXInference.class);
    Dialect.registerAllDialects();
    FuncHMXConversion.registerBuiltinHMXConversion();
    BuiltinHMXConversion.registerBuiltinHMXConversion();
    ArithHMXConversion.registerBuiltinHMXConversion();
    CfHMXConversion.registerBuiltinHMXConversion();
  }

  private static Type integerT() {
    return IntegerT.baseType;
  }

  private static ProgramOp buildSubtypedProgram() {
    ProgramOp programOp = new ProgramOp(LOC);
    var int32 = IntegerT.INT32();
    var int64 = IntegerT.INT64();

    // (defn foo [a : integer] ...)
    var fooType = FuncType.of(List.of(MaybeType.of(integerT())), MaybeType.of());
    FuncOp foo = programOp.addOperation(new FuncOp(LOC, "foo", fooType));
    // (defn bar [a : integer] (return (foo a)))
    FuncOp bar = programOp.addOperation(new FuncOp(LOC, "bar", fooType));
    // (defn bar2 [a : int64] (return (foo a)))
    FuncOp bar2 = programOp.addOperation(
        new FuncOp(LOC, "bar2", FuncType.of(List.of(MaybeType.of((Type) int64)), MaybeType.of())));

    // foo body
    Value a = foo.getRegion().getRegionValue(0).orElseThrow();
    Block thenBlock = foo.addBlock(new Block());
    Block elseBlock = foo.addBlock(new Block());

    Block entry = foo.getEntryBlock();
    var ten = entry.addOperation(new ConstantOp(LOC, new IntegerAttribute(10, int32)));
    var lt = entry.addOperation(new BinaryOp(LOC, a, ten.getResult(), BinMode.LT));
    entry.addOperation(new BranchCondOp(LOC, lt.getResult(), thenBlock, elseBlock));

    // (bar (+ a 1))
    var one = thenBlock.addOperation(new ConstantOp(LOC, new IntegerAttribute(1, int32)));
    var add = thenBlock.addOperation(new BinaryOp(LOC, a, one.getResult(), BinMode.ADD));
    var callBar = thenBlock.addOperation(
        new CallOp(LOC, "bar", List.of(add.getOutputValue().orElseThrow()), MaybeType.of()));
    thenBlock.addOperation(new ReturnOp(LOC, callBar.getOutputValue().orElseThrow()));

    // a
    elseBlock.addOperation(new ReturnOp(LOC, a));

    // bar body: (return (foo a))
    Value barA = bar.getRegion().getRegionValue(0).orElseThrow();
    var callFoo = bar.getEntryBlock().addOperation(new CallOp(LOC, "foo", List.of(barA), MaybeType.of()));
    bar.getEntryBlock().addOperation(new ReturnOp(LOC, callFoo.getOutputValue().orElseThrow()));

    // bar2 body: (return (foo a))
    Value bar2A = bar2.getRegion().getRegionValue(0).orElseThrow();
    var callFoo2 = bar2.getEntryBlock().addOperation(new CallOp(LOC, "foo", List.of(bar2A), MaybeType.of()));
    bar2.getEntryBlock().addOperation(new ReturnOp(LOC, callFoo2.getOutputValue().orElseThrow()));

    // main: (assert (= (bar2 11l) 11))
    FuncOp main = programOp.addOperation(
        new FuncOp(LOC, "main", FuncType.of(List.of(), MaybeType.of())));
    Block mainBody = main.getEntryBlock();
    var eleven = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(11, int64)));
    var callBar2 = mainBody.addOperation(
        new CallOp(LOC, "bar2", List.of(eleven.getResult()), MaybeType.of()));
    var eleven2 = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(11, int64)));
    var eq = mainBody.addOperation(
        new BinaryOp(LOC, callBar2.getOutputValue().orElseThrow(), eleven2.getResult(), BinMode.EQ));
    mainBody.addOperation(new AssertOp(LOC, eq.getResult()));
    mainBody.addOperation(new ReturnOp(LOC));

    return programOp;
  }

  private static boolean solve(ProgramOp programOp) {
    var solver = new HMXInference().getNewSolverInstance();
    var solved = solver.solve(ExprOrOperator.of(programOp.getOperation()));
    var rebuilt = solved.instantiated().getUnderlyingOperation().orElseThrow();
    return rebuilt.verify(VerifyOptions.FULL_VERIFICATION);
  }

  @Test
  public void subtypedStandaloneIsGreen() {
    assertTrue(solve(buildSubtypedProgram()), "inferred program must verify");
  }

  /** Mirrors the dsl sequence: other inference runs in the same JVM before this one. */
  @Test
  public void subtypedAfterOtherProgramsIsGreen() {
    // warm-up solves mimicking earlier dsl tests sharing static solver state
    assertTrue(solve(buildSubtypedProgram()));
    assertTrue(solve(buildSubtypedProgram()));
    assertTrue(solve(buildSubtypedProgram()), "third sequential run must still verify");
  }
}

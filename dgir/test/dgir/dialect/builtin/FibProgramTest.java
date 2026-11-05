package dgir.dialect.builtin;

import static dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;
import static dgir.dialect.arith.ArithOps.BinaryOp;
import static dgir.dialect.arith.ArithOps.ConstantOp;
import static dgir.dialect.builtin.BuiltinOps.ProgramOp;
import static dgir.dialect.cf.CfOps.AssertOp;
import static dgir.dialect.cf.CfOps.BranchCondOp;
import static dgir.dialect.func.FuncOps.CallOp;
import static dgir.dialect.func.FuncOps.FuncOp;
import static dgir.dialect.func.FuncOps.ReturnOp;
import static dgir.dialect.func.FuncTypes.FuncType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dgir.core.analysis.OperationVerifier.VerifyOptions;
import dgir.core.debug.Location;
import dgir.core.ir.Block;
import dgir.core.ir.Dialect;
import dgir.core.ir.MaybeType;
import dgir.core.ir.Type;
import dgir.core.ir.Value;
import dgir.core.ir.types.builtin.systemf.SystemFInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.dialect.arith.ArithSystemFConversion;
import dgir.dialect.builtin.BuiltinTypes.IntegerT;
import dgir.dialect.cf.CfSystemFConversion;
import dgir.dialect.func.FuncSystemFConversion;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Reconstructs the IR of {@code test_assets/fib.dg}, with an annotated return
 * type on {@code fib}:
 *
 * <pre>
 * (defn fib [a: int64] : int64
 *   (if (&lt;= a 1)
 *     (return 1l)
 *     (return (+ (fib (- a 1)) (fib (- a 2))))))
 *
 * (defn main []
 *   (assert (= (fib 13l) 233l))
 *   (assert (= (fib 20l) 6765l)))
 * </pre>
 *
 * The unsuffixed literals are int32 and the {@code l}-suffixed ones are int64.
 * The recursive calls need the annotation, as their result type can't be
 * inferred otherwise. The return type of {@code main} stays unresolved and is
 * determined by System F inference.
 */
public class FibProgramTest {
  private static final Location LOC = Location.UNKNOWN;

  @BeforeAll
  public static void setup() {
    ConverterRegistry.registerDialect(SystemFInference.class);
    Dialect.registerAllDialects();
    FuncSystemFConversion.registerBuiltinSystemFConversion();
    BuiltinSystemFConversion.registerBuiltinSystemFConversion();
    ArithSystemFConversion.registerBuiltinSystemFConversion();
    CfSystemFConversion.registerBuiltinSystemFConversion();
  }

  private static CallOp callFib(Block block, Value argument) {
    return block.addOperation(new CallOp(LOC, "fib", List.of(argument), List.of(), MaybeType.of()));
  }

  private static ProgramOp buildFibProgram() {
    ProgramOp programOp = new ProgramOp(LOC);

    // (defn fib [a: int64] : int64 ...)
    FuncOp fib = programOp.addOperation(new FuncOp(
        LOC, "fib", FuncType.of(List.of(MaybeType.of((Type) IntegerT.INT64())), MaybeType.of((Type) IntegerT.INT64()))));
    Value a = fib.getRegion().getRegionValue(0).orElseThrow();

    Block thenBlock = fib.addBlock(new Block());
    Block elseBlock = fib.addBlock(new Block());

    // (<= a 1)
    Block entry = fib.getEntryBlock();
    var condOne = entry.addOperation(new ConstantOp(LOC, 1));
    var le = entry.addOperation(new BinaryOp(LOC, a, condOne.getResult(), BinMode.LE));
    entry.addOperation(new BranchCondOp(LOC, le.getResult(), thenBlock, elseBlock));

    // (return 1l)
    var thenOne = thenBlock.addOperation(new ConstantOp(LOC, 1L));
    thenBlock.addOperation(new ReturnOp(LOC, thenOne.getResult()));

    // (return (+ (fib (- a 1)) (fib (- a 2))))
    var one = elseBlock.addOperation(new ConstantOp(LOC, 1));
    var aMinusOne = elseBlock.addOperation(new BinaryOp(LOC, a, one.getResult(), BinMode.SUB));
    var fibOne = callFib(elseBlock, aMinusOne.getResult());
    var two = elseBlock.addOperation(new ConstantOp(LOC, 2));
    var aMinusTwo = elseBlock.addOperation(new BinaryOp(LOC, a, two.getResult(), BinMode.SUB));
    var fibTwo = callFib(elseBlock, aMinusTwo.getResult());
    var sum = elseBlock.addOperation(new BinaryOp(
        LOC, fibOne.getOutputValue().orElseThrow(), fibTwo.getOutputValue().orElseThrow(), BinMode.ADD));
    elseBlock.addOperation(new ReturnOp(LOC, sum.getResult()));

    // (defn main [] ...)
    FuncOp main = programOp.addOperation(new FuncOp(LOC, "main", FuncType.of(List.of(), MaybeType.of())));
    Block mainBody = main.getEntryBlock();
    addFibAssert(mainBody, 13L, 233L);
    addFibAssert(mainBody, 20L, 6765L);
    mainBody.addOperation(new ReturnOp(LOC));

    return programOp;
  }

  /** {@code (assert (= (fib n) expected))} */
  private static void addFibAssert(Block block, long n, long expected) {
    var argument = block.addOperation(new ConstantOp(LOC, n));
    var call = callFib(block, argument.getResult());
    var expectedValue = block.addOperation(new ConstantOp(LOC, expected));
    var eq = block.addOperation(new BinaryOp(
        LOC, call.getOutputValue().orElseThrow(), expectedValue.getResult(), BinMode.EQ));
    block.addOperation(new AssertOp(LOC, eq.getResult()));
  }

  /** With {@code (defn fib [a: int64] : int64 ...)} the recursive calls have a known result type. */
  @Test
  public void annotatedFibProgramInfersAndVerifies() {
    var solver = new SystemFInference().getNewSolverInstance();
    var program = buildFibProgram();
    var solved = solver.solve(ExprOrOperator.of(program.getOperation()));
    var rebuilt = solved.instantiated().getUnderlyingOperation().orElseThrow();

    assertTrue(rebuilt.verify(VerifyOptions.FULL_VERIFICATION), "inferred program must verify");

    var fib = ((ProgramOp) rebuilt.asOp()).getEntryBlock().getOperations().stream()
        .filter(op -> op.asOp() instanceof FuncOp func && func.getFuncName().equals("fib"))
        .map(op -> (FuncOp) op.asOp())
        .findFirst()
        .orElseThrow();
    assertEquals(MaybeType.of((Type) IntegerT.INT64()), fib.getType().getOutput().orElseThrow());
  }
}

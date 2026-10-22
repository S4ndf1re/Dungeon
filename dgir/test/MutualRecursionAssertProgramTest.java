import static dgir.dialect.arith.ArithOps.BinaryOp;
import static dgir.dialect.arith.ArithOps.ConstantOp;
import static dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;
import static dgir.dialect.builtin.BuiltinAttrs.IntegerAttribute;
import static dgir.dialect.builtin.BuiltinOps.ProgramOp;
import static dgir.dialect.builtin.BuiltinTypes.IntegerT;
import static dgir.dialect.builtin.BuiltinTypes.UnitType;
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
import dgir.core.ir.TypeUniquer;
import dgir.core.ir.Value;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.dialect.arith.ArithHMXConversion;
import dgir.dialect.builtin.BuiltinHMXConversion;
import dgir.dialect.cf.CfHMXConversion;
import dgir.dialect.func.FuncHMXConversion;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reconstructs the IR of the clojure program:
 *
 * <pre>
 * (defn foo [a] (if (&lt; a 10) (bar (+ a 1)) a))
 * (defn bar [a] (foo a))
 * (defn main []
 *   (assert (= (bar 0) 10))
 *   (assert (= (bar 10) 10))
 *   (assert (= (bar 11) 11)))
 * </pre>
 *
 * Function return types are left unresolved (as the front-end emits them) and
 * are determined by running HMX type inference over the program. The test
 * passes only if the inferred program verifies.
 */
public class MutualRecursionAssertProgramTest {
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

  @Test
  public void mutualRecursionAssertProgramInfersAndVerifies() {
    ProgramOp programOp = new ProgramOp(LOC);
    var int32 = IntegerT.INT32();

    // (defn foo [a] ...) — return type unresolved, like the front-end output
    FuncOp foo = programOp.addOperation(
        new FuncOp(LOC, "foo", FuncType.of(List.of(MaybeType.of()), MaybeType.of())));

    // (defn bar [a] (foo a))
    FuncOp bar = programOp.addOperation(
        new FuncOp(LOC, "bar", FuncType.of(List.of(MaybeType.of()), MaybeType.of())));

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
    var callBar = thenBlock.addOperation(new CallOp(LOC, "bar", List.of(add.getResult()), MaybeType.of()));
    thenBlock.addOperation(new ReturnOp(LOC, callBar.getOutputValue().orElseThrow()));

    // a
    elseBlock.addOperation(new ReturnOp(LOC, a));

    // (foo a)
    Value barA = bar.getRegion().getRegionValue(0).orElseThrow();
    var callFoo = bar.getEntryBlock().addOperation(new CallOp(LOC, "foo", List.of(barA), MaybeType.of()));
    bar.getEntryBlock().addOperation(new ReturnOp(LOC, callFoo.getOutputValue().orElseThrow()));

    // (defn main [] ...)
    FuncOp main = programOp.addOperation(
        new FuncOp(LOC, "main", FuncType.of(List.of(), TypeUniquer.uniqueInstance(new UnitType()))));
    Block mainBody = main.getEntryBlock();

    for (var n : new int[] {0, 10, 11}) {
      var arg = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(n, int32)));
      var call = mainBody.addOperation(new CallOp(LOC, "bar", List.of(arg.getResult()), MaybeType.of()));
      var expected = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(n, int32)));
      var eq = mainBody.addOperation(
          new BinaryOp(LOC, call.getOutputValue().orElseThrow(), expected.getResult(), BinMode.EQ));
      mainBody.addOperation(new AssertOp(LOC, eq.getResult()));
    }
    mainBody.addOperation(new ReturnOp(LOC));

    // Run HMX type inference and verify the inferred program.
    var solver = new HMXInference().getNewSolverInstance();
    var solved = solver.solve(ExprOrOperator.of(programOp.getOperation()));
    var rebuilt = solved.instantiated().getUnderlyingOperation().orElseThrow();

    assertTrue(rebuilt.verify(VerifyOptions.FULL_VERIFICATION),
        "inferred program must verify");
  }
}

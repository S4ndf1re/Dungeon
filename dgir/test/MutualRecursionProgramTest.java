import static dgir.dialect.arith.ArithOps.BinaryOp;
import static dgir.dialect.arith.ArithOps.ConstantOp;
import static dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;
import static dgir.dialect.builtin.BuiltinAttrs.IntegerAttribute;
import static dgir.dialect.builtin.BuiltinAttrs.UnitAttribute;
import static dgir.dialect.builtin.BuiltinOps.ProgramOp;
import static dgir.dialect.builtin.BuiltinTypes.IntegerT;
import static dgir.dialect.cf.CfOps.BranchCondOp;
import static dgir.dialect.func.FuncOps.CallOp;
import static dgir.dialect.func.FuncOps.FuncOp;
import static dgir.dialect.func.FuncOps.ReturnOp;
import static dgir.dialect.func.FuncTypes.FuncType;

import dgir.core.analysis.OperationVerifier.VerifyOptions;
import dgir.core.debug.Location;
import dgir.core.ir.Block;
import dgir.core.ir.Dialect;
import dgir.core.ir.TypeUniquer;
import dgir.core.ir.Value;
import dgir.dialect.builtin.BuiltinTypes.UnitType;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Reconstructs the IR of:
 *
 * <pre>
 * (defn foo [a] (if (< a 10) (bar (+ a 1)) ()))
 * (defn bar [a] (foo a))
 * (defn main [] (bar 0))
 * </pre>
 */
public class MutualRecursionProgramTest {
  private static final Location LOC = Location.UNKNOWN;

  @BeforeAll
  public static void setup() {
    Dialect.registerAllDialects();
  }

  @Test
  public void mutualRecursionProgramVerifies() {
    ProgramOp programOp = new ProgramOp(LOC);
    var unitT = TypeUniquer.uniqueInstance(new UnitType());
    var fooType = FuncType.of(List.of(IntegerT.INT32()), unitT);

    // (defn foo [a] ...)
    FuncOp foo = programOp.addOperation(new FuncOp(LOC, "foo", fooType));
    Value a = foo.getRegion().getRegionValue(0).orElseThrow();

    Block thenBlock = foo.addBlock(new Block());
    Block elseBlock = foo.addBlock(new Block());

    Block entry = foo.getEntryBlock();
    var ten = entry.addOperation(new ConstantOp(LOC, new IntegerAttribute(10, IntegerT.INT32())));
    var lt = entry.addOperation(new BinaryOp(LOC, a, ten.getResult(), BinMode.LT));
    entry.addOperation(new BranchCondOp(LOC, lt.getResult(), thenBlock, elseBlock));

    // (bar (+ a 1))
    var one = thenBlock.addOperation(new ConstantOp(LOC, new IntegerAttribute(1, IntegerT.INT32())));
    var add = thenBlock.addOperation(new BinaryOp(LOC, a, one.getResult(), BinMode.ADD));
    thenBlock.addOperation(new CallOp(LOC, "bar", List.of(add.getResult()), unitT));
    thenBlock.addOperation(new ReturnOp(LOC));

    // ()
    elseBlock.addOperation(new ConstantOp(LOC, new UnitAttribute()));
    elseBlock.addOperation(new ReturnOp(LOC));

    // (defn bar [a] (foo a))
    FuncOp bar = programOp.addOperation(new FuncOp(LOC, "bar", fooType));
    Value barA = bar.getRegion().getRegionValue(0).orElseThrow();
    Block barBody = bar.getEntryBlock();
    barBody.addOperation(new CallOp(LOC, "foo", List.of(barA), unitT));
    barBody.addOperation(new ReturnOp(LOC));

    // (defn main [] (bar 0))
    FuncOp main = programOp.addOperation(
        new FuncOp(LOC, "main", FuncType.of(List.of(), unitT)));
    Block mainBody = main.getEntryBlock();
    var zero = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(0, IntegerT.INT32())));
    mainBody.addOperation(new CallOp(LOC, "bar", List.of(zero.getResult()), unitT));
    mainBody.addOperation(new ReturnOp(LOC));

    org.junit.jupiter.api.Assertions.assertTrue(
        programOp.verify(VerifyOptions.FULL_VERIFICATION));
  }
}

import static dgir.dialect.arith.ArithOps.BinaryOp;
import static dgir.dialect.arith.ArithOps.ConstantOp;
import static dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;
import static dgir.dialect.builtin.BuiltinAttrs.IntegerAttribute;
import static dgir.dialect.builtin.BuiltinOps.ProgramOp;
import static dgir.dialect.builtin.BuiltinTypes.IntegerT;
import static dgir.dialect.cf.CfOps.AssertOp;
import static dgir.dialect.cf.CfOps.BranchCondOp;
import static dgir.dialect.cf.CfOps.BranchOp;
import static dgir.dialect.func.FuncOps.CallOp;
import static dgir.dialect.func.FuncOps.FuncOp;
import static dgir.dialect.func.FuncOps.ReturnOp;
import static dgir.dialect.func.FuncTypes.FuncType;

import dgir.core.ir.Dialect;
import dgir.core.ir.TypeUniquer;
import dgir.core.ir.Value;
import dgir.core.ir.types.Expression.ExpressionVisitor;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitGetChildrenOption;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.debug.Location;
import dgir.dialect.arith.ArithHMXConversion;
import dgir.dialect.builtin.BuiltinHMXConversion;
import dgir.dialect.builtin.BuiltinTypes.UnitType;
import dgir.dialect.cf.CfHMXConversion;
import dgir.dialect.func.FuncHMXConversion;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Recreates the EXACT operation tree of the clojure cyclic-function-test:
 *
 * <pre>
 * (defn foo [a] (if (&lt; a 10) (+ a 1) (- a 1)) (return a))
 * (defn bar [a] (return (foo a)))
 * (defn main [] (assert (= (bar 0) 10)) (assert (= (bar 10) 10)) (assert (= (bar 11) 11)))
 * </pre>
 *
 * foo's then/else blocks both cf.br to the SAME merge block (return a).
 * After HMX inference the merge block must exist exactly once.
 */
public class CyclicFunctionExactTreeTest {
  private static final Location LOC = Location.UNKNOWN;

  @BeforeEach
  public void setup() {
    ConverterRegistry.registerDialect(HMXInference.class);
    Dialect.registerAllDialects();
    FuncHMXConversion.registerBuiltinHMXConversion();
    BuiltinHMXConversion.registerBuiltinHMXConversion();
    ArithHMXConversion.registerBuiltinHMXConversion();
    CfHMXConversion.registerBuiltinHMXConversion();
  }

  @Test
  public void mergeBlockEmittedOnce() {
    ProgramOp programOp = new ProgramOp(LOC);
    var int32 = IntegerT.INT32();
    var unitT = TypeUniquer.uniqueInstance(new UnitType());

    // ---- foo ----
    FuncOp foo = programOp.addOperation(
        new FuncOp(LOC, "foo", FuncType.of(List.of(int32), int32)));
    Value a = foo.getRegion().getRegionValue(0).orElseThrow();

    var entry = foo.getEntryBlock();
    var thenBlock = foo.addBlock(new dgir.core.ir.Block());
    var elseBlock = foo.addBlock(new dgir.core.ir.Block());
    var mergeBlock = foo.addBlock(new dgir.core.ir.Block());

    var ten = entry.addOperation(new ConstantOp(LOC, new IntegerAttribute(10, int32)));
    var lt = entry.addOperation(new BinaryOp(LOC, a, ten.getResult(), BinMode.LT));
    entry.addOperation(new BranchCondOp(LOC, lt.getResult(), thenBlock, elseBlock));

    var oneThen = thenBlock.addOperation(new ConstantOp(LOC, new IntegerAttribute(1, int32)));
    thenBlock.addOperation(new BinaryOp(LOC, a, oneThen.getResult(), BinMode.ADD));
    thenBlock.addOperation(new BranchOp(LOC, mergeBlock));

    var oneElse = elseBlock.addOperation(new ConstantOp(LOC, new IntegerAttribute(1, int32)));
    elseBlock.addOperation(new BinaryOp(LOC, a, oneElse.getResult(), BinMode.SUB));
    elseBlock.addOperation(new BranchOp(LOC, mergeBlock));

    mergeBlock.addOperation(new ReturnOp(LOC, a));

    // ---- bar: (return (foo a)) ----
    FuncOp bar = programOp.addOperation(
        new FuncOp(LOC, "bar", FuncType.of(List.of(int32), int32)));
    Value barA = bar.getRegion().getRegionValue(0).orElseThrow();
    var callFoo = bar.getEntryBlock().addOperation(new CallOp(LOC, foo, barA));
    bar.getEntryBlock().addOperation(new ReturnOp(LOC, callFoo.getOutputValue().orElseThrow()));

    // ---- main: three (assert (= (bar N) N)) ----
    FuncOp main = programOp.addOperation(
        new FuncOp(LOC, "main", FuncType.of(List.of(), unitT)));
    var mainBody = main.getEntryBlock();
    for (var n : new int[] {0, 10, 11}) {
      var arg = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(n, int32)));
      var call = mainBody.addOperation(new CallOp(LOC, bar, arg.getResult()));
      var expected = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(n, int32)));
      var eq = mainBody.addOperation(new BinaryOp(LOC, call.getOutputValue().orElseThrow(), expected.getResult(), BinMode.EQ));
      mainBody.addOperation(new AssertOp(LOC, eq.getResult()));
    }
    mainBody.addOperation(new ReturnOp(LOC));

    var solver = new HMXInference().getNewSolverInstance();
    var solved = solver.solve(ExprOrOperator.of(programOp.getOperation()));

    var rebuilt = solved.instantiated().getUnderlyingOperation().orElseThrow();
    var returnOps = new IdentityHashMap<dgir.core.ir.Operation, Boolean>();
    var blockCount = new int[] {0};
    collectReturns(rebuilt, returnOps, blockCount, new IdentityHashMap<>());

    System.out.println("Rebuilt IR blocks visited: " + blockCount[0]);
    System.out.println("Distinct func.return operations in rebuilt IR: " + returnOps.size());
    for (var block : rebuilt.getRegions().get(0).getBlocks()) {
      for (var fn : block.getOperations()) {
        if (!fn.getRegions().isEmpty()) {
          System.out.println("  FUNC " + fn.getDetails().ident() + " "
              + fn.getAttributesMap().values().stream()
                  .filter(na -> na.getName().contains("symbol"))
                  .map(na -> na.getAttributeOrThrow().getStorage().toString())
                  .toList());
          for (var region : fn.getRegions()) {
            for (var b : region.getBlocks()) {
              System.out.println("    " + b + " ops=["
                  + b.getOperations().stream()
                      .map(o -> o.getDetails().ident()
                          + (o.getDetails().ident().equals("func.return")
                              ? "@" + System.identityHashCode(o)
                              : ""))
                      .reduce((x, y) -> x + ", " + y).orElse("") + "]");
            }
          }
        }
      }
    }

    org.junit.jupiter.api.Assertions.assertEquals(3, returnOps.size(),
        "expected one return per function (foo merge, bar, main) — more means the "
            + "merge block was duplicated");
  }

  private static void collectReturns(
      dgir.core.ir.Operation op,
      IdentityHashMap<dgir.core.ir.Operation, Boolean> returns,
      int[] blockCount,
      IdentityHashMap<dgir.core.ir.Block, Boolean> seenBlocks) {
    for (var region : op.getRegions()) {
      for (var block : region.getBlocks()) {
        if (seenBlocks.putIfAbsent(block, Boolean.TRUE) != null) {
          continue;
        }
        blockCount[0]++;
        for (var child : block.getOperations()) {
          if (child.getDetails().ident().equals("func.return")) {
            returns.put(child, Boolean.TRUE);
          }
          collectReturns(child, returns, blockCount, seenBlocks);
        }
      }
    }
  }
}

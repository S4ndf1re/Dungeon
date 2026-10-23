import static dgir.dialect.arith.ArithOps.BinaryOp;
import static dgir.dialect.arith.ArithOps.ConstantOp;
import static dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;
import static dgir.dialect.builtin.BuiltinAttrs.IntegerAttribute;
import static dgir.dialect.builtin.BuiltinOps.ProgramOp;
import static dgir.dialect.builtin.BuiltinTypes.IntegerT;
import static dgir.dialect.cf.CfOps.BranchCondOp;
import static dgir.dialect.cf.CfOps.BranchOp;
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
 * Mirrors the clojure program:
 *
 * <pre>
 * (defn foo [a] (if (&lt; a 10) (+ a 1) (- a 1)) a)   -- then/else merge into one return block
 * </pre>
 *
 * IR shape: entry --br_cond--> [then, else]; then --cf.br--> merge;
 * else --cf.br--> merge; merge: func.return a.
 *
 * The merge block is referenced by TWO different cf.br terminators. After
 * inference + reconstruction it must exist exactly ONCE.
 */
public class MergeBlockDedupTest {
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
  public void sharedMergeBlockIsEmittedOnce() {
    ProgramOp programOp = new ProgramOp(LOC);
    var int32 = IntegerT.INT32();

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

    // The ProgramOp conversion inserts a synthetic `call main`; main must exist.
    FuncOp main = programOp.addOperation(
        new FuncOp(LOC, "main", FuncType.of(List.of(), TypeUniquer.uniqueInstance(new UnitType()))));
    var callArg = main.getEntryBlock().addOperation(
        new ConstantOp(LOC, new IntegerAttribute(1, int32)));
    var callResult = main.getEntryBlock().addOperation(
        new dgir.dialect.func.FuncOps.CallOp(LOC, "foo", List.of(callArg.getResult()),
            FuncType.of(List.of(int32), int32)));
    main.getEntryBlock().addOperation(new ReturnOp(LOC));

    var solver = new HMXInference().getNewSolverInstance();
    var solved = solver.solve(ExprOrOperator.of(programOp.getOperation()));

    var returnOps = new IdentityHashMap<dgir.core.ir.Operation, Boolean>();
    new ExpressionVisitor<HMXExpr, dgir.core.ir.types.builtin.hmx.HMXType>(
        VisitOrder.POST_ORDER, VisitGetChildrenOption.ALL_CHILDREN)
        .visit(solved.instantiated(), e -> {
          e.getUnderlyingOperation().ifPresent(op -> {
            if (op.getDetails().ident().equals("func.return")) {
              returnOps.put(op, Boolean.TRUE);
            }
          });
        });

    System.out.println("Distinct func.return operations reachable: " + returnOps.size());

    // NOTE: one return per function — foo's merge block return and main's own
    // return. The merge block is referenced by TWO cf.br terminators, so a
    // duplicated merge block would surface as THREE or more distinct
    // func.return operations here.
    org.junit.jupiter.api.Assertions.assertEquals(2, returnOps.size(),
        "expected one return per function (foo merge, main) — more means the "
            + "merge block was duplicated");
  }
}

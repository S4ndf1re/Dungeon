import static dgir.dialect.arith.ArithOps.ConstantOp;
import static dgir.dialect.builtin.BuiltinOps.ProgramOp;
import static dgir.dialect.cf.CfOps.BranchCondOp;
import static dgir.dialect.func.FuncOps.FuncOp;
import static dgir.dialect.func.FuncOps.ReturnOp;
import static dgir.dialect.func.FuncTypes.FuncType;


import dgir.core.ir.Dialect;
import dgir.core.ir.Operation;
import dgir.core.ir.TypeUniquer;
import dgir.core.ir.types.Expression.ExpressionVisitor;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitGetChildrenOption;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.expr.Block;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.debug.Location;
import dgir.dialect.arith.ArithHMXConversion;
import dgir.dialect.builtin.BuiltinHMXConversion;
import dgir.dialect.builtin.BuiltinTypes.UnitType;
import dgir.dialect.cf.CfHMXConversion;
import dgir.dialect.func.FuncHMXConversion;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Both arms of the if branch to the SAME target block FB (return unit).
 * After inference + reconstruction there must be exactly ONE func.return
 * operation and ONE block expression for FB — no duplicated emission.
 */
public class SharedBranchTargetDedupTest {
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
  public void sharedBranchTargetIsEmittedOnce() {
    ProgramOp programOp = new ProgramOp(LOC);
    var unitT = TypeUniquer.uniqueInstance(new UnitType());
    // NOTE: no parameters — inference drops unused function parameters, which
    // would desync the reconstructed region values. Irrelevant to block dedup.
    var mainType = FuncType.of(List.of(), unitT);

    FuncOp main = programOp.addOperation(new FuncOp(LOC, "main", mainType));

    dgir.core.ir.Block fb = main.addBlock(new dgir.core.ir.Block());
    dgir.core.ir.Block entry = main.getEntryBlock();

    // NOTE: a constant condition keeps this test focused on block dedup; the
    // a < 10 comparison is irrelevant to whether FB is emitted twice.
    var cond = entry.addOperation(new ConstantOp(LOC, true));
    entry.addOperation(new BranchCondOp(LOC, cond.getResult(), fb, fb));

    fb.addOperation(new ReturnOp(LOC));

    var solver = new HMXInference().getNewSolverInstance();
    var solved = solver.solve(ExprOrOperator.of(programOp.getOperation()));

    // Walk the instantiated expression tree and collect statistics.
    var blockExprs = new ArrayList<Block>();
    var returnOps = new IdentityHashMap<Operation, Boolean>();
    var seenExprs = new IdentityHashMap<HMXExpr, Boolean>();
    new ExpressionVisitor<HMXExpr, dgir.core.ir.types.builtin.hmx.HMXType>(
        VisitOrder.POST_ORDER, VisitGetChildrenOption.ALL_CHILDREN)
        .visit(solved.instantiated(), e -> {
          seenExprs.putIfAbsent(e, Boolean.TRUE);
          if (e instanceof Block blockExpr) {
            blockExprs.add(blockExpr);
          }
          e.getUnderlyingOperation().ifPresent(op -> {
            if (op.getDetails().ident().equals("func.return")) {
              returnOps.put(op, Boolean.TRUE);
            }
          });
        });

    System.out.println("Distinct block expr instances reached: " + distinctCount(blockExprs));
    System.out.println("Distinct func.return operations reachable: " + returnOps.size());
    for (var b : blockExprs) {
      System.out.println("  Block@" + System.identityHashCode(b)
          + " type=" + b.getInferredType()
          + " underlying=" + b.getUnderlyingOperation().map(o -> System.identityHashCode(o)).orElse(-1));
    }

    org.junit.jupiter.api.Assertions.assertEquals(1, returnOps.size(),
        "FB block was duplicated: multiple func.return operations in the tree");
  }

  private static int distinctCount(List<Block> blocks) {
    var seen = new IdentityHashMap<Block, Boolean>();
    blocks.forEach(b -> seen.put(b, Boolean.TRUE));
    return seen.size();
  }
}

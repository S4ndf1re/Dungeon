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

import dgir.core.ir.Block;
import dgir.core.ir.Dialect;
import dgir.core.ir.Operation;
import dgir.core.ir.TypeUniquer;
import dgir.core.ir.Value;
import dgir.core.ir.types.Expression.ExpressionVisitor;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitGetChildrenOption;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.debug.Location;
import dgir.dialect.arith.ArithAlgoWConversion;
import dgir.dialect.arith.ArithHMXConversion;
import dgir.dialect.builtin.BuiltinAlgoWConversion;
import dgir.dialect.builtin.BuiltinHMXConversion;
import dgir.dialect.cf.CfAlgoWConversion;
import dgir.dialect.cf.CfHMXConversion;
import dgir.dialect.func.FuncAlgoWConversion;
import dgir.dialect.func.FuncHMXConversion;
import dgir.dialect.builtin.BuiltinTypes.UnitType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Builds the mutual-recursion foo/bar/main program and prints instantiated-tree
 * op counts per dialect.
 *
 * <p>
 * Dedup design (user-specified): {@code Subst.expand} keeps the fresh-variable
 * scheme instantiation and unification, but returns the scheme-local subst with
 * deref'd entries (no composition with the caller chain). The composed subst
 * differed per caller path, so {@code Pair.of(expr, subst)} memo keys never hit
 * and callee bodies were re-instantiated per occurrence (bar x3, foo x2).
 * Memo hits share instances, so {@code convertProgramOp} dedupes global ops by
 * identity. Counts must stay: funcs=4, calls=4, constants=4, bins=2.
 */

public class MutualRecursionDuplicationTest {
  private static final Location LOC = Location.UNKNOWN;

  @BeforeEach
  public void setup() {
    ConverterRegistry.registerDialect(AlgorithmWInference.class);
    ConverterRegistry.registerDialect(HMXInference.class);
    Dialect.registerAllDialects();
    FuncAlgoWConversion.registerBuiltinAlgoWConversion();
    BuiltinAlgoWConversion.registerBuiltinAlgoWConversion();
    ArithAlgoWConversion.registerBuiltinAlgoWConversion();
    CfAlgoWConversion.registerBuiltinAlgoWConversion();
    FuncHMXConversion.registerBuiltinHMXConversion();
    BuiltinHMXConversion.registerBuiltinHMXConversion();
    ArithHMXConversion.registerBuiltinHMXConversion();
    CfHMXConversion.registerBuiltinHMXConversion();
  }

  private static ProgramOp buildProgram() {
    ProgramOp programOp = new ProgramOp(LOC);
    var unitT = TypeUniquer.uniqueInstance(new UnitType());
    var fnType = FuncType.of(List.of(IntegerT.INT32()), unitT);

    FuncOp foo = programOp.addOperation(new FuncOp(LOC, "foo", fnType));
    Value a = foo.getRegion().getRegionValue(0).orElseThrow();
    Block thenBlock = foo.addBlock(new Block());
    Block elseBlock = foo.addBlock(new Block());
    Block entry = foo.getEntryBlock();
    var ten = entry.addOperation(new ConstantOp(LOC, new IntegerAttribute(10, IntegerT.INT32())));
    var lt = entry.addOperation(new BinaryOp(LOC, a, ten.getResult(), BinMode.LT));
    entry.addOperation(new BranchCondOp(LOC, lt.getResult(), thenBlock, elseBlock));
    var one = thenBlock.addOperation(new ConstantOp(LOC, new IntegerAttribute(1, IntegerT.INT32())));
    var add = thenBlock.addOperation(new BinaryOp(LOC, a, one.getResult(), BinMode.ADD));
    thenBlock.addOperation(new CallOp(LOC, "bar", List.of(add.getResult()), unitT));
    thenBlock.addOperation(new ReturnOp(LOC));
    elseBlock.addOperation(new ConstantOp(LOC, new UnitAttribute()));
    elseBlock.addOperation(new ReturnOp(LOC));

    FuncOp bar = programOp.addOperation(new FuncOp(LOC, "bar", fnType));
    Value barA = bar.getRegion().getRegionValue(0).orElseThrow();
    Block barBody = bar.getEntryBlock();
    barBody.addOperation(new CallOp(LOC, "foo", List.of(barA), unitT));
    barBody.addOperation(new ReturnOp(LOC));

    FuncOp main = programOp.addOperation(new FuncOp(LOC, "main", FuncType.of(List.of(), unitT)));
    Block mainBody = main.getEntryBlock();
    var zero = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(0, IntegerT.INT32())));
    mainBody.addOperation(new CallOp(LOC, "bar", List.of(zero.getResult()), unitT));
    mainBody.addOperation(new ReturnOp(LOC));
    return programOp;
  }

  private record Counts(long funcs, long calls, long constants, long bins) {}

  private Counts count(Object instantiated, Class<?> exprClass) {
    List<Operation> ops = new ArrayList<>();
    if (exprClass == Expr.class) {
      new ExpressionVisitor<Expr, dgir.core.ir.types.builtin.algorithmw.AlgorithmWType>(
          VisitOrder.POST_ORDER, VisitGetChildrenOption.ALL_CHILDREN)
          .visit((Expr) instantiated, e -> e.getUnderlyingOperation().ifPresent(ops::add));
    } else {
      new ExpressionVisitor<HMXExpr, dgir.core.ir.types.builtin.hmx.HMXType>(
          VisitOrder.POST_ORDER, VisitGetChildrenOption.ALL_CHILDREN)
          .visit((HMXExpr) instantiated, e -> e.getUnderlyingOperation().ifPresent(ops::add));
    }
    return new Counts(
        ops.stream().filter(o -> o.getDetails().ident().equals("func.func")).count(),
        ops.stream().filter(o -> o.getDetails().ident().equals("func.call")).count(),
        ops.stream().filter(o -> o.getDetails().ident().equals("arith.constant")).count(),
        ops.stream().filter(o -> o.getDetails().ident().equals("arith.bin")).count());
  }

  @Test
  public void hmxMutualRecursionDuplication() {
    var hmxSolver = new HMXInference().getNewSolverInstance();
    var hmx = hmxSolver.solve(ExprOrOperator.of(buildProgram().getOperation()));
    System.out.println("HMX: " + count(hmx.instantiated(), HMXExpr.class));
    DgirTestUtils.saveDotExpr(hmx.instantiated());
  }
}

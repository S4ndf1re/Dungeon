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
import dgir.core.ir.types.Expression.ExpressionVisitor;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitGetChildrenOption;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.dialect.arith.ArithHMXConversion;
import dgir.dialect.builtin.BuiltinTypes.UnitType;
import dgir.dialect.cf.CfHMXConversion;
import dgir.dialect.func.FuncHMXConversion;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reconstructs the dsl program:
 *
 * <pre>
 * (defn foo [a : integer]
 *   (if (&lt; a 10)
 *     (return (&lt; a 10))
 *     (return (&gt;= a 10))))
 * (defn bar [a : integer] (return (foo a)))
 * (defn main []
 *   (assert (= (bar 0) false))
 *   (assert (= (bar 10l) 10)))
 * </pre>
 *
 * {@code bar} is {@code integer}-annotated and called with an int32 (0) and
 * an int64 (10l) literal — both must check against the width-less
 * {@code integer} bound, and the equality determines must converge per call
 * site.
 */
public class MixedWidthAnnotationProgramTest {
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

  @Test
  public void mixedWidthAnnotatedCallersInfersAndVerifies() {
    ProgramOp programOp = new ProgramOp(LOC);
    var int32 = IntegerT.INT32();
    var int64 = IntegerT.INT64();

    // (defn foo [a : integer] (if (< a 10) (return (+ a 1)) (return a)))
    var fooType = FuncType.of(List.of(MaybeType.of(integerT())), MaybeType.of(integerT()));
    FuncOp foo = programOp.addOperation(new FuncOp(LOC, "foo", fooType));

    Value a = foo.getRegion().getRegionValue(0).orElseThrow();
    Block thenBlock = foo.addBlock(new Block());
    Block elseBlock = foo.addBlock(new Block());

    Block entry = foo.getEntryBlock();
    var ten = entry.addOperation(new ConstantOp(LOC, new IntegerAttribute(10, int32)));
    var lt = entry.addOperation(new BinaryOp(LOC, a, ten.getResult(), BinMode.LT));
    entry.addOperation(new BranchCondOp(LOC, lt.getResult(), thenBlock, elseBlock));

    // (< a 10)
    var thenTen = thenBlock.addOperation(new ConstantOp(LOC, new IntegerAttribute(10, int32)));
    var thenLt = thenBlock.addOperation(new BinaryOp(LOC, a, thenTen.getResult(), BinMode.LT));
    thenBlock.addOperation(new ReturnOp(LOC, a));

    // (>= a 10)
    var elseTen = elseBlock.addOperation(new ConstantOp(LOC, new IntegerAttribute(10, int32)));
    var elseGe = elseBlock.addOperation(new BinaryOp(LOC, a, elseTen.getResult(), BinMode.GE));
    elseBlock.addOperation(new ReturnOp(LOC, a));

    // (defn bar [a : integer] (return (foo a)))
    FuncOp bar = programOp.addOperation(new FuncOp(LOC, "bar", fooType));
    Value barA = bar.getRegion().getRegionValue(0).orElseThrow();
    var callFoo = bar.getEntryBlock().addOperation(
        new CallOp(LOC, "foo", List.of(barA), MaybeType.of()));
    bar.getEntryBlock().addOperation(new ReturnOp(LOC, callFoo.getOutputValue().orElseThrow()));

    // (defn main [] ...)
    FuncOp main = programOp.addOperation(
        new FuncOp(LOC, "main", FuncType.of(List.of(), TypeUniquer.uniqueInstance(new UnitType()))));
    Block mainBody = main.getEntryBlock();

    // (assert (= (bar 0) 1))
    var zero = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(0, int32)));
    var callBarInt = mainBody.addOperation(
        new CallOp(LOC, "bar", List.of(zero.getResult()), MaybeType.of()));
    var one = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(1, int32)));
    var eqInt = mainBody.addOperation(
        new BinaryOp(LOC, callBarInt.getOutputValue().orElseThrow(), one.getResult(), BinMode.EQ));
    mainBody.addOperation(new AssertOp(LOC, eqInt.getResult()));

    // (assert (= (bar 10l) 10))
    var tenL = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(10, int64)));
    var callBarLong = mainBody.addOperation(
        new CallOp(LOC, "bar", List.of(tenL.getResult()), MaybeType.of()));
    var ten64 = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(10, int64)));
    var eqLong = mainBody.addOperation(
        new BinaryOp(LOC, callBarLong.getOutputValue().orElseThrow(), ten64.getResult(), BinMode.EQ));
    mainBody.addOperation(new AssertOp(LOC, eqLong.getResult()));

    mainBody.addOperation(new ReturnOp(LOC));

    var solver = new HMXInference().getNewSolverInstance();
    var solved = solver.solve(ExprOrOperator.of(programOp.getOperation()));
    var rebuilt = solved.instantiated().getUnderlyingOperation().orElseThrow();

    assertTrue(rebuilt.verify(VerifyOptions.FULL_VERIFICATION),
        "inferred program must verify");
  }

  /**
   * Direct IR equivalent of the dsl {@code subtyped-annotation-function-test}:
   *
   * <pre>
   * (defn foo [a : integer]
   *   (if (&lt; a 10)
   *     (return (&lt; a 10))
   *     (return (&gt;= a 10))))
   * (defn bar [a : integer] (return (foo a)))
   * (defn main []
   *   (assert (= (bar 0) false))
   *   (assert (= (bar 10l) true)))
   * </pre>
   *
   * Return types are unannotated and {@code foo} returns {@code bool}, so the
   * result of {@code bar} does not depend on the argument width.
   */
  @Test
  public void dslSubtypedAnnotationFunctionTest() {
    ProgramOp programOp = new ProgramOp(LOC);
    var int32 = IntegerT.INT32();
    var int64 = IntegerT.INT64();

    var fooType = FuncType.of(List.of(MaybeType.of(integerT())), MaybeType.of());
    FuncOp foo = programOp.addOperation(new FuncOp(LOC, "foo", fooType));

    Value a = foo.getRegion().getRegionValue(0).orElseThrow();
    Block thenBlock = foo.addBlock(new Block());
    Block elseBlock = foo.addBlock(new Block());

    Block entry = foo.getEntryBlock();
    var ten = entry.addOperation(new ConstantOp(LOC, new IntegerAttribute(10, int32)));
    var lt = entry.addOperation(new BinaryOp(LOC, a, ten.getResult(), BinMode.LT));
    entry.addOperation(new BranchCondOp(LOC, lt.getResult(), thenBlock, elseBlock));

    // (return (< a 10))
    var thenTen = thenBlock.addOperation(new ConstantOp(LOC, new IntegerAttribute(10, int32)));
    var thenLt = thenBlock.addOperation(new BinaryOp(LOC, a, thenTen.getResult(), BinMode.LT));
    thenBlock.addOperation(new ReturnOp(LOC, thenLt.getResult()));

    // (return (>= a 10))
    var elseTen = elseBlock.addOperation(new ConstantOp(LOC, new IntegerAttribute(10, int32)));
    var elseGe = elseBlock.addOperation(new BinaryOp(LOC, a, elseTen.getResult(), BinMode.GE));
    elseBlock.addOperation(new ReturnOp(LOC, elseGe.getResult()));

    // (defn bar [a : integer] (return (foo a)))
    FuncOp bar = programOp.addOperation(new FuncOp(LOC, "bar", fooType));
    Value barA = bar.getRegion().getRegionValue(0).orElseThrow();
    var callFoo = bar.getEntryBlock().addOperation(
        new CallOp(LOC, "foo", List.of(barA), MaybeType.of()));
    bar.getEntryBlock().addOperation(new ReturnOp(LOC, callFoo.getOutputValue().orElseThrow()));

    // (defn main [] ...)
    FuncOp main = programOp.addOperation(
        new FuncOp(LOC, "main", FuncType.of(List.of(), MaybeType.of())));
    Block mainBody = main.getEntryBlock();

    // (assert (= (bar 0) false))
    var zero = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(0, int32)));
    var callBarInt = mainBody.addOperation(
        new CallOp(LOC, "bar", List.of(zero.getResult()), MaybeType.of()));
    var no = mainBody.addOperation(new ConstantOp(LOC, false));
    var eqInt = mainBody.addOperation(
        new BinaryOp(LOC, callBarInt.getOutputValue().orElseThrow(), no.getResult(), BinMode.EQ));
    mainBody.addOperation(new AssertOp(LOC, eqInt.getResult()));

    // (assert (= (bar 10l) true))
    var tenL = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(10, int64)));
    var callBarLong = mainBody.addOperation(
        new CallOp(LOC, "bar", List.of(tenL.getResult()), MaybeType.of()));
    var yes = mainBody.addOperation(new ConstantOp(LOC, true));
    var eqLong = mainBody.addOperation(
        new BinaryOp(LOC, callBarLong.getOutputValue().orElseThrow(), yes.getResult(), BinMode.EQ));
    mainBody.addOperation(new AssertOp(LOC, eqLong.getResult()));

    mainBody.addOperation(new ReturnOp(LOC));

    var solver = new HMXInference().getNewSolverInstance();
    var solved = solver.solve(ExprOrOperator.of(programOp.getOperation()));
    var rebuilt = solved.instantiated().getUnderlyingOperation().orElseThrow();

    assertTrue(rebuilt.verify(VerifyOptions.FULL_VERIFICATION),
        "inferred program must verify");
  }

  /**
   * The same instance of a function reached on two paths:
   *
   * <pre>
   * (defn foo [a : integer] (return (&lt; a 10)))
   * (defn bar [a : integer] (return (foo a)))
   * (defn main [] (bar 0) (foo 0))
   * </pre>
   *
   * {@code foo} is instantiated at {@code int32} through {@code bar} and
   * directly from {@code main}. Both uses denote the same instance, so it must
   * be emitted once and stay intact, no matter how many call sites reach it.
   */
  @Test
  public void sameInstanceReachedFromTwoCallers() {
    ProgramOp programOp = new ProgramOp(LOC);
    var int32 = IntegerT.INT32();
    var fooType = FuncType.of(List.of(MaybeType.of(integerT())), MaybeType.of());

    // (defn foo [a : integer] (return (< a 10)))
    FuncOp foo = programOp.addOperation(new FuncOp(LOC, "foo", fooType));
    Value a = foo.getRegion().getRegionValue(0).orElseThrow();
    var ten = foo.getEntryBlock().addOperation(new ConstantOp(LOC, new IntegerAttribute(10, int32)));
    var lt = foo.getEntryBlock().addOperation(new BinaryOp(LOC, a, ten.getResult(), BinMode.LT));
    foo.getEntryBlock().addOperation(new ReturnOp(LOC, lt.getResult()));

    // (defn bar [a : integer] (return (foo a)))
    FuncOp bar = programOp.addOperation(new FuncOp(LOC, "bar", fooType));
    Value barA = bar.getRegion().getRegionValue(0).orElseThrow();
    var callFoo = bar.getEntryBlock().addOperation(
        new CallOp(LOC, "foo", List.of(barA), MaybeType.of()));
    bar.getEntryBlock().addOperation(new ReturnOp(LOC, callFoo.getOutputValue().orElseThrow()));

    // (defn main [] (bar 0) (foo 0))
    FuncOp main = programOp.addOperation(
        new FuncOp(LOC, "main", FuncType.of(List.of(), MaybeType.of())));
    Block mainBody = main.getEntryBlock();
    var zero = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(0, int32)));
    mainBody.addOperation(new CallOp(LOC, "bar", List.of(zero.getResult()), MaybeType.of()));
    var zero2 = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(0, int32)));
    mainBody.addOperation(new CallOp(LOC, "foo", List.of(zero2.getResult()), MaybeType.of()));
    mainBody.addOperation(new ReturnOp(LOC));

    var solver = new HMXInference().getNewSolverInstance();
    var solved = solver.solve(ExprOrOperator.of(programOp.getOperation()));
    var rebuilt = solved.instantiated().getUnderlyingOperation().orElseThrow();

    assertTrue(rebuilt.verify(VerifyOptions.FULL_VERIFICATION),
        "inferred program must verify");

    // Both uses denote foo(int32): a second copy would still verify, so count.
    var fooFunctions = new java.util.ArrayList<Object>();
    new ExpressionVisitor<HMXExpr, HMXType>(VisitOrder.POST_ORDER, VisitGetChildrenOption.ALL_CHILDREN)
        .visit(solved.instantiated(), e -> e.getUnderlyingOperation().ifPresent(o -> {
          if (o.asOp() instanceof FuncOp f && "foo".equals(f.getFuncName())) {
            fooFunctions.add(o);
          }
        }));
    assertEquals(1, fooFunctions.size(), "foo(int32) must be emitted once");
  }

  /**
   * An internal type that depends on the instance but does not occur in the
   * function's type:
   *
   * <pre>
   * (defn foo [a : integer]
   *   (let [b (+ a 1)]
   *     (return (&lt; b 10))))
   * (defn main [] (foo 0) (foo 10l))
   * </pre>
   *
   * {@code foo : integer -> bool}, so {@code b} is not visible in the type, yet
   * its width follows {@code a}: each instance must add at its own width.
   */
  @Test
  public void instanceDependentInternalType() {
    ProgramOp programOp = new ProgramOp(LOC);
    var int32 = IntegerT.INT32();
    var int64 = IntegerT.INT64();
    var fooType = FuncType.of(List.of(MaybeType.of(integerT())), MaybeType.of());

    // (defn foo [a : integer] (let [b (+ a 1)] (return (< b 10))))
    FuncOp foo = programOp.addOperation(new FuncOp(LOC, "foo", fooType));
    Value a = foo.getRegion().getRegionValue(0).orElseThrow();
    Block body = foo.getEntryBlock();
    var one = body.addOperation(new ConstantOp(LOC, new IntegerAttribute(1, int32)));
    var b = body.addOperation(new BinaryOp(LOC, a, one.getResult(), BinMode.ADD));
    var ten = body.addOperation(new ConstantOp(LOC, new IntegerAttribute(10, int32)));
    var lt = body.addOperation(new BinaryOp(LOC, b.getResult(), ten.getResult(), BinMode.LT));
    body.addOperation(new ReturnOp(LOC, lt.getResult()));

    // (defn main [] (foo 0) (foo 10l))
    FuncOp main = programOp.addOperation(
        new FuncOp(LOC, "main", FuncType.of(List.of(), MaybeType.of())));
    Block mainBody = main.getEntryBlock();
    var zero = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(0, int32)));
    mainBody.addOperation(new CallOp(LOC, "foo", List.of(zero.getResult()), MaybeType.of()));
    var tenL = mainBody.addOperation(new ConstantOp(LOC, new IntegerAttribute(10, int64)));
    mainBody.addOperation(new CallOp(LOC, "foo", List.of(tenL.getResult()), MaybeType.of()));
    mainBody.addOperation(new ReturnOp(LOC));

    var solver = new HMXInference().getNewSolverInstance();
    var solved = solver.solve(ExprOrOperator.of(programOp.getOperation()));
    var rebuilt = solved.instantiated().getUnderlyingOperation().orElseThrow();

    assertTrue(rebuilt.verify(VerifyOptions.FULL_VERIFICATION),
        "inferred program must verify");

    var fooInstances = new java.util.ArrayList<FuncOp>();
    new ExpressionVisitor<HMXExpr, HMXType>(VisitOrder.POST_ORDER, VisitGetChildrenOption.ALL_CHILDREN)
        .visit(solved.instantiated(), e -> e.getUnderlyingOperation().ifPresent(o -> {
          if (o.asOp() instanceof FuncOp f && "foo".equals(f.getFuncName())) {
            fooInstances.add(f);
          }
        }));
    assertEquals(2, fooInstances.size(), "one foo per argument width");

    for (var instance : fooInstances) {
      var paramType = instance.getRegion().getRegionValue(0).orElseThrow().getType();
      var add = instance.getEntryBlock().getOperations().stream()
          .map(op -> op.asOp())
          .filter(op -> op instanceof BinaryOp bin && bin.getMode() == BinMode.ADD)
          .map(op -> (BinaryOp) op)
          .findFirst().orElseThrow();
      assertEquals(paramType, add.getResult().getType(),
          "the internal addition must have the instance's width");
    }
  }
}

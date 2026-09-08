import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dgir.core.ir.Dialect;
import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.types.Expression.ExpressionVisitor;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitGetChildrenOption;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.SystemFConversionUtils;
import dgir.core.ir.types.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.algorithmw.Expr;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.systemf.SystemFInference;
import dgir.core.ir.types.systemf.SystemFType;
import dgir.core.debug.Location;
import dgir.dialect.arith.ArithAlgoWConversion;
import dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;
import dgir.dialect.arith.ArithOps.BinaryOp;
import dgir.dialect.arith.ArithOps.ConstantOp;
import dgir.dialect.arith.ArithSystemFConversion;
import dgir.dialect.builtin.BuiltinAlgoWConversion;
import dgir.dialect.builtin.BuiltinOps.IdOp;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.builtin.BuiltinSystemFConversion;
import dgir.dialect.cf.CfAlgoWConversion;
import dgir.dialect.cf.CfOps.AssertOp;
import dgir.dialect.cf.CfSystemFConversion;
import dgir.dialect.func.FuncAlgoWConversion;
import dgir.dialect.func.FuncOps.FuncOp;
import dgir.dialect.func.FuncOps.ReturnOp;
import dgir.dialect.func.FuncSystemFConversion;

/**
 * Validates that the System F conversions mirror the Algorithm W conversions:
 * the same IR program, solved through both dialects, must produce the same
 * reconstructed operations and the same solved type.
 */
public class ConversionParityTest {
  static final Location LOC = Location.UNKNOWN;

  @BeforeEach
  public void setup() {
    ConverterRegistry.registerDialect(AlgorithmWInference.class);
    ConverterRegistry.registerDialect(SystemFInference.class);
    Dialect.registerAllDialects();
    FuncAlgoWConversion.registerBuiltinAlgoWConversion();
    BuiltinAlgoWConversion.registerBuiltinAlgoWConversion();
    ArithAlgoWConversion.registerBuiltinAlgoWConversion();
    CfAlgoWConversion.registerBuiltinAlgoWConversion();
    FuncSystemFConversion.registerBuiltinSystemFConversion();
    BuiltinSystemFConversion.registerBuiltinSystemFConversion();
    ArithSystemFConversion.registerBuiltinSystemFConversion();
    CfSystemFConversion.registerBuiltinSystemFConversion();
  }

  private record SolvedResult(String solvedType, Map<String, Integer> opCounts) {
  }

  private static SolvedResult solveAlgoW(ProgramOp programOp) {
    var inference = new AlgorithmWInference();
    var solver = inference.getSolverInstance();
    var solvedPair = solver.solve(ExprOrOperator.of(programOp.getOperation()));

    List<Operation> ops = new ArrayList<>();
    new ExpressionVisitor<Expr, dgir.core.ir.types.algorithmw.AlgorithmWType>(
        VisitOrder.POST_ORDER, VisitGetChildrenOption.ALL_CHILDREN)
        .visit(solvedPair.getRight(), e -> e.getUnderlyingOperation().ifPresent(ops::add));

    var gpnt = solvedPair.getLeft().asTypeParameter().getConcrete();
    return new SolvedResult(gpnt.toString(), countOps(ops));
  }

  private static SolvedResult solveSystemF(ProgramOp programOp) {
    var inference = new SystemFInference();
    var solver = inference.getSolverInstance();
    var solvedPair = solver.solve(ExprOrOperator.of(programOp.getOperation()));

    List<Operation> ops = new ArrayList<>();
    new ExpressionVisitor<dgir.core.ir.types.systemf.Expr, SystemFType>(
        VisitOrder.POST_ORDER, VisitGetChildrenOption.ALL_CHILDREN)
        .visit(solvedPair.getRight(), e -> e.getUnderlyingOperation().ifPresent(ops::add));

    var solvedType = solvedPair.getLeft();
    GeneralParameterizedNominalType gpnt = null;
    if (solvedType instanceof SystemFType.Lit) {
      gpnt = SystemFConversionUtils.systemFTypeToGeneralNominal((SystemFType) solvedType);
    }

    return new SolvedResult(gpnt != null ? gpnt.toString() : solvedType.toString(), countOps(ops));
  }

  private static Map<String, Integer> countOps(List<Operation> ops) {
    var counts = new HashMap<String, Integer>();
    for (var op : ops) {
      counts.merge(op.asOp().getIdent(), 1, Integer::sum);
    }
    return counts;
  }

  private static void assertParity(Supplier<ProgramOp> programBuilder) {
    var algoW = solveAlgoW(programBuilder.get());
    var systemF = solveSystemF(programBuilder.get());

    assertEquals(algoW.solvedType(), systemF.solvedType(),
        "solved types diverge between Algorithm W and System F");
    assertEquals(algoW.opCounts(), systemF.opCounts(),
        "reconstructed operations diverge between Algorithm W and System F");
  }

  @Test
  public void constantReturnParity() {
    assertParity(() -> {
      ProgramOp programOp = new ProgramOp(LOC);
      FuncOp funcOp = programOp.addOperation(new FuncOp(LOC, "main"));
      var numberOp = funcOp.addOperation(new ConstantOp(LOC, 42), 0);
      funcOp.addOperation(new ReturnOp(LOC, numberOp.getResult()), 0);
      return programOp;
    });
  }

  @Test
  public void identityChainParity() {
    assertParity(() -> {
      ProgramOp programOp = new ProgramOp(LOC);
      FuncOp funcOp = programOp.addOperation(new FuncOp(LOC, "main"));
      var textOp = funcOp.addOperation(new ConstantOp(LOC, "Hello World!"), 0);
      var idOp = funcOp.addOperation(new IdOp(LOC, textOp.getResult()), 0);
      funcOp.addOperation(new ReturnOp(LOC, idOp.getResult()), 0);
      return programOp;
    });
  }

  @Test
  public void binaryOpParity() {
    assertParity(() -> {
      ProgramOp programOp = new ProgramOp(LOC);
      FuncOp funcOp = programOp.addOperation(new FuncOp(LOC, "main"));
      var lhs = funcOp.addOperation(new ConstantOp(LOC, 10), 0);
      var rhs = funcOp.addOperation(new ConstantOp(LOC, 20), 0);
      var addOp = funcOp
          .addOperation(new BinaryOp(LOC, lhs.getResult(), rhs.getResult(), BinMode.ADD), 0);
      funcOp.addOperation(new ReturnOp(LOC, addOp.getResult()), 0);
      return programOp;
    });
  }

  @Test
  public void assertParityTest() {
    assertParity(() -> {
      ProgramOp programOp = new ProgramOp(LOC);
      FuncOp funcOp = programOp.addOperation(new FuncOp(LOC, "main"));
      var condOp = funcOp.addOperation(new ConstantOp(LOC, true), 0);
      funcOp.addOperation(new AssertOp(LOC, condOp.getResult()), 0);
      funcOp.addOperation(new ReturnOp(LOC), 0);
      return programOp;
    });
  }

  @Test
  public void constantReturnUnitParity() {
    assertParity(() -> {
      ProgramOp programOp = new ProgramOp(LOC);
      FuncOp funcOp = programOp.addOperation(new FuncOp(LOC, "main"));
      funcOp.addOperation(new ConstantOp(LOC, "unused"), 0);
      funcOp.addOperation(new ReturnOp(LOC), 0);
      return programOp;
    });
  }

}

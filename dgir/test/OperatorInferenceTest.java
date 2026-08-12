
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dgir.core.ir.Dialect;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.AlgorithmWType;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.builtin.BuiltinAlgoWConversion;
import dgir.dialect.builtin.BuiltinOps.IdOp;
import dgir.dialect.func.FuncOps.ReturnOp;
import dgir.dialect.func.FuncAlgoWConversion;
import dgir.dialect.func.FuncOps.FuncOp;
import dgir.dialect.arith.ArithAlgoWConversion;
import dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;
import dgir.dialect.arith.ArithOps;
import dgir.dialect.arith.ArithOps.ConstantOp;
import dgir.core.debug.Location;

public class OperatorInferenceTest {
  static final Location LOC = Location.UNKNOWN;

  @BeforeAll
  public static void setup() {
    ConverterRegistry.registerDialect(AlgorithmWInference.class);
    Dialect.registerAllDialects();
    FuncAlgoWConversion.registerBuiltinAlgoWConversion();
    BuiltinAlgoWConversion.registerBuiltinAlgoWConversion();
    ArithAlgoWConversion.registerBuiltinAlgoWConversion();
  }

  @Test
  public void simpleFunctionInference() {
    var inference = new AlgorithmWInference();
    var solver = inference.getSolverInstance();

    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMainOp = entry.getRight();

    var textOp = funcMainOp.addOperation(new ConstantOp(LOC, "Hello World!"), 0);
    var numberOp = funcMainOp.addOperation(new ConstantOp(LOC, 42), 0);
    var idOp = funcMainOp.addOperation(new IdOp(LOC, textOp.getResult()), 0);
    funcMainOp.addOperation(new IdOp(LOC, numberOp.getResult()), 0);
    funcMainOp.addOperation(new ReturnOp(LOC, idOp.getResult()), 0);

    var solved = solver.solve(ExprOrOperator.of(programOp.getOperation()));
    assert solved instanceof AlgorithmWType;
    assert solved instanceof AlgorithmWType.Arrow;
    assert ((AlgorithmWType.Arrow) solved).from instanceof AlgorithmWType.LitType;
    assert ((AlgorithmWType.LitType) ((AlgorithmWType.Arrow) solved).from).tyName.equals(TypeIdent.TYPE_IDENT_UNIT);

    assert ((AlgorithmWType.Arrow) solved).to instanceof AlgorithmWType.LitType;
    assert ((AlgorithmWType.LitType) ((AlgorithmWType.Arrow) solved).to).tyName.equals(TypeIdent.from("string"));
  }

  @Test
  public void simpleBinOpInference() {
    var inference = new AlgorithmWInference();
    var solver = inference.getSolverInstance();

    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMainOp = entry.getRight();

    var lhsNumber = funcMainOp.addOperation(new ConstantOp(LOC, 10), 0);
    var rhsNumber = funcMainOp.addOperation(new ConstantOp(LOC, 20), 0);
    var addOp = funcMainOp
        .addOperation(new ArithOps.BinaryOp(LOC, lhsNumber.getResult(), rhsNumber.getResult(), BinMode.ADD), 0);
    funcMainOp.addOperation(new ReturnOp(LOC, addOp.getResult()), 0);

    var solved = solver.solve(ExprOrOperator.of(programOp.getOperation()));
    assert solved instanceof AlgorithmWType;
    assert solved instanceof AlgorithmWType.Arrow;
    assert ((AlgorithmWType.Arrow) solved).from instanceof AlgorithmWType.LitType;
    assert ((AlgorithmWType.LitType) ((AlgorithmWType.Arrow) solved).from).tyName.equals(TypeIdent.TYPE_IDENT_UNIT);

    assert ((AlgorithmWType.Arrow) solved).to instanceof AlgorithmWType.LitType;
    assert ((AlgorithmWType.LitType) ((AlgorithmWType.Arrow) solved).to).tyName.equals(TypeIdent.from("int32"));
  }

  @Test
  public void simpleBinOpInference2() {
    var inference = new AlgorithmWInference();
    var solver = inference.getSolverInstance();

    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMainOp = entry.getRight();

    var lhsNumber = funcMainOp.addOperation(new ConstantOp(LOC, (int) 10), 0);
    var rhsNumber = funcMainOp.addOperation(new ConstantOp(LOC, (long) 20), 0);
    var addOp = funcMainOp
        .addOperation(new ArithOps.BinaryOp(LOC, lhsNumber.getResult(), rhsNumber.getResult(), BinMode.ADD), 0);
    funcMainOp.addOperation(new ReturnOp(LOC, addOp.getResult()), 0);

    var solved = solver.solve(ExprOrOperator.of(programOp.getOperation()));
    assert solved instanceof AlgorithmWType;
    assert solved instanceof AlgorithmWType.Arrow;
    assert ((AlgorithmWType.Arrow) solved).from instanceof AlgorithmWType.LitType;
    assert ((AlgorithmWType.LitType) ((AlgorithmWType.Arrow) solved).from).tyName.equals(TypeIdent.TYPE_IDENT_UNIT);

    assert ((AlgorithmWType.Arrow) solved).to instanceof AlgorithmWType.LitType;
    assert ((AlgorithmWType.LitType) ((AlgorithmWType.Arrow) solved).to).tyName.equals(TypeIdent.from("int64"));
  }
}

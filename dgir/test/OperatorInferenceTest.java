
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
    var numberTextOP = funcMainOp.addOperation(new ConstantOp(LOC, 42), 0);
    var idOp = funcMainOp.addOperation(new IdOp(LOC, textOp.getResult()), 0);
    funcMainOp.addOperation(new IdOp(LOC, numberTextOP.getResult()), 0);
    funcMainOp.addOperation(new ReturnOp(LOC, idOp.getResult()), 0);

    var solved = solver.solve(ExprOrOperator.of(programOp.getOperation()));
    assert solved instanceof AlgorithmWType;
    assert solved instanceof AlgorithmWType.LitType;
    assert ((AlgorithmWType.LitType) solved).tyName.equals(TypeIdent.from("string"));
  }
}

import dgir.core.ir.Dialect;
import dgir.core.debug.Location;
import dgir.core.ir.types.Expression.ExpressionVisitor;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitGetChildrenOption;
import dgir.core.ir.types.Expression.ExpressionVisitor.VisitOrder;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.systemf.Expr;
import dgir.core.ir.types.systemf.SystemFInference;
import dgir.core.ir.types.systemf.SystemFType;
import dgir.dialect.arith.ArithOps.ConstantOp;
import dgir.dialect.arith.ArithSystemFConversion;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.builtin.BuiltinSystemFConversion;
import dgir.dialect.func.FuncOps.FuncOp;
import dgir.dialect.func.FuncOps.ReturnOp;
import dgir.dialect.func.FuncSystemFConversion;
import org.junit.jupiter.api.Test;

public class DebugTmpTest {
  static final Location LOC = Location.UNKNOWN;

  @Test
  public void dbg() {
    ConverterRegistry.registerDialect(SystemFInference.class);
    Dialect.registerAllDialects();
    FuncSystemFConversion.registerBuiltinSystemFConversion();
    BuiltinSystemFConversion.registerBuiltinSystemFConversion();
    ArithSystemFConversion.registerBuiltinSystemFConversion();

    ProgramOp programOp = new ProgramOp(Location.UNKNOWN);
    FuncOp funcOp = programOp.addOperation(new FuncOp(Location.UNKNOWN, "main"));
    var numberOp = funcOp.addOperation(new ConstantOp(LOC, 42), 0);
    funcOp.addOperation(new ReturnOp(LOC, numberOp.getResult()), 0);

    var solver = new SystemFInference().getSolverInstance();
    var solved = solver.solve(ExprOrOperator.of(programOp.getOperation()));
    Expr root = solved.getRight();
    new ExpressionVisitor<Expr, SystemFType>(VisitOrder.POST_ORDER, VisitGetChildrenOption.ALL_CHILDREN)
        .visit(root, e -> {
          if (e.getUnderlyingOperation().isPresent()) {
            System.err.println("LIT @" + System.identityHashCode(e)
                + " ty=" + e.getInferredType()
                + " ps=" + e.getParentScopeExpr().map(p -> System.identityHashCode(p)).orElse(-1)
                + " pos=" + e.getParentScopePosition().map(Object::toString).orElse("-")
                + " eqOther? self-op=" + System.identityHashCode(e.getUnderlyingOperation().get()));
          }
        });
  }
}

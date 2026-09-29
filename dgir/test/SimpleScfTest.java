import static dgir.dialect.arith.ArithOps.ConstantOp;
import static dgir.dialect.scf.ScfOps.*;

import dgir.core.analysis.OperationVerifier.VerifyOptions;
import dgir.core.debug.Location;
import dgir.core.ir.Dialect;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Simple test to debug SCF operations */
public class SimpleScfTest {
  static final Location LOC = Location.UNKNOWN;

  @BeforeAll
  public static void setup() {
    Dialect.registerAllDialects();
  }

  @Test
  public void testScopeOpDirectly() {
    ScopeOp scopeOp = new ScopeOp(LOC);
    scopeOp.getRegion().getEntryBlock().addOperation(new ContinueOp(LOC));

    // ScopeOp's implicit terminator is EndOp, so ContinueOp is an invalid terminator here.
    assertFalse(scopeOp.verify(VerifyOptions.FULL_VERIFICATION));
    assertTrue(scopeOp.getRegion().getEntryBlock().hasTerminator());
  }

  @Test
  public void testIfOpDirectly() {
    var condOp = new ConstantOp(LOC, true);
    IfOp ifOp = new IfOp(LOC, condOp.getResult(), false);

    ifOp.getThenRegion().getEntryBlock().addOperation(new ContinueOp(LOC));

    assertFalse(ifOp.verify(VerifyOptions.FULL_VERIFICATION));
    assertTrue(ifOp.getThenRegion().getEntryBlock().hasTerminator());
  }

  @Test
  public void testForOpDirectly() {
    var initValue = new ConstantOp(LOC, 0);
    var lowerBound = new ConstantOp(LOC, 0);
    var upperBound = new ConstantOp(LOC, 10);
    var step = new ConstantOp(LOC, 1);

    ForOp forOp = new ForOp(
        LOC,
        initValue.getResult(),
        lowerBound.getResult(),
        upperBound.getResult(),
        step.getResult());

    forOp.getRegion().getEntryBlock().addOperation(new ContinueOp(LOC));

    // ContinueOp is the loop back-edge terminator and is valid inside ForOp.
    assertTrue(forOp.verify(VerifyOptions.FULL_VERIFICATION));
    assertTrue(forOp.getRegion().getEntryBlock().hasTerminator());
  }
}

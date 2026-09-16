package dgir.core.ir.types.builtin.hmx;

import java.util.List;

import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Symbol;

public abstract class HMXExpr extends Expression<HMXExpr, HMXType> {

  @Override
  public List<HMXExpr> getChildren() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'getChildren'");
  }

  @Override
  public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'replaceSymbol'");
  }

  @Override
  public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'containsSymbol'");
  }

  @Override
  public HMXExpr copy() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'copy'");
  }

}

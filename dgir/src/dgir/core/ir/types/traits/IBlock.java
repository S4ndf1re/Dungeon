package dgir.core.ir.types.traits;

import java.util.HashMap;

import dgir.core.ir.Block;
import dgir.core.ir.Op;
import dgir.core.ir.Operation;
import dgir.core.ir.Region;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Type;

public interface IBlock<E extends Expression<E, T>, T extends Type<T>> {
  public HashMap<Object, Block> convertedGeneralBlock = new HashMap<>();

  public E getInnerExpression();

  public default Block asBlock() {
    if (IBlock.convertedGeneralBlock.containsKey(this)) {
      return IBlock.convertedGeneralBlock.get(this);
    }

    var innerExpr = this.getInnerExpression();

    var resultBlock = new Block();
    innerExpr.fillBlockScoped(resultBlock);

    IBlock.convertedGeneralBlock.put(this, resultBlock);

    return resultBlock;
  }

  public default Block fillEntryBlockInRegion(Region region) {
    var block = this.asBlock();
    region.removeBlockAt(0);
    region.addBlockAt(0, block);
    return block;
  }

  public default Block fillEntryBlockInOp(Op op, int regionIdx) {
    var region = op.getRegion(regionIdx).orElseThrow();
    return this.fillEntryBlockInRegion(region);
  }

  public default Block fillEntryBlockInOp(Operation op, int regionIdx) {
    var region = op.getRegion(regionIdx).orElseThrow();
    return this.fillEntryBlockInRegion(region);
  }

}

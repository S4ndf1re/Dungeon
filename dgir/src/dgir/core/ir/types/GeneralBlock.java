package dgir.core.ir.types;

import java.util.ArrayList;
import java.util.List;

import dgir.core.ir.Block;
import dgir.core.ir.Operation;

/**
 * A general list of {@link Operation}s that will be transformed into a nested
 * structure of let bindings.
 */
public class GeneralBlock {
  private ArrayList<Operation> operations;

  public GeneralBlock() {
    this.operations = new ArrayList<>();
  }

  public void addOperation(Operation op) {
    this.operations.add(op);
  }

  public static GeneralBlock fromBlock(Block block) {
    GeneralBlock genBlock = new GeneralBlock();

    for (var op : block.getOperations()) {
      genBlock.addOperation(op);
    }

    return genBlock;
  }

  public List<Operation> getOperations() {
    return List.copyOf(this.operations);
  }

}

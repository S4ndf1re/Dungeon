package dgir.core.traits;

import java.util.List;

import dgir.core.ir.Operation;
import dgir.core.ir.Region;

public interface IIsPermeableTerminator extends IOpTrait{

  public static boolean verify(Operation op) {
    return true;
  }

  public List<Region> getPermeableRegion();
}

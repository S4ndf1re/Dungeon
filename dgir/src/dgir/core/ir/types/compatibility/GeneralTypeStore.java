package dgir.core.ir.types.compatibility;

import java.util.HashSet;

public class GeneralTypeStore {

  private HashSet<GeneralType> registerd;

  public GeneralTypeStore() {
    this.registerd = new HashSet<>();
  }

  /**
   * Register a new general type, like "Int"
   * @param type the {@link GeneralType} to register
   */
  public void register(GeneralType type) {
    this.registerd.add(type);
  }

  /**
   * Deregister a new general type, like "Int"
   * @param type the {@link GeneralType} to register
   */
  public void deregister(GeneralType type) {
    this.registerd.remove(type);
  }

  /**
   * Validate the type hierarchy for all registered Types
   */
  public void validateHierarchy() {
    for (var type : this.registerd) {
      type.validateHierarchy();
    }
  }
}

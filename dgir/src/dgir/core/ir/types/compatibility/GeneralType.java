package dgir.core.ir.types.compatibility;

import java.util.ArrayList;
import java.util.List;

/**
 * Represent a general Type
 * The long goal is that only one instance may exist for a concrete general
 * type.
 * This instance would be stored and managed within the {@link GeneralTypeStore}
 */
public abstract class GeneralType {

  /**
   * this is a collection of all direct supertypes. I.e. if we have the types
   * int64 and int32 both implement int. Int could have the additional supertype
   * Numeric
   */
  private ArrayList<GeneralType> registeredSuperTypes;

  /**
   * Collection of all directly registered subtypes
   */
  private ArrayList<GeneralType> registeredSubTypes;

  public GeneralType() {
    registeredSuperTypes = new ArrayList<>();
    registeredSubTypes = new ArrayList<>();
  }

  /**
   * Check if this is a subtype of other
   *
   * @param other the possible supertype
   * @return true if this is a subtype of other
   */
  public boolean isSubtypeOf(GeneralType other) {
    // Reference equality check, as references of general types are unique
    if (this == other) {
      return true;
    }

    for (var entry : registeredSuperTypes) {
      if (entry.isSubtypeOf(other)) {
        return true;
      }
    }

    return false;
  }

  public List<GeneralType> getSuperTypes(GeneralType other) {
    return List.copyOf(registeredSuperTypes);
  }

  public List<GeneralType> getSubType(GeneralType other) {
    return List.copyOf(registeredSubTypes);
  }

  public void registerSuperType(GeneralType superType) {
    this.registeredSuperTypes.add(superType);
    superType.registeredSubTypes.add(this);
  }

  public void deregisterSuperType(GeneralType superType) {
    this.registeredSuperTypes.remove(superType);
    superType.registeredSubTypes.remove(this);
  }

  /**
   * Simple validation of the type hierarchy.
   * Generally, this validation checks when entry is a supertype of this, this
   * must be a subtype of entry.
   * This check is then concluded vice versa, i.e. when this is a supertype of
   * entry, entry must also be a subtype of this.
   *
   * <p>
   * This method is not called recursively. The full validation is covered by the
   * {@link GeneralTypeStore#validateHierarchy()} method.
   */
  public void validateHierarchy() {
    for (var entry : registeredSuperTypes) {
      if (!this.isSubtypeOf(entry)) {
        throw new IllegalStateException(
            "Supertype " + entry + " is not a subtype of " + this);
      }
    }

    for (var entry : registeredSubTypes) {
      if (!entry.isSubtypeOf(this)) {
        throw new IllegalStateException(
            "Subtype " + entry + " is not a subtype of " + this);
      }
    }
  }
}

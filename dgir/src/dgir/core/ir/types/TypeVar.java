package dgir.core.ir.types;

/**
 * TypeVar is marker for a unique type variable.
 * This type variable only exists, to circumvent the need for de brujin indizes
 * or string variable names.
 * This is analogous to the Value class of the DGIR, but for types.
 */
public class TypeVar {

  private static long counter;

  private long idx;

  public TypeVar() {
    this.idx = TypeVar.counter++;
  }

  @Override
  public String toString() {
    return "t" + idx;
  }

}

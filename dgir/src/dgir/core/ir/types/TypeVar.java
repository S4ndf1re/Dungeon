package dgir.core.ir.types;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import dgir.core.ir.Value;

/**
 * Marker class representing a unique type variable.
 *
 * <p>
 * This class eliminates the need for de Bruijn indices or string-based variable
 * names by
 * assigning each instance a globally unique numeric identifier. It is analogous
 * to the
 * {@code Value} class but operates at the type level rather than the value
 * level.
 *
 * <p>
 * Type variables are created automatically with unique indices via a global
 * counter. They can
 * be tracked within logical scopes using {@link TypeVarScope} for cleanup or
 * analysis purposes.
 */
public class TypeVar {

  private final Optional<Value> boundValue;

  /**
   * A scope for tracking type variables created within a logical context.
   *
   * <p>
   * This class implements {@link AutoCloseable} to support try-with-resources
   * patterns for
   * automatic scope management. When a scope is open, all newly created
   * {@link TypeVar}
   * instances are automatically registered with it.
   *
   * <p>
   * Use {@link TypeVar#addScope()} to create and register a new scope, and either
   * call
   * {@link #close()} manually or use try-with-resources for automatic cleanup.
   */
  public static final class TypeVarScope implements AutoCloseable {
    private ArrayList<TypeVar> createdVars;

    /**
     * Constructs a new, empty type variable scope.
     */
    public TypeVarScope() {
      this.createdVars = new ArrayList<>();
    }

    /**
     * Returns an immutable list of all type variables created within this scope.
     *
     * @return an unmodifiable list containing all {@link TypeVar} instances
     *         registered with
     *         this scope
     */
    public List<TypeVar> createdVars() {
      return List.copyOf(createdVars);
    }

    /**
     * Registers a type variable with this scope.
     *
     * @param var the type variable to track
     */
    public void addCreated(TypeVar var) {
      createdVars.add(var);
    }

    /**
     * Closes this scope by removing it from the global set of open scopes.
     *
     * <p>
     * After closing, newly created type variables will no longer be registered with
     * this
     * scope. This method is idempotent and safe to call multiple times.
     *
     * @throws Exception never thrown; present to satisfy the {@link AutoCloseable}
     *                   interface
     */
    @Override
    public void close() throws Exception {
      TypeVar.removeScope(this);
    }

  }

  private static long counter;
  private static HashSet<TypeVarScope> openScopes = new HashSet<>();

  private long idx;

  /**
   * Creates a new type variable with a unique numeric identifier.
   *
   * <p>
   * The identifier is assigned from a global counter, ensuring uniqueness across
   * all type
   * variables. The new variable is automatically registered with all currently
   * open scopes
   * (see {@link #addScope()}).
   */
  public TypeVar() {
    this(null);
  }

  public TypeVar(Symbol value) {
    this.idx = TypeVar.counter++;
    if (value != null && value.isValue()) {
      this.boundValue = Optional.ofNullable(value.getValue());
    } else {
      this.boundValue = Optional.empty();
    }
    openScopes.forEach(scope -> scope.addCreated(this));
  }

  /**
   * Returns the string representation of this type variable.
   *
   * <p>
   * The format is "t" followed by the unique numeric index (e.g., "t0", "t42").
   *
   * @return the string representation of this type variable
   */
  @Override
  public String toString() {
    return "t" + idx;
  }

  /**
   * Creates and registers a new type variable scope.
   *
   * <p>
   * All {@link TypeVar} instances created after this call and before the scope is
   * closed
   * will be automatically registered with the returned scope. This enables
   * tracking of type
   * variables introduced during specific operations (e.g., type inference for an
   * expression).
   *
   * <p>
   * Example usage with try-with-resources:
   *
   * <pre>{@code
   * try (var scope = TypeVar.addScope()) {
   *   // TypeVars created here are tracked by 'scope'
   *   TypeVar v = new TypeVar();
   *   List<TypeVar> created = scope.createdVars(); // contains v
   * }
   * }</pre>
   *
   * @return a new, registered {@link TypeVarScope}
   */
  public static TypeVarScope addScope() {
    var scope = new TypeVarScope();
    openScopes.add(scope);
    return scope;
  }

  /**
   * Removes a scope from the global set of open scopes.
   *
   * <p>
   * After removal, newly created type variables will no longer be registered with
   * the
   * given scope. This method is idempotent.
   *
   * @param scope the scope to remove
   */
  public static void removeScope(TypeVarScope scope) {
    openScopes.remove(scope);
  }

  public void provideSolution(Type type) {
    if (this.boundValue.isPresent()) {
      // TODO(jan): set type here
      // this.boundValue.get().setType(type);
    }
  }

}

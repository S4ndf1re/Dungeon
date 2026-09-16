package dgir.core.ir.types;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.apache.commons.lang3.tuple.Pair;

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
public class TypeVar<T extends Type<T>> {

  private TypeVar<T> parent;
  private Optional<T> assignedType;
  private int level;

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
  public static final class TypeVarScope<T extends Type<T>> implements AutoCloseable {
    private ArrayList<TypeVar<T>> createdVars;

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
    public List<TypeVar<T>> createdVars() {
      return List.copyOf(createdVars);
    }

    /**
     * Registers a type variable with this scope.
     *
     * @param var the type variable to track
     */
    public void addCreated(TypeVar<T> var) {
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
  private static HashSet<TypeVarScope<?>> openScopes = new HashSet<>();

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
  @SuppressWarnings("unchecked")
  public TypeVar() {
    this.idx = TypeVar.counter++;
    openScopes.forEach(scope -> ((TypeVarScope<T>) scope).addCreated(this));
    this.parent = this;
    this.assignedType = Optional.empty();
    this.level = -1;
  }

  @SuppressWarnings("unchecked")
  public TypeVar(int level) {
    this.idx = TypeVar.counter++;
    openScopes.forEach(scope -> ((TypeVarScope<T>) scope).addCreated(this));
    this.parent = this;
    this.assignedType = Optional.empty();
    this.level = level;
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
  public static <T extends Type<T>> TypeVarScope<T> addScope() {
    var scope = new TypeVarScope<T>();
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
  public static <T extends Type<T>> void removeScope(TypeVarScope<T> scope) {
    openScopes.remove(scope);
  }

  public TypeVar<T> find() {
    var current = this;
    while (current != current.parent) {
      /// flatten the union find tree
      current.parent = current.parent.parent;
      current = current.parent;
    }

    return current;
  }

  public static int mergeLevel(int l1, int l2) {
    if (l1 < 0 || l2 < 0) {
      throw new IllegalArgumentException("One of the levels is onbound: " + l1 + " " + l2);
    }
    return Math.min(l1, l2);
  }

  public Optional<Pair<T, T>> unify(TypeVar<T> other) {
    if (this == other) {
      return Optional.empty();
    }

    BiConsumer<TypeVar<T>, TypeVar<T>> merge = (v1, v2) -> {
      var v = v1;
      if (v1.assignedType.isEmpty() && v2.assignedType.isPresent()) {
        return;
      }

      v.setLevel(mergeLevel(v1.level, v2.level));
    };

    Optional<Pair<T, T>> toUnify = Optional.empty();
    if (this.assignedType.isPresent() && other.assignedType.isPresent()) {
      toUnify = Optional.of(Pair.of(this.assignedType.get(), other.assignedType.get()));
    }

    if (this.assignedType.isPresent() && other.assignedType.isEmpty()) {
      this.assignedType.get().occursCheckAjustLevel(other);
    } else if (this.assignedType.isEmpty() && other.assignedType.isPresent()) {
      other.assignedType.get().occursCheckAjustLevel(this);
    }

    var thisRoot = this.find();
    var otherRoot = other.find();
    // Union Find common representant
    merge.accept(thisRoot, otherRoot);

    if (thisRoot.assignedType.isEmpty()) {
      thisRoot.assignedType = otherRoot.getAssigendType();
    }
    other.parent = this;

    return toUnify;
  }

  public void assignType(T type) {
    this.find().assignedType = Optional.ofNullable(type);
  }

  public Optional<T> getAssigendType() {
    return this.find().assignedType;
  }

  public int getLevel() {
    return this.find().level;
  }

  public void setLevel(int level) {
    this.find().level = level;
  }

}

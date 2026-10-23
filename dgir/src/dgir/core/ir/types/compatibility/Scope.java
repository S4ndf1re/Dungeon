package dgir.core.ir.types.compatibility;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dgir.core.ir.types.Type;

public final class Scope<T extends Type<T>> {

  public static abstract class ScopeLike<T extends Type<T>> {
    ArrayList<Scope<T>> scopeStack;

    protected ScopeLike() {
      this.scopeStack = new ArrayList<>();
    }

    protected ScopeLike(List<Scope<T>> scopeStack) {
      this.scopeStack = new ArrayList<>(scopeStack);
    }

    protected ScopeLike(ScopeLike<T> scopeLike) {
      this(scopeLike.scopeStack);
    }

    public Scope<T> addScope() {
      var scope = new Scope<T>();
      this.scopeStack.add(scope);
      return scope;
    }

    public Optional<Scope<T>> popScope() {
      return Optional.ofNullable(this.scopeStack.removeLast());
    }

    public Optional<Scope<T>> topScope() {
      return Optional.ofNullable(this.scopeStack.getLast());
    }
  }

  private ArrayList<T> returnTypes;
  // The result type of the function this scope belongs to, if any. Returns
  // constrain their value against it at the place they occur.
  private Optional<T> returnType;

  public Scope() {
    this.returnTypes = new ArrayList<>();
    this.returnType = Optional.empty();
  }

  public Optional<T> getReturnType() {
    return this.returnType;
  }

  public void setReturnType(T returnType) {
    this.returnType = Optional.of(returnType);
  }

  public List<T> getAllReturnTypesInScope() {
    return List.copyOf(this.returnTypes);
  }

  public void addReturnType(T retType) {
    this.returnTypes.add(retType);
  }
}

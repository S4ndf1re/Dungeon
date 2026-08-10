package dgir.core.ir.types.compatibility;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dgir.core.ir.types.Type;

public final class Scope<T extends Type> {

  public static abstract class ScopeLike<T extends Type> {
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

  public Scope() {
    this.returnTypes = new ArrayList<>();
  }

  public List<T> getAllReturnTypesInScope() {
    return List.copyOf(this.returnTypes);
  }

  public void addReturnType(T retType) {
    this.returnTypes.add(retType);
  }
}

package dgir.core.ir.types;

import dgir.core.ir.Value;

public sealed abstract class Symbol<E extends Expression<T>, T extends Type> {

  public static <E extends Expression<T>, T extends Type> Symbol<E, T> of(String name) {
    return new Symbol.StringSymbol<E, T>(name);
  }

  public static <E extends Expression<T>, T extends Type> Symbol<E, T> of(Value val) {
    return new Symbol.ValueSymbol<E, T>(val);
  }

  public boolean isValue() {
    return this instanceof ValueSymbol;
  }

  public Value getValue() {
    if (this.isValue()) {
      return ((ValueSymbol<E, T>) this).value;
    }
    throw new RuntimeException("Symbol is not of type Value");
  }

  public boolean isString() {
    return this instanceof StringSymbol;
  }

  public String getString() {
    if (this.isString()) {
      return ((StringSymbol<E, T>) this).value;
    }
    throw new RuntimeException("Symbol is not of type String");
  }

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  public static final class ValueSymbol<E extends Expression<T>, T extends Type> extends Symbol<E, T> {
    private final Value value;

    public ValueSymbol(Value value) {
      this.value = value;
    }

    public Value get() {
      return this.value;
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ValueSymbol sym && this.value.equals(sym.value);
    }
  }

  public static final class StringSymbol<E extends Expression<T>, T extends Type> extends Symbol<E, T> {
    private final String value;

    public StringSymbol(String value) {
      this.value = value;
    }

    public String get() {
      return this.value;
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof StringSymbol sym && this.value.equals(sym.value);
    }

  }

}

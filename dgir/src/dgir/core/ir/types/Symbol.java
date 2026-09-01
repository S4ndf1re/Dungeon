package dgir.core.ir.types;

import dgir.core.ir.Value;
import dgir.core.traits.ISymbol.SymbolTableSymbol;

public sealed abstract class Symbol<E extends Expression<E, T>, T extends Type> {

  public static <E extends Expression<E, T>, T extends Type> Symbol<E, T> of(SymbolTableSymbol name) {
    return new Symbol.TableSymbol<E, T>(name);
  }

  public static <E extends Expression<E, T>, T extends Type> Symbol<E, T> of(Value val) {
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

  public boolean isTableSymbol() {
    return this instanceof TableSymbol;
  }

  public SymbolTableSymbol getTableSymbol() {
    if (this.isTableSymbol()) {
      return ((TableSymbol<E, T>) this).value;
    }
    throw new RuntimeException("Symbol is not of type String");
  }

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  public static final class ValueSymbol<E extends Expression<E, T>, T extends Type> extends Symbol<E, T> {
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

  public static final class TableSymbol<E extends Expression<E, T>, T extends Type> extends Symbol<E, T> {
    private final SymbolTableSymbol value;

    public TableSymbol(SymbolTableSymbol value) {
      this.value = value;
    }

    public SymbolTableSymbol get() {
      return this.value;
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof TableSymbol sym && this.value.equals(sym.value);
    }

  }

}

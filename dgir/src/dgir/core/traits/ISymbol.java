package dgir.core.traits;

import dgir.core.ir.Operation;
import dgir.core.ir.SymbolTable;
import dgir.core.ir.Type;
import dgir.dialect.builtin.BuiltinAttrs;
import dgir.dialect.func.FuncOps;
import dgir.dialect.str.StrAttrs;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Marks an operation as declaring a named symbol that can be looked up via
 * {@link SymbolTable}.
 *
 * <p>
 * The implementing op must carry an attribute named
 * {@link SymbolTable#getSymbolAttributeName()}
 * (i.e. {@code "symbol_name"}) that holds the symbol's string name as a {@link
 * StrAttrs.StringAttribute}.
 *
 * <p>
 * The verifier checks that the attribute is present. {@link #getSymbol()} is a
 * convenience
 * accessor that reads the attribute value.
 *
 * <p>
 * Examples: {@link FuncOps.FuncOp}.
 */
public interface ISymbol extends IOpTrait {
  /**
   * Verifies that the operation carries the required symbol-name attribute.
   *
   * @param operation the operation to verify.
   * @return {@code true} if the symbol attribute exists.
   */
  @Contract(pure = true)
  static boolean verify(@NotNull Operation operation) {
    if (!operation.getAttributesMap().containsKey(SymbolTable.getSymbolAttributeName())) {
      operation.emitError("Symbol must have a symbol attribute.");
      return false;
    }

    if (!operation.getAttributesMap().containsKey(SymbolTable.getSymbolTypeAttributeName())) {
      operation.emitError("Symbol must have a type attribute.");
      return false;
    }
    return true;
  }

  public final record SymbolTableSymbol(String ident, Type type) {
  }

  /**
   * Returns the declared symbol name.
   *
   * @return the symbol name string.
   */
  @Contract(pure = true)
  default @NotNull SymbolTableSymbol getSymbol() {
    var symbolName = getOperation()
        .getAttributeAs(SymbolTable.getSymbolAttributeName(), StrAttrs.StringAttribute.class)
        .orElseThrow()
        .getValue();

    var symbolType = getOperation()
        .getAttributeAs(SymbolTable.getSymbolTypeAttributeName(), BuiltinAttrs.TypeAttribute.class)
        .orElseThrow()
        .getType();

    return new SymbolTableSymbol(symbolName, symbolType);
  }
}

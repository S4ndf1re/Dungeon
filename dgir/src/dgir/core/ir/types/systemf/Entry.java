package dgir.core.ir.types.systemf;

import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.compatibility.ExprOrOperator;

/** Context entry for type checking and inference */
  public sealed interface Entry {
    public final record VarBnd(
        Symbol<Expr, SystemFType> tmVar,
        SystemFType type) implements Entry {
      @Override
      public final String toString() {
        return tmVar + " : " + type;
      }
    }

    public final record TVarBnd(TypeVar tyVar) implements Entry {
      @Override
      public final String toString() {
        return tyVar.toString();
      }
    }

    public final record ETVarBnd(TypeVar tyVar) implements Entry {
      @Override
      public final String toString() {
        return tyVar.toString();
      }
    }

    public final record SETVarBnd(
        TypeVar tyVar,
        SystemFType type) implements Entry {
      @Override
      public final String toString() {
        return tyVar + " : " + type;
      }
    }

    /**
     * NOTE: this MUST be a class and not a record, as the equality to identify
     * marks is based on reference equality, as is default with class objects
     */
    public final class Mark implements Entry {
      @Override
      public final String toString() {
        return "MARK";
      }
    }

    public final record VarExpr(Symbol<Expr, SystemFType> symbol, ExprOrOperator<Expr, SystemFType> expr)
        implements Entry {
      @Override
      public final String toString() {
        return symbol + " -> " + expr;
      }
    }
  }


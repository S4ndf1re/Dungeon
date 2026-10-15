package dgir.dialect.arith;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.builtin.systemf.expr.BinOp;
import dgir.core.ir.types.builtin.systemf.expr.UnaryOp;
import dgir.core.ir.types.builtin.systemf.expr.CastOp;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFInference;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.expr.Var;
import dgir.core.ir.types.builtin.systemf.expr.LitExpr;

public final class ArithSystemFConversion {

  public static void registerBuiltinSystemFConversion() {
    ConverterRegistry.<Expr, SystemFType, TypeInference>addOperatorsToDialect(
        SystemFInference.class,
        Pair.of(ArithOps.ConstantOp.class, ArithSystemFConversion::convertConstOp),
        Pair.of(ArithOps.BinaryOp.class, ArithSystemFConversion::convertBinOp),
        Pair.of(ArithOps.UnaryOp.class, ArithSystemFConversion::convertUnaryOp),
        Pair.of(ArithOps.CastOp.class, ArithSystemFConversion::convertCastOp));
  }

  public static Expr convertConstOp(
      Operation op,
      TypeInference engine) {
    ArithOps.ConstantOp constOp = (ArithOps.ConstantOp) op.asOp();

    var expr = new LitExpr(constOp.getLocation(), new Literal.Generic(constOp.getResult()));
    expr.setInstantiateOperationCallback(instantiatedExpr -> {
      var inferredType = instantiatedExpr.getInferredType();
      assert inferredType.isPresent();
      assert inferredType.get().isFullySpecified();

      Type t = Type.fromGeneralParameterizedNominalType(inferredType.get().asTypeParameter().getConcrete());
      assert t.equals(constOp.getValueAttribute().getType());

      return new ArithOps.ConstantOp(constOp.getLocation(), constOp.getValueAttribute()).getOperation();
    });

    return expr;
  }

  public static Expr convertBinOp(
      Operation op,
      TypeInference engine) {

    ArithOps.BinaryOp binOp = (ArithOps.BinaryOp) op.asOp();

    Expr lhs = new Var(Symbol.<Expr, SystemFType>of(binOp.getLhs()));
    Expr rhs = new Var(Symbol.<Expr, SystemFType>of(binOp.getRhs()));
    var binMode = binOp.getMode();

    var result = new BinOp(op.getLocation(), lhs, rhs, binMode);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof BinOp;

      var custExpr = (BinOp) instantiatedExpr;

      var lhsValue = custExpr.lhs.getOutputValue();
      var rhsValue = custExpr.rhs.getOutputValue();

      return new ArithOps.BinaryOp(op.getLocation(), lhsValue,
          rhsValue, custExpr.mode).getOperation();
    });

    return result;
  }

  public static Expr convertUnaryOp(
      Operation op,
      TypeInference engine) {

    ArithOps.UnaryOp unaryOp = (ArithOps.UnaryOp) op.asOp();
    var lhs = engine.Var(Symbol.<Expr, SystemFType>of(unaryOp.getOperand()));
    var unaryMode = unaryOp.getMode();

    var result = new UnaryOp(op.getLocation(), lhs, unaryMode);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof UnaryOp;
      var custExpr = (UnaryOp) instantiatedExpr;

      var lhsValue = custExpr.operand.getOutputValue();
      return new ArithOps.UnaryOp(op.getLocation(), lhsValue,
          custExpr.mode).getOperation();
    });

    return result;
  }

  public static Expr convertCastOp(
      Operation op,
      TypeInference engine) {

    ArithOps.CastOp castOp = (ArithOps.CastOp) op.asOp();
    var value = engine.Var(Symbol.<Expr, SystemFType>of(castOp.getOperand()));
    var targetType = castOp.getTargetType();

    var result = new CastOp(op.getLocation(), value, targetType);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof CastOp;
      var custExpr = (CastOp) instantiatedExpr;

      var lhsValue = custExpr.operand.getOutputValue();

      return new ArithOps.CastOp(op.getLocation(), lhsValue,
          custExpr.targetType).getOperation();
    });
    return result;
  }
}

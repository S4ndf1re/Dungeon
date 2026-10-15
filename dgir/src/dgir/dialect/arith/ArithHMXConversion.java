package dgir.dialect.arith;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.expr.Lit;
import dgir.core.ir.types.builtin.hmx.expr.UnaryOp;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.builtin.hmx.expr.Var;
import dgir.core.ir.types.builtin.hmx.expr.BinOp;
import dgir.core.ir.types.builtin.hmx.expr.CastOp;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.dialect.arith.ArithOps.BinaryOp;
import dgir.dialect.arith.ArithOps.ConstantOp;

public final class ArithHMXConversion {

  public static void registerBuiltinHMXConversion() {
    ConverterRegistry.<HMXExpr, HMXType, TypeInference>addOperatorsToDialect(HMXInference.class,
        Pair.of(ConstantOp.class, ArithHMXConversion::convertConstOp),
        Pair.of(ArithOps.BinaryOp.class, ArithHMXConversion::convertBinOp),
        Pair.of(ArithOps.UnaryOp.class, ArithHMXConversion::convertUnaryOp),
        Pair.of(ArithOps.CastOp.class, ArithHMXConversion::convertCastOp));
  }

  public static HMXExpr convertConstOp(Operation op, TypeInference engine) {
    ArithOps.ConstantOp constOp = (ArithOps.ConstantOp) op.asOp();

    var expr = new Lit(constOp.getLocation(), new Literal.Generic(constOp.getResult()));
    expr.setInstantiateOperationCallback(instantiatedExpr -> {
      var inferredType = instantiatedExpr.getInferredType();
      assert inferredType.isPresent();

      var gpnt = inferredType.get().asTypeParameter();
      assert gpnt.isConcrete();

      Type t = Type.fromGeneralParameterizedNominalType(gpnt.getConcrete());
      assert t == constOp.getValueAttribute().getType();

      return new ArithOps.ConstantOp(constOp.getLocation(), constOp.getValueAttribute()).getOperation();
    });

    return expr;
  }

  public static HMXExpr convertBinOp(Operation op, TypeInference engine) {

    ArithOps.BinaryOp binOp = (ArithOps.BinaryOp) op.asOp();

    var lhs = new Var(Symbol.<HMXExpr, HMXType>of(binOp.getLhs()));
    var rhs = new Var(Symbol.<HMXExpr, HMXType>of(binOp.getRhs()));

    var result = new BinOp(binOp.getLocation(), lhs, rhs, binOp.getMode());

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof BinOp;

      var custExpr = (BinOp) instantiatedExpr;

      var lhsValue = custExpr.lhs.getOutputValue();
      var rhsValue = custExpr.rhs.getOutputValue();

      return new BinaryOp(op.getLocation(), lhsValue, rhsValue, custExpr.mode).getOperation();
    });

    return result;
  }

  public static HMXExpr convertUnaryOp(Operation op, TypeInference engine) {

    ArithOps.UnaryOp unaryOp = (ArithOps.UnaryOp) op.asOp();
    var lhs = new Var(Symbol.<HMXExpr, HMXType>of(unaryOp.getOperand()));
    var unaryMode = unaryOp.getMode();


    var result = new UnaryOp(unaryOp.getLocation(), lhs, unaryMode);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof UnaryOp;

      var custExpr = (UnaryOp) instantiatedExpr;

      var lhsValue = custExpr.lhs.getOutputValue();

      return new ArithOps.UnaryOp(op.getLocation(), lhsValue, custExpr.mode).getOperation();
    });
    return result;
  }

  public static HMXExpr convertCastOp(Operation op, TypeInference engine) {

    ArithOps.CastOp castOp = (ArithOps.CastOp) op.asOp();
    var value = new Var(Symbol.<HMXExpr, HMXType>of(castOp.getOperand()));
    var targetType = castOp.getTargetType();


    var result = new CastOp(castOp.getLocation(), value, targetType);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof CastOp;

      var custExpr = (CastOp) instantiatedExpr;

      var lhsValue = custExpr.operand.getOutputValue();

      return new ArithOps.CastOp(op.getLocation(), lhsValue, custExpr.targetType).getOperation();
    });
    return result;
  }
}

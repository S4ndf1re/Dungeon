package dgir.dialect.arith;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;
import dgir.core.ir.types.builtin.algorithmw.expr.BinOp;
import dgir.core.ir.types.builtin.algorithmw.expr.CastOp;
import dgir.core.ir.types.builtin.algorithmw.expr.Lit;
import dgir.core.ir.types.builtin.algorithmw.expr.UnaryOp;
import dgir.core.ir.types.builtin.algorithmw.expr.Var;
import dgir.core.ir.types.compatibility.ConverterRegistry;

public final class ArithAlgoWConversion {

  public static void registerBuiltinAlgoWConversion() {
    ConverterRegistry.<Expr, AlgorithmWType, TypeInference>addOperatorsToDialect(
        AlgorithmWInference.class,
        Pair.of(ArithOps.ConstantOp.class, ArithAlgoWConversion::convertConstOp),
        Pair.of(ArithOps.BinaryOp.class, ArithAlgoWConversion::convertBinOp),
        Pair.of(ArithOps.UnaryOp.class, ArithAlgoWConversion::convertUnaryOp));
  }

  public static Expr convertConstOp(
      Operation op,
      TypeInference engine) {
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

  public static Expr convertBinOp(
      Operation op,
      TypeInference engine) {

    ArithOps.BinaryOp binOp = (ArithOps.BinaryOp) op.asOp();
    var binMode = binOp.getMode();

    var lhs = Symbol.<Expr, AlgorithmWType>of(binOp.getLhs());
    var rhs = Symbol.<Expr, AlgorithmWType>of(binOp.getRhs());

    var result = new BinOp(binOp.getLocation(), new Var(binOp.getLocation(), lhs),
        new Var(binOp.getLocation(), rhs), binMode);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof BinOp;

      var binOpExpr = (BinOp) instantiatedExpr;

      var lhsValue = binOpExpr.lhs.getOutputValue();
      var rhsValue = binOpExpr.rhs.getOutputValue();

      return new ArithOps.BinaryOp(op.getLocation(), lhsValue,
          rhsValue, binOpExpr.mode).getOperation();
    });

    return result;
  }

  public static Expr convertUnaryOp(
      Operation op,
      TypeInference engine) {

    ArithOps.UnaryOp unaryOp = (ArithOps.UnaryOp) op.asOp();
    var unaryMode = unaryOp.getMode();
    var lhs = Symbol.<Expr, AlgorithmWType>of(unaryOp.getOperand());

    var result = new UnaryOp(unaryOp.getLocation(), new Var(unaryOp.getLocation(), lhs), unaryMode);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof UnaryOp;

      var unaryOpExpr = (UnaryOp) instantiatedExpr;

      var lhsValue = unaryOpExpr.operand.getOutputValue();

      return new ArithOps.UnaryOp(op.getLocation(), lhsValue,
          unaryOpExpr.mode).getOperation();
    });
    return result;
  }

  public static Expr convertCastOp(
      Operation op,
      TypeInference engine) {

    ArithOps.CastOp castOp = (ArithOps.CastOp) op.asOp();
    var value = Symbol.<Expr, AlgorithmWType>of(castOp.getOperand());
    var targetType = castOp.getTargetType();

    var result = new CastOp(castOp.getLocation(), new Var(castOp.getLocation(), value), targetType);

    result.setInstantiateOperationCallback(instantiatedExpr -> {
      assert instantiatedExpr instanceof CastOp;

      var castOpExpr = (CastOp) instantiatedExpr;

      var lhsValue = castOpExpr.operand.getOutputValue();

      return new ArithOps.CastOp(op.getLocation(), lhsValue,
          castOpExpr.targetType).getOperation();
    });
    return result;
  }
}

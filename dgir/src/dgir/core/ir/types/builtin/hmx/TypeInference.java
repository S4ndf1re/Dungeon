package dgir.core.ir.types.builtin.hmx;

import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.TypeInferenceSolver;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;

public final class TypeInference extends TypeInferenceSolver<TypeInference, HMXExpr, HMXType> {

  public TypeInference() {
    super(new TypeDialectConverterRegistry());
  }

  public TypeInference(TypeDialectConverterRegistry registry) {
    super(registry);
  }


  @Override
  public SolveResult<HMXExpr> solve(ExprOrOperator<HMXExpr, HMXType> expr) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'solve'");
  }

  @Override
  public HMXExpr generalBlockToInferenceExpr(GeneralBlock block) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'generalBlockToInferenceExpr'");
  }

  @Override
  public Pair<HMXType, Optional<ConversionContext<HMXExpr, HMXType>>> generalNominalTypeToInferenceType(
      GeneralParameterizedNominalType type, Optional<ConversionContext<HMXExpr, HMXType>> context) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'generalNominalTypeToInferenceType'");
  }

  @Override
  public HMXExpr asExpression(ExprOrOperator<HMXExpr, HMXType> exprOrOp) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'asExpression'");
  }

}


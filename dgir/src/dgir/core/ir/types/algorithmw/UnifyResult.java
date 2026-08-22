package dgir.core.ir.types.algorithmw;

import dgir.core.ir.types.InferenceTree;

public record UnifyResult(Subst subst, InferenceTree tree) {
  public AlgorithmWType applySubst(AlgorithmWType type) {
    return this.subst.apply(type);
  }
}

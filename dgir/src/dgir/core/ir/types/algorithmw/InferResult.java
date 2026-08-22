package dgir.core.ir.types.algorithmw;

import dgir.core.ir.types.InferenceTree;

public final record InferResult(
    Subst subst,
    AlgorithmWType type,
    InferenceTree tree) {
}

package dgir.core.ir.types.builtin.algorithmw;

import dgir.core.ir.types.InferenceTree;

public final record InferResult(
    AlgorithmWType type,
    InferenceTree tree) {
}

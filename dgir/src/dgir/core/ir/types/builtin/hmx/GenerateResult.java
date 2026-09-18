package dgir.core.ir.types.builtin.hmx;

import dgir.core.ir.types.InferenceTree;

public final record GenerateResult(
    Constraint constr,
    InferenceTree tree) {
}

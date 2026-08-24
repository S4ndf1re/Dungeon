package dgir.core.ir.types.systemf;

import dgir.core.ir.types.InferenceTree;

public final record CheckResult(Context ctx, InferenceTree tree) {
}

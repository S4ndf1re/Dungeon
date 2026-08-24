package dgir.core.ir.types.systemf;

import dgir.core.ir.types.InferenceTree;

public final record TypeResult(
        SystemFType type,
        Context ctx,
        InferenceTree tree) {
    }

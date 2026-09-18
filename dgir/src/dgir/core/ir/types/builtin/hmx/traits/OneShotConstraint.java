package dgir.core.ir.types.builtin.hmx.traits;

/**
 * Marker for constraints that must be solved at most once: re-solving them in
 * a later epoch is either a no-op (already consumed) or unsound (e.g.
 * {@link Inst} re-enters the env and can unfold recursive bindings forever).
 *
 * The engine skips marked constraints that report {@code isSolved()} and calls
 * {@code markSolved()} after the first solve. Implementors own their solved
 * state and carry it through {@code applySubst} copies.
 */
public interface OneShotConstraint {

  boolean isSolved();

  void markSolved();

}

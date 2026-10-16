package dgir.core.ir.types.traits;

/**
 * Marker trait for recursion-breaker cell expressions produced during
 * instantiation (see {@code IInstantiable#getCellFor}).
 *
 * <p>
 * A cell is a placeholder for a definition whose instantiation is still in
 * progress (mutual recursion). It shares its {@code underlyingOperation} with
 * the resolved instance, so a cell and its target legitimately coexist as two
 * expression nodes referencing the same operation. Traversals that collect
 * operations must therefore treat a cell and its resolved instance as one:
 * duplicates where either side is a cell are expected; any other duplicate is
 * a hard bug in the inference chain.
 */
public interface ICell {
}

package dgir.core.ir;

import com.fasterxml.jackson.annotation.*;
import java.util.*;
import org.jetbrains.annotations.*;

/**
 * A region containing an ordered list of {@link Block}s, attached to an
 * {@link Operation}.
 *
 * <p>
 * Regions can also be freestanding ("orphan" regions) while being built up, and
 * then transferred
 * into an operation via {@link #takeRegion}.
 *
 * <p>
 * Every region always has at least one block — the <em>entry block</em> — which
 * is created
 * automatically if needed. Execution enters a region through this block.
 *
 * <p>
 * Regions may carry <em>body values</em>: typed values that are visible inside
 * the region and
 * act like block/region arguments (e.g. loop induction variables).
 *
 * <pre>{@code
 * Region {
 *   Block entryBlock {
 *     Operation1
 *     ...
 *     TerminatorOperation
 *   }
 *   Block otherBlock { ... }
 * }
 * }</pre>
 *
 * @see Operation
 * @see Block
 */
@JsonPropertyOrder({ "regionValues", "blocks" })
public final class Region {

  // =========================================================================
  // Members
  // =========================================================================

  /**
   * The blocks contained in this region, in order. A region always has at least
   * one block (the
   * entry block), which is created automatically if needed.
   */
  private final @NotNull List<Block> blocks = new ArrayList<>();

  /**
   * Values visible inside this region, acting as parameters/arguments (e.g. the
   * induction variable
   * of a for-loop body).
   */
  private final @JsonIdentityReference @Unmodifiable @NotNull List<@NotNull Value> regionValues;

  private final @JsonIgnore @Nullable Operation parent;
  private final @JsonIgnore Optional<Region> copyFrom;

  // =========================================================================
  // Constructors
  // =========================================================================

  private Region(
      @Nullable Operation parent, @Nullable List<Value> regionValues, @NotNull List<Block> blocks,
      Optional<Region> copyFrom) {
    this.parent = parent;
    this.regionValues = new ArrayList<>(regionValues == null ? List.of() : regionValues);
    for (Block block : blocks)
      addBlock(block);

    this.copyFrom = copyFrom;
  }

  public Region(
      @Nullable Operation parent, @Nullable List<Value> regionValues, @NotNull List<Block> blocks) {
    this(parent, regionValues, blocks, Optional.empty());
  }

  @JsonCreator
  public Region(
      @JsonProperty("regionValues") @Nullable List<Value> regionValues,
      @JsonProperty("blocks") @NotNull List<Block> blocks) {
    this(null, regionValues, blocks);
  }

  /**
   * Nested copy of a region, by copying all blocks and setting their parents
   */
  public Region(Region other, Operation parent) {
    this(parent,
        List.copyOf(other.regionValues.stream().map(val -> new Value(val.getType(), val.getDebugInfo())).toList()),
        other.blocks.stream().map(block -> new Block(block)).toList(),
        Optional.ofNullable(other));

    // After completely copying all blocks, all values (the new output
    // values of each block's operations) must be replaced for every operation that
    // is part of this region! As every subtree Operation is also copied, all result
    // values must be replaced.
    // For this, all child blocks must be iterated, and every operation's output
    // value must be replaced for all children of `this` region.
    // For this, helper methods are already in place!
    //
    // NOTE(jan): since each child Region (contained in each Operation in each
    // block) executes the same logic,
    // This must not be performed recursively. Additionally, it can be assumed that
    // the order of the value replacement is also irrelevant,
    // as the SSA nature of the IR prevents shadowing and other bugs. Hence every
    // result value can simply be replaced in all child operations to any depth.
    // Since Regions are the value bounds, and assuming the IR region was in a valid
    // state before copying, the copied region will also be valid.
    List<Operation> directChildOperations = this.blocks.stream().flatMap(block -> block.getOperations().stream())
        .toList();

    for (var op : directChildOperations) {
      var output = op.getOutput();
      var copyFrom = op.getCopyFrom();
      if (copyFrom.isPresent() && output.isPresent() && copyFrom.get().getOutput().isPresent()) {
        // NOTE(jan): this might replace one variable multiple times, but is ESSENTIAL
        // for non SSA form IR Operation Trees.
        // Sometimes, operations specify the same output value. This is tracked in the
        // values definition list.
        // In those cases, every definition (output value) of all operations using said
        // value must replace the value.
        // But since tracking values created during copying requires some form of state
        // tracking, the value is just replaced for every operation.
        // As all uses of those values are replaced, this operation will always create a
        // valid new region tree.
        var oldOutputValue = output.get().getValue();
        var outputValue = new Value(oldOutputValue.getType(), oldOutputValue.getDebugInfo());
        copyFrom.get().getOutput().get().getValue().replaceAllUsesIn(outputValue, this);
      }
    }

    assert this.regionValues.size() == other.regionValues.size();
    for (int i = 0; i < this.regionValues.size(); i++) {
      other.regionValues.get(i).replaceAllUsesIn(this.regionValues.get(i), this);
    }

    assert this.blocks.size() == other.blocks.size();
    for (int i = 0; i < this.blocks.size(); i++) {
      other.blocks.get(i).replaceAllUsesIn(this.blocks.get(i), this);
    }
  }
  // =========================================================================
  // Blocks
  // =========================================================================

  /**
   * Get the blocks in this region.
   *
   * @return An unmodifiable view of the block list.
   */
  @Contract(pure = true)
  public @NotNull @UnmodifiableView List<Block> getBlocks() {
    return Collections.unmodifiableList(blocks);
  }

  public Block addBlock(@NotNull Block block) {
    return addBlockAt(blocks.size(), block);
  }

  public Block addBlockAt(int index, @NotNull Block block) {
    assert block.getParent().isEmpty() : "Block is already part of a region.";
    assert index >= 0 && index <= blocks.size() : "Index out of bounds.";
    blocks.add(index, block);
    block.setParent(this);
    return block;
  }

  public Block addBlockBefore(@NotNull Block block, @NotNull Block before) {
    return addBlockAt(blocks.indexOf(before), block);
  }

  public Block addBlockAfter(@NotNull Block block, @NotNull Block after) {
    return addBlockAt(blocks.indexOf(after) + 1, block);
  }

  public Block removeBlock(@NotNull Block block) {
    assert blocks.contains(block) : "Block is not part of this region.";
    return removeBlockAt(blocks.indexOf(block));
  }

  public Block removeBlockAt(int index) {
    assert index >= 0 && index < blocks.size() : "Index out of bounds.";
    Block block = blocks.remove(index);
    if (block != null) {
      // Ensure that none of the region values are used or defined in the removed
      // block.
      assert !block.areValuesUsedOrDefined(new HashSet<>(regionValues), false)
          : "Cannot remove block that uses or defines region values of its current parent.";
      block.setParent(null);
    }
    return block;
  }

  @JsonIgnore
  @Contract(pure = true)
  public @NotNull Block getEntryBlock() {
    return blocks.getFirst();
  }

  /**
   * Get the first operation in the entry block.
   *
   * @return The first operation in the entry block.
   */
  @JsonIgnore
  @Contract(pure = true)
  public @NotNull Operation getEntryOperation() {
    var operations = getEntryBlock().getOperations();
    assert !operations.isEmpty() : "Entry block must have at least one operation.";
    return operations.getFirst();
  }

  // =========================================================================
  // Body Values
  // =========================================================================

  @Contract(pure = true)
  public @NotNull @Unmodifiable List<Value> getRegionValues() {
    return regionValues;
  }

  @Contract(pure = true)
  public Optional<Value> getRegionValue(int index) {
    if (index < 0 || index >= regionValues.size())
      return Optional.empty();
    return Optional.of(regionValues.get(index));
  }

  @Contract(pure = true)
  public int getRegionValueIndex(@NotNull Value value) {
    return regionValues.indexOf(value);
  }

  // =========================================================================
  // Parent & Transfer
  // =========================================================================

  @Contract(pure = true)
  public @NotNull Optional<Operation> getParent() {
    return Optional.ofNullable(parent);
  }

  public Optional<Region> getParentRegion() {
    return this.getParent().flatMap(parent -> parent.getParentRegion());
  }

  @JsonIgnore
  @Contract(pure = true)
  public int getIndex() {
    return parent == null ? -1 : parent.getRegions().indexOf(this);
  }

  /**
   * Move all blocks from {@code other} into this region. Uses of {@code other}'s
   * body values are
   * replaced with the corresponding values from this region.
   *
   * @param other    The region to drain. Must have matching region value types
   *                 and count.
   * @param override If true, all blocks currently contained in this region will
   *                 be removed first.
   */
  public void takeRegion(@NotNull Region other, boolean override) {
    assertRegionCompatibility(other);

    if (override)
      for (Block block : List.copyOf(getBlocks())) {
        removeBlock(block);
      }

    // Update uses of the other region's body values to point to this region's body
    // values instead.
    for (int i = 0; i < this.regionValues.size(); i++) {
      Value thisBodyValue = this.regionValues.get(i);
      Value otherBodyValue = other.regionValues.get(i);
      if (thisBodyValue != otherBodyValue)
        otherBodyValue.replaceAllUsesWith(thisBodyValue);
    }

    // Move all blocks from the other region into this region, updating their parent
    // pointers.
    for (Block block : List.copyOf(other.blocks)) {
      other.removeBlock(block);
      addBlock(block);
    }
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  /**
   * Assert that this region is compatible with another region, meaning they have
   * the same number of
   * region values and matching types. This is a precondition for taking blocks
   * from other regions.
   */
  public void assertRegionCompatibility(@NotNull Region other) {
    assert this.regionValues.size() == other.regionValues.size()
        : "Region values of regions must have the same size.";
    for (int i = 0; i < this.regionValues.size(); i++) {
      assert this.regionValues.get(i).getType().equals(other.regionValues.get(i).getType())
          : "Region value types of regions must match.";
    }
  }

  // =========================================================================
  // Object
  // =========================================================================

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("Region[" + getIndex() + "] (");
    for (int i = 0; i < regionValues.size(); i++) {
      builder.append(regionValues.get(i));
      if (i < regionValues.size() - 1)
        builder.append(", ");
    }

    builder.append(") {");
    builder.append(blocks.stream().map(Block::toString).reduce("", (a, b) -> a + "\n  " + b));
    builder.append("\n}");

    return builder.toString();
  }
}

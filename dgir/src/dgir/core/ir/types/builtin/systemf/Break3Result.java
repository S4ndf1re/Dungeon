package dgir.core.ir.types.builtin.systemf;

import java.util.List;
import java.util.Optional;

public final record Break3Result(
    List<Entry> left,
    Optional<Entry> target,
    List<Entry> right) {
}

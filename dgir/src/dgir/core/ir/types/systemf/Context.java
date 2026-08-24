package dgir.core.ir.types.systemf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import dgir.core.ir.types.TypeDialect.TypeInferenceSolver.ConversionContext;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.compatibility.Scope.ScopeLike;

public class Context extends ScopeLike<SystemFType>
    implements ConversionContext<Expr, SystemFType> {

  private ArrayList<Entry> entries;
  private HashSet<ExprOrOperator<Expr, SystemFType>> visited;

  public Context() {
    super();
    this.entries = new ArrayList<>();
    this.visited = new HashSet<>();
  }

  public Context(List<Entry> entries, Context other) {
    super(other);
    this.entries = new ArrayList<>(entries);
    this.visited = other.visited;
  }

  public Context(Context other) {
    super(other);
    this.entries = new ArrayList<>(other.entries);
    this.visited = other.visited;
  }

  @Override
  public String toString() {
    return ("{" +
        this.entries
            .stream()
            .map(Object::toString)
            .collect(Collectors.joining(", "))
        +
        "}");
  }


  public void push(Entry entry) {
    this.entries.add(entry);
  }

  public void extend(Collection<? extends Entry> entries) {
    this.entries.addAll(entries);
  }

  public Optional<Entry> find(Predicate<? super Entry> filterFunc) {
    var filtered = this.entries.stream().filter(filterFunc).toList();
    if (filtered.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.ofNullable(filtered.getLast());
    }
  }

  public Context copy() {
    return new Context(this);
  }

  /**
   * break the context into possibly three parts, if the predicate was found to be
   * in the context.
   *
   * @param pred the predicate to find an entry in the context
   * @return the three parts broken up into left half (up to, but excluding, the
   *         found entry), the entry for which the pred is true, and the right
   *         half (everything after the found entry, exlucing the entry)
   */
  public Break3Result break3(Predicate<? super Entry> pred) {
    var positions = IntStream.range(0, this.entries.size())
        .filter(i -> pred.test(this.entries.get(i)))
        .toArray();

    OptionalInt position = OptionalInt.empty();
    if (positions.length > 0) {
      position = OptionalInt.of(positions[positions.length - 1]);
    }

    if (position.isPresent()) {
      var firstPart = this.entries.subList(0, position.getAsInt());
      var target = this.entries.get(position.getAsInt());
      var secondPart = this.entries.subList(
          position.getAsInt() + 1,
          this.entries.size());
      return new Break3Result(
          List.copyOf(firstPart),
          Optional.of(target),
          secondPart);
    } else {
      return new Break3Result(
          List.copyOf(this.entries.subList(0, this.entries.size())),
          Optional.empty(),
          List.of());
    }
  }

  public static Context fromParts(
      List<Entry> left,
      Entry middle,
      List<Entry> right,
      Context oldContext) {
    var list = new ArrayList<Entry>();
    list.addAll(left);
    list.add(middle);
    list.addAll(right);

    return new Context(list, oldContext);
  }

  public SystemFType applyOnce(SystemFType type) {
    if (type instanceof SystemFType.EtVar etVar) {
      var filterRes = this.find(
          entry -> entry instanceof Entry.SETVarBnd bnd &&
              bnd.tyVar().equals(etVar.tyVar));
      if (filterRes.isPresent()) {
        var solvedEtVar = (Entry.SETVarBnd) filterRes.get();
        solvedEtVar.tyVar().provideSolution(solvedEtVar.type());
        return this.applyOnce(solvedEtVar.type());
      } else {
        return type;
      }
    } else if (type instanceof SystemFType.Arrow arrow) {
      return new SystemFType.Arrow(
          this.applyOnce(arrow.from),
          this.applyOnce(arrow.to));
    } else if (type instanceof SystemFType.ForAll forAll) {
      return new SystemFType.ForAll(
          forAll.boundVar,
          this.applyOnce(forAll.body));
    } else if (type instanceof SystemFType.Lit lit) {
      return new SystemFType.Lit(lit.ident, lit.parameters.stream().map(param -> this.applyOnce(param)).toList());
    }
    return type;
  }

  public SystemFType apply(SystemFType type) {
    var current = type;
    var changed = true;

    while (changed) {
      changed = false;
      var newType = this.applyOnce(current);
      if (!newType.equals(current)) {
        current = newType;
        changed = true;
      }
    }

    return current;
  }

  /**
   * Test wheather type Variable A appears before type Variable B in the context.
   * In this case, appearing before means that type Variable A appears later in
   * the context
   */
  public boolean before(TypeVar tyVarA, TypeVar tyVarB) {
    var posA = IntStream.range(0, this.entries.size())
        .filter(
            i -> this.entries.get(i) instanceof Entry.ETVarBnd bnd &&
                bnd.tyVar().equals(tyVarA))
        .findFirst();

    var posB = IntStream.range(0, this.entries.size())
        .filter(
            i -> this.entries.get(i) instanceof Entry.ETVarBnd bnd &&
                bnd.tyVar().equals(tyVarB))
        .findFirst();

    if (posA.isPresent() && posB.isPresent()) {
      return posA.getAsInt() > posB.getAsInt();
    }
    return false;
  }

  public boolean isVisited(ExprOrOperator<Expr, SystemFType> expr) {
    return this.visited.contains(expr);
  }

  public void visit(ExprOrOperator<Expr, SystemFType> expr) {
    this.visited.add(expr);
  }
}

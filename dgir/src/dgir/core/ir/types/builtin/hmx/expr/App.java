package dgir.core.ir.types.builtin.hmx.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.traits.IApplication;
import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.Env;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Subst;
import dgir.core.ir.types.builtin.hmx.TypeInference;

public final class App extends HMXExpr implements IApplication<HMXExpr, HMXType> {

  private final HMXExpr func;
  private final List<HMXExpr> args;

  public HMXExpr func() {
    return this.func.unwrapOrThis();
  }

  public List<HMXExpr> args() {
    return this.args.stream().map(HMXExpr::unwrapOrThis).toList();
  }

  private Optional<HMXType> inferredFunctionType;

  public App(
      HMXExpr func,
      HMXExpr arg) {
    this.func = func;
    this.args = List.of(arg);
    this.inferredFunctionType = Optional.empty();
  }

  public App(
      Location loc,
      HMXExpr func,
      HMXExpr arg) {
    super(loc);
    this.func = func;
    this.args = List.of(arg);
    this.inferredFunctionType = Optional.empty();
  }

  public App(
      HMXExpr func,
      List<HMXExpr> args) {
    this.func = func;
    this.args = List.copyOf(args);
    this.inferredFunctionType = Optional.empty();
  }

  public App(
      Location loc,
      HMXExpr func,
      List<HMXExpr> args) {
    super(loc);
    this.func = func;
    this.args = List.copyOf(args);
    this.inferredFunctionType = Optional.empty();
  }

  public App(
      App other) {
    super(other);
    this.func = other.func;
    this.args = List.copyOf(other.args);
    this.inferredFunctionType = other.inferredFunctionType;
  }

  public App(App other, HMXExpr func,
      List<HMXExpr> args) {
    super(other);
    this.func = func;
    this.args = List.copyOf(args);
    this.inferredFunctionType = other.inferredFunctionType;
  }

  @Override
  public final String toString() {
    if (args.size() > 1) {
      return func + " (" + args.stream().map(Object::toString).collect(Collectors.joining(",")) + ")";
    } else if (!args.isEmpty()) {
      return func + " " + args.get(0);
    } else {
      return func + " ()";
    }
  }

  @Override
  public List<HMXExpr> getChildren() {
    var list = new ArrayList<HMXExpr>();
    list.add(this.func.getExpr());
    this.args.forEach(arg -> list.add(arg.getExpr()));
    return List.copyOf(list);
  }


  @Override
  public List<HMXExpr> getApplications() {
    return this.args();
  }

  @Override
  public HMXExpr getFunction() {
    return this.func();
  }

  @Override
  public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
    this.inferredFunctionType = this.inferredFunctionType.map(fnTy -> solution.apply(fnTy));

    return new App(this, this.func.instantiate(engine, env, solution),
        this.args.stream().map(arg -> arg.instantiate(engine, env, solution)).toList());

  }

  @Override
  public HMXExpr copy() {
    return new App(this);
  }

  @Override
  public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
    HMXType resultType = type;

    HMXType builtArrowType = resultType;
    ArrayList<TypeVar<HMXType>> quantifiedVars = new ArrayList<>();
    ArrayList<Constraint> argConstraints = new ArrayList<>();

    for (var arg : this.args.reversed()) {
      var freshTypeVar = new TypeVar<HMXType>();
      quantifiedVars.add(freshTypeVar);
      GenerateResult argRes = engine.generate(arg, env, new HMXType.Var(this.getLocation(), freshTypeVar));
      argConstraints.add(argRes.constr());
      builtArrowType = new HMXType.Arrow(this.getLocation(),
          new HMXType.Var(this.getLocation(), freshTypeVar), builtArrowType);
    }

    // NOTE: in case the APP parameters are empty, i.e. a function without
    // parameters is configured, the APP behaviour and the ABS behaviour are
    // identical and treat the function type as a Unit -> t0. This means, that the
    // final
    // type is an arrow type that accepts a unit value as a parameter.
    if (builtArrowType == resultType) {
      builtArrowType = new HMXType.Arrow(this.getLocation(),
          new HMXType.LitType(this.getLocation(), TypeIdent.TYPE_IDENT_UNIT), builtArrowType);
    }

    var fnTypeVar = new TypeVar<HMXType>();
    var fnType = new HMXType.Var(this.getLocation(), fnTypeVar);
    GenerateResult funcInferRes = engine.generate(func, env, fnType);
    quantifiedVars.add(fnTypeVar);

    this.inferredFunctionType = Optional.of(builtArrowType);

    return new GenerateResult(
        new Constraint.Exists(quantifiedVars,
            new Constraint.And(new Constraint.And(argConstraints),
                new Constraint.Sub(fnType, builtArrowType),
                funcInferRes.constr())));
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof App other && this.func.equals(other.func) && this.args.equals(other.args)
        && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.func, this.args, super.hashCode());
  }

  @Override
  public void replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
    this.func.replaceSymbol(original, replacement);
    for (var arg : this.args) {
      arg.replaceSymbol(original, replacement);
    }
  }

  public Optional<HMXType> getInferredFunctionType() {
    return this.inferredFunctionType;
  }
}

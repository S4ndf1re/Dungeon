package dgir.core.ir.types.builtin.algorithmw.expr;

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
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Env;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.InferResult;
import dgir.core.ir.types.builtin.algorithmw.Subst;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;
import dgir.core.ir.types.traits.IApplication;

public final class App extends Expr implements IApplication<Expr, AlgorithmWType> {

    private final Expr func;
    private final List<Expr> args;

    public Expr func() {
      return this.func.unwrapOrThis();
    }

    public List<Expr> args() {
      return this.args.stream().map(Expr::unwrapOrThis).toList();
    }

    private Optional<AlgorithmWType> inferredFunctionType;

    public App(
        Expr func,
        Expr arg) {
      this.func = func;
      this.args = List.of(arg);
      this.inferredFunctionType = Optional.empty();
    }

    public App(
        Location loc,
        Expr func,
        Expr arg) {
      super(loc);
      this.func = func;
      this.args = List.of(arg);
      this.inferredFunctionType = Optional.empty();
    }

    public App(
        Expr func,
        List<Expr> args) {
      this.func = func;
      this.args = List.copyOf(args);
      this.inferredFunctionType = Optional.empty();
    }

    public App(
        Location loc,
        Expr func,
        List<Expr> args) {
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

    public App(App other, Expr func,
        List<Expr> args) {
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
    public List<Expr> getChildren() {
      var list = new ArrayList<Expr>();
      list.add(this.func.getExpr());
      this.args.forEach(arg -> list.add(arg.getExpr()));
      return List.copyOf(list);
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.func.containsSymbol(symbol) || this.args.stream().anyMatch(arg -> arg.containsSymbol(symbol));
    }

    @Override
    public List<Expr> getApplications() {
      return this.args();
    }

    @Override
    public Expr getFunction() {
      return this.func();
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      this.inferredFunctionType = this.inferredFunctionType.map(fnTy -> solution.apply(fnTy));

      var funcExpr = engine.asExpression(this.func);
      if (this.inferredFunctionType.isPresent() && funcExpr.getInferredType().isPresent()) {
        // var newSolution = solution.expand(engine, funcExpr, this.inferredFunctionType.get());

        // return new App(this, this.func.instantiate(engine, env, newSolution),
        //     this.args.stream().map(arg -> arg.instantiate(engine, env, newSolution)).toList());
        return new App(this, this.func.instantiate(engine, env, solution),
            this.args.stream().map(arg -> arg.instantiate(engine, env, solution)).toList());
      } else {
        return new App(this, this.func.instantiate(engine, env, solution),
            this.args.stream().map(arg -> arg.instantiate(engine, env, solution)).toList());
      }

    }

    @Override
    public Expr copy() {
      return new App(this);
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      AlgorithmWType resultType = new AlgorithmWType.Var(this.getLocation(), new TypeVar<>(engine.getCurrentLevel()));

      InferResult funcInferRes = engine.infer(func, env);

      AlgorithmWType builtArrowType = resultType;

      for (var arg : this.args.reversed()) {
        InferResult argRes = engine.infer(arg, env);
        builtArrowType = new AlgorithmWType.Arrow(this.getLocation(), argRes.type(), builtArrowType);
      }

      // NOTE: in case the APP parameters are empty, i.e. a function without
      // parameters is configured, the APP behaviour and the ABS behaviour are
      // identical and treat the function type as a Unit -> t0. This means, that the
      // final
      // type is an arrow type that accepts a unit value as a parameter.
      if (builtArrowType == resultType) {
        builtArrowType = new AlgorithmWType.Arrow(this.getLocation(),
            new AlgorithmWType.LitType(this.getLocation(), TypeIdent.TYPE_IDENT_UNIT), builtArrowType);
      }

      this.inferredFunctionType = Optional.of(funcInferRes.type());
      engine.unify(funcInferRes.type(), builtArrowType);

      return new InferResult(resultType);
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
    public void replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      this.func.replaceSymbol(original, replacement);
      this.args.forEach(arg -> arg.replaceSymbol(original, replacement));
    }

    public Optional<AlgorithmWType> getInferredFunctionType() {
      return this.inferredFunctionType;
    }
  }

package dgir.core.ir.types;

import dgir.core.ir.types.AlgorithmWInference.AlgorithmWType.Arrow;
import dgir.core.ir.types.AlgorithmWInference.AlgorithmWType.LitType;
import dgir.core.ir.types.AlgorithmWInference.AlgorithmWType.Tuple;
import dgir.core.ir.types.AlgorithmWInference.AlgorithmWType.UnifyResult;
import dgir.core.ir.types.AlgorithmWInference.AlgorithmWType.Var;
import dgir.core.ir.types.AlgorithmWInference.Expr.InferResult;
import dgir.core.ir.types.compatibility.AlgorithmWCompatibility;
import dgir.core.ir.types.compatibility.InferOrTransformResult;
import dgir.core.ir.types.compatibility.InferResultMarker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class AlgorithmWInference
    extends
    TypeDialect<InferOrTransformResult<dgir.core.ir.types.AlgorithmWInference.Expr.InferResult, dgir.core.ir.types.AlgorithmWInference.Expr>, AlgorithmWCompatibility> {

  private static Optional<TypeInference> instance = Optional.empty();

  @Override
  public TypeInferenceSolver<AlgorithmWCompatibility> getSolverInstance() {
    if (AlgorithmWInference.instance.isPresent()) {
      return AlgorithmWInference.instance.get();
    } else {
      TypeInference solver = new TypeInference();
      AlgorithmWInference.instance = Optional.of(solver);
      return solver;
    }
  }

  @Override
  public List<Class<? extends Type>> getAllowedTypes() {
    return TypeDialect.extractTypesFromAbstract(AlgorithmWType.class);
  }

  @Override
  public List<Class<? extends Expression>> getAllowedExpressions() {
    return TypeDialect.extractExpressionsFromAbstract(Expr.class);
  }

  public static interface Expr extends Expression, AlgorithmWCompatibility {
    private static InferResult convertInferOrTransformToInferResult(
        InferOrTransformResult<InferResult, Expr> infOrTrans,
        TypeInference engine,
        Env env) {
      if (infOrTrans.isInfer()) {
        return infOrTrans.getInferResult();
      } else {
        return engine.infer(infOrTrans.getTransformExpr(), env);
      }
    }

    public static record InferResult(
        Subst subst,
        AlgorithmWType type,
        InferenceTree tree) implements InferResultMarker<AlgorithmWType> {
    }

    public abstract InferResult infer(TypeInference engine, Env env);

    public default @Override InferOrTransformResult<Expr.InferResult, Expr> inferOrTransformAlgorithmW(
        TypeInference engine, Env env) {
      return new InferOrTransformResult.Infer<Expr.InferResult, Expr>(
          this.infer(engine, env));
    }

    public static final class ExprAnn implements Expr {

      private final AlgorithmWCompatibility expr;
      private final AlgorithmWType type;

      public ExprAnn(AlgorithmWCompatibility expr, AlgorithmWType type) {
        this.expr = expr;
        this.type = type;
      }

      @Override
      public InferResult infer(TypeInference engine, Env env) {
        InferResult res = Expr.convertInferOrTransformToInferResult(
            expr.inferOrTransformAlgorithmW(engine, env),
            engine,
            env);

        var unifyRes = engine.unify(res.type, type);
        var subst = unifyRes.subst.compose(res.subst);

        return new InferResult(
            subst,
            type,
            new InferenceTree(
                "T-Ann",
                env + " |- " + this,
                type.toString(),
                List.of(res.tree)));
      }
    }

    public static sealed interface Lit {
      public AlgorithmWType getAlgorithmWType();

      public final record LitInt(int value) implements Lit {
        @Override
        public final String toString() {
          return "Int(" + value + ")";
        }

        @Override
        public AlgorithmWType getAlgorithmWType() {
          return new AlgorithmWType.LitType("Int");
        }
      }

      public final record LitBool(boolean value) implements Lit {
        @Override
        public final String toString() {
          return "Bool(" + value + ")";
        }

        @Override
        public AlgorithmWType getAlgorithmWType() {
          return new AlgorithmWType.LitType("Bool");
        }
      }
    }

    public static final class ExprLit implements Expr {

      private Lit value;

      public ExprLit(Lit value) {
        this.value = value;
      }

      @Override
      public final String toString() {
        return value.toString();
      }

      @Override
      public InferResult infer(TypeInference engine, Env env) {
        return new InferResult(
            Subst.newEmpty(),
            value.getAlgorithmWType(),
            new InferenceTree(
                "T-" + value.getAlgorithmWType(),
                env + " |- " + this,
                value.getAlgorithmWType().toString(),
                List.of()));
      }
    }

    public static final class ExprTuple implements Expr {

      private List<Expr> elements;

      public ExprTuple(List<Expr> elements) {
        this.elements = elements;
      }

      @Override
      public final String toString() {
        return ("(" +
            elements
                .stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "))
            +
            ")");
      }

      @Override
      public InferResult infer(TypeInference engine, Env env) {
        String input = env + " |- " + this;
        Subst subst = Subst.newEmpty();
        ArrayList<AlgorithmWType> types = new ArrayList<>();
        ArrayList<InferenceTree> trees = new ArrayList<>();
        Env currentEnv = env.copy();

        for (var expr : elements) {
          var res = engine.infer(expr, currentEnv);
          subst = res.subst.compose(subst);
          currentEnv = currentEnv.apply(res.subst);
          types.add(res.type);
          trees.add(res.tree);
        }

        var resultType = new AlgorithmWType.Tuple(List.copyOf(types));

        return new InferResult(
            subst,
            resultType,
            new InferenceTree(
                "T-Tuple",
                input,
                "" + resultType,
                List.copyOf(trees)));
      }
    }

    public static final class ExprVar implements Expr {

      private final String name;

      public ExprVar(String name) {
        this.name = name;
      }

      @Override
      public final String toString() {
        return name;
      }

      @Override
      public InferResult infer(TypeInference engine, Env env) {
        String input = env + " |- " + this;

        Scheme scheme = env.env.get(name);
        if (scheme != null) {
          AlgorithmWType instantiated = scheme.instantiate(engine);
          return new InferResult(
              Subst.newEmpty(),
              instantiated,
              new InferenceTree(
                  "T-Var",
                  input,
                  instantiated.toString(),
                  List.of()));
        } else {
          throw new TypingException.UnknownVariable(name);
        }
      }
    }

    public static final class ExprApp implements Expr {

      private final AlgorithmWCompatibility func;
      private final AlgorithmWCompatibility arg;

      public ExprApp(
          AlgorithmWCompatibility func,
          AlgorithmWCompatibility arg) {
        this.func = func;
        this.arg = arg;
      }

      @Override
      public final String toString() {
        return func + " " + arg;
      }

      @Override
      public InferResult infer(TypeInference engine, Env env) {
        String input = env + " |- " + this;

        AlgorithmWType resultType = new AlgorithmWType.Var(
            engine.freshTypeVar());

        InferResult res1 = Expr.convertInferOrTransformToInferResult(
            func.inferOrTransformAlgorithmW(engine, env),
            engine,
            env);
        Env envSubst = env.apply(res1.subst);
        InferResult res2 = Expr.convertInferOrTransformToInferResult(
            arg.inferOrTransformAlgorithmW(engine, envSubst),
            engine,
            envSubst);

        AlgorithmWType funcTypeSubst = res2.subst.apply(res1.type);
        AlgorithmWType expectedFuncType = new Arrow(res2.type, resultType);

        UnifyResult res3 = engine.unify(funcTypeSubst, expectedFuncType);

        Subst finalSubst = res3.subst.compose(res2.subst.compose(res1.subst));
        AlgorithmWType finalType = res3.subst.apply(resultType);

        return new InferResult(
            finalSubst,
            finalType,
            new InferenceTree(
                "T-App",
                input,
                "" + finalType,
                List.of(res1.tree, res2.tree, res3.tree)));
      }
    }

    public static final class ExprAbs implements Expr {

      private final String param;
      private final AlgorithmWCompatibility body;

      public ExprAbs(String param, AlgorithmWCompatibility body) {
        this.param = param;
        this.body = body;
      }

      @Override
      public final String toString() {
        return "λ" + param + "." + body + "";
      }

      @Override
      public InferResult infer(TypeInference engine, Env env) {
        String input = env + " |- " + this;

        AlgorithmWType freshTypeVar = new AlgorithmWType.Var(
            engine.freshTypeVar());
        Env newEnv = env.copy();
        Scheme newScheme = new Scheme(List.of(), freshTypeVar);
        newEnv.env.put(param, newScheme);

        InferResult res = Expr.convertInferOrTransformToInferResult(
            body.inferOrTransformAlgorithmW(engine, newEnv),
            engine,
            newEnv);
        AlgorithmWType resultType = new Arrow(
            res.subst.apply(freshTypeVar),
            res.type);

        return new InferResult(
            res.subst,
            resultType,
            new InferenceTree(
                "T-Abs",
                input,
                resultType.toString(),
                List.of(res.tree)));
      }
    }

    public static final class ExprLet implements Expr {

      private final String param;
      private final AlgorithmWCompatibility value;
      private final AlgorithmWCompatibility body;

      public ExprLet(String param, Expr value, Expr body) {
        this.param = param;
        this.value = value;
        this.body = body;
      }

      @Override
      public final String toString() {
        return "let " + param + " = " + value + " in " + body;
      }

      @Override
      public InferResult infer(TypeInference engine, Env env) {
        String input = env + " |- " + this;

        InferResult res1 = Expr.convertInferOrTransformToInferResult(
            value.inferOrTransformAlgorithmW(engine, env),
            engine,
            env);
        Env envSubst = env.apply(res1.subst);
        Scheme generalizedType = res1.type.generalize(envSubst);

        Env newEnv = envSubst.copy();
        newEnv.env.put(param, generalizedType);

        InferResult res2 = Expr.convertInferOrTransformToInferResult(
            body.inferOrTransformAlgorithmW(engine, newEnv),
            engine,
            newEnv);
        Subst finalSubst = res2.subst.compose(res1.subst);

        return new InferResult(
            finalSubst,
            res2.type,
            new InferenceTree(
                "T-Let",
                input,
                "" + res2.type,
                List.of(res1.tree, res2.tree)));
      }
    }
  }

  public static final class TypeInference
      extends TypeDialect.TypeInferenceSolver<AlgorithmWCompatibility> {

    public TypeInference() {
    }

    public TypeVar freshTypeVar() {
      return new TypeVar();
    }

    @Override
    public Type solve(AlgorithmWCompatibility expr) {
      if (expr instanceof AlgorithmWCompatibility) {
        Env env = new Env(new HashMap<>());
        InferResult res = Expr.convertInferOrTransformToInferResult(
            expr.inferOrTransformAlgorithmW(this, env),
            this,
            env);
        return (Type) res.subst.apply(res.type);
      } else {
        throw new TypingException.UnsupportedExpression(
            TypingException.UnsupportedExpression.AlgorithmType.AlgorithmW,
            expr);
      }
    }

    public InferResult infer(Expr expr, Env env) {
      return expr.infer(this, env);
    }

    public UnifyResult unify(AlgorithmWType left, AlgorithmWType right) {
      if (right instanceof AlgorithmWType.Var) {
        return right.unify(this, left);
      }
      return left.unify(this, right);
    }
  }

  public final record Env(HashMap<String, Scheme> env) {
    @Override
    public final String toString() {
      return ("{" +
          env
              .entrySet()
              .stream()
              .map(entry -> entry.getKey() + " -> " + entry.getValue())
              .collect(Collectors.joining(", "))
          +
          "}");
    }

    /**
     * Apply the subst to this env.
     *
     * @param subst the subst to apply with
     * @return the applied env where subst is applied to this
     */
    public Env apply(Subst subst) {
      var newEnv = new HashMap<String, Scheme>(env);
      for (var entry : this.env.entrySet()) {
        newEnv.put(entry.getKey(), entry.getValue().apply(subst));
      }
      return new Env(newEnv);
    }

    /**
     * Get the free type Variables that are unbound for the whole environment
     *
     * @return
     */
    public Set<TypeVar> freeTypeVars() {
      var set = new HashSet<TypeVar>();

      for (var entry : this.env.entrySet()) {
        set.addAll(entry.getValue().freeTypeVars());
      }

      return Set.copyOf(set);
    }

    public Env copy() {
      return new Env(new HashMap<>(this.env));
    }
  }

  public final record Scheme(List<TypeVar> vars, AlgorithmWType type) {
    /**
     * Apply the subst to this scheme. First filter all bound variables from the
     * subst, then apply
     * the filtered subst to the type.
     *
     * @param subst the subst to apply with
     * @return the applied scheme where subst is applied to this
     */
    public Scheme apply(Subst subst) {
      var filtered = new HashMap<TypeVar, AlgorithmWType>(subst.types);

      for (var s : this.vars) {
        filtered.remove(s);
      }

      var newType = new Subst(filtered).apply(this.type);
      return new Scheme(this.vars, newType);
    }

    @Override
    public final String toString() {
      return ("[{" +
          this.vars
              .stream()
              .map(Object::toString)
              .collect(Collectors.joining(", "))
          +
          "}, " +
          this.type +
          "]");
    }

    /**
     * Find all non bound type variables
     *
     * @return
     */
    public Set<TypeVar> freeTypeVars() {
      var ftv = this.type.freeTypeVars();
      var set = new HashSet<TypeVar>(ftv);
      set.removeAll(this.vars);
      return Set.copyOf(set);
    }

    public AlgorithmWType instantiate(TypeInference engine) {
      Subst s = Subst.newEmpty();

      for (var typeVar : this.vars) {
        var fresh = engine.freshTypeVar();
        s.types.put(typeVar, new AlgorithmWType.Var(fresh));
      }

      return s.apply(this.type);
    }
  }

  public final record Subst(HashMap<TypeVar, AlgorithmWType> types) {
    public static Subst newEmpty() {
      return new Subst(new HashMap<>());
    }

    public static Subst newSingleton(TypeVar key, AlgorithmWType type) {
      var map = new HashMap<TypeVar, AlgorithmWType>();
      map.put(key, type);
      return new Subst(map);
    }

    @Override
    public final String toString() {
      return ("{" +
          types
              .entrySet()
              .stream()
              .map(entry -> entry.getKey() + " -> " + entry.getValue())
              .collect(Collectors.joining(", "))
          +
          "}");
    }

    public AlgorithmWType apply(AlgorithmWType type) {
      if (type instanceof Var var) {
        var t = types.get(var.tyVar);
        if (t != null) {
          return apply(t);
        } else {
          return type;
        }
      } else if (type instanceof Arrow arrow) {
        return new AlgorithmWType.Arrow(apply(arrow.from), apply(arrow.to));
      } else if (type instanceof LitType) {
        return type;
      } else if (type instanceof Tuple tuple) {
        return new Tuple(tuple.elements.stream().map(this::apply).toList());
      } else {
        throw new TypingException.UnknownType(type);
      }
    }

    /**
     * Compose this subst with another subst, by first relaying other through this
     *
     * @param other the other subst to compose with
     * @return the composed subst
     */
    public Subst compose(Subst other) {
      var otherTypes = new HashMap<TypeVar, AlgorithmWType>(other.types);
      otherTypes
          .entrySet()
          .stream()
          .forEach(entry -> entry.setValue(this.apply(entry.getValue())));

      this.types
          .entrySet()
          .stream()
          .forEach(entry -> otherTypes.putIfAbsent(entry.getKey(), entry.getValue()));

      return new Subst(otherTypes);
    }
  }

  public abstract static sealed class AlgorithmWType extends Type {

    public record UnifyResult(Subst subst, InferenceTree tree) {
      public AlgorithmWType applySubst(AlgorithmWType type) {
        return this.subst.apply(type);
      }
    }

    public Scheme generalize(Env env) {
      Set<TypeVar> ftv = this.freeTypeVars();
      Set<TypeVar> envFtv = env.freeTypeVars();

      List<TypeVar> unboundFtv = ftv
          .stream()
          .filter(ty -> !envFtv.contains(ty))
          .collect(Collectors.toList());

      return new Scheme(unboundFtv, this);
    }

    /**
     * unify both this and other to a common substitution that can be used for
     * inference
     *
     * @param other The other type to unify with.
     * @return The unification result consisting of a substitution and an inference
     *         tree.
     * @throws RuntimeException if unimplemented
     */
    public abstract UnifyResult unify(
        TypeInference engine,
        AlgorithmWType other);

    public boolean occursCheck(TypeVar ty) {
      var ftv = this.freeTypeVars();
      return ftv.contains(ty);
    }

    public abstract Set<TypeVar> freeTypeVars();

    public static final class Var extends AlgorithmWType {

      public final TypeVar tyVar;

      public Var(TypeVar tyVar) {
        this.tyVar = tyVar;
      }

      @Override
      public String toString() {
        return tyVar.toString();
      }

      @Override
      public boolean equals(Object obj) {
        return obj instanceof Var other && this.tyVar == other.tyVar;
      }

      @Override
      public int hashCode() {
        return super.hashCode();
      }

      @Override
      public UnifyResult unify(TypeInference engine, AlgorithmWType other) {
        // Maybe the two types (this and other) are actually the same type variable
        if (other instanceof Var b && this.tyVar == b.tyVar) {
          return new UnifyResult(
              Subst.newEmpty(),
              new InferenceTree(
                  "Unify-Var-Same",
                  this.toString() + " ~ " + b.toString()));
        } else if (other.occursCheck(this.tyVar)) {
          throw new TypingException.OccursCheckFailed(other, this.tyVar);
        } else {
          // In every other case, the type variable can be substituded with the concrete
          // type that is other
          var subst = Subst.newSingleton(this.tyVar, other);
          return new UnifyResult(
              subst,
              new InferenceTree(
                  "Unify-Var",
                  this.toString() + " ~ " + other.toString(),
                  other.toString() + "/" + this.toString()));
        }
      }

      @Override
      public Set<TypeVar> freeTypeVars() {
        return Set.of(this.tyVar);
      }
    }

    public static final class Arrow extends AlgorithmWType {

      public final AlgorithmWType from;
      public final AlgorithmWType to;

      public Arrow(AlgorithmWType from, AlgorithmWType to) {
        this.from = from;
        this.to = to;
      }

      @Override
      public String toString() {
        return from + " -> " + to;
      }

      @Override
      public boolean equals(Object obj) {
        return (obj instanceof Arrow other &&
            this.from.equals(other.from) &&
            this.to.equals(other.to));
      }

      @Override
      public int hashCode() {
        return super.hashCode();
      }

      @Override
      public UnifyResult unify(TypeInference engine, AlgorithmWType other) {
        if (other instanceof Arrow b) {
          UnifyResult u1 = engine.unify(this.from, b.from);
          UnifyResult u2 = engine.unify(
              u1.applySubst(this.to),
              u1.applySubst(b.to));

          Subst finalSubst = u2.subst().compose(u1.subst());

          return new UnifyResult(
              finalSubst,
              new InferenceTree(
                  "Unify-Arrow",
                  this.toString() + " ~ " + b.toString(),
                  finalSubst.toString(),
                  List.of(u1.tree, u2.tree)));
        } else {
          throw new TypingException.UnificationFailed(this, other);
        }
      }

      @Override
      public Set<TypeVar> freeTypeVars() {
        var set = new HashSet<TypeVar>();
        set.addAll(this.from.freeTypeVars());
        set.addAll(this.to.freeTypeVars());
        return Set.copyOf(set);
      }
    }

    public static final class LitType extends AlgorithmWType {

      public final String tyName;
      public final List<AlgorithmWType> parameters;

      public LitType(String tyName) {
        this.tyName = tyName;
        this.parameters = List.of();
      }

      public LitType(String tyName, List<AlgorithmWType> parameters) {
        this.tyName = tyName;
        this.parameters = List.copyOf(parameters);
      }

      @Override
      public String toString() {
        return (tyName +
            "<" +
            parameters
                .stream()
                .map(Object::toString)
                .collect(Collectors.joining(","))
            +
            ">");
      }

      @Override
      public boolean equals(Object obj) {
        return (obj instanceof LitType && ((LitType) obj).tyName.equals(this.tyName));
      }

      @Override
      public int hashCode() {
        return super.hashCode();
      }

      @Override
      public UnifyResult unify(TypeInference engine, AlgorithmWType other) {
        if (other instanceof LitType otherLit &&
            otherLit.tyName.equals(this.tyName)) {
          var subst = Subst.newEmpty();
          var trees = new ArrayList<InferenceTree>();

          if (this.parameters.size() != otherLit.parameters.size()) {
            throw new RuntimeException(
                "Parameter count mismatch: " +
                    this.parameters.size() +
                    " vs " +
                    otherLit.parameters.size());
          }

          for (int i = 0; i < this.parameters.size(); i++) {
            var result = engine.unify(
                this.parameters.get(i),
                otherLit.parameters.get(i));
            subst = result.subst.compose(subst);
            trees.add(result.tree);
          }

          return new UnifyResult(
              Subst.newEmpty(),
              new InferenceTree(
                  "Unify-Base",
                  this.toString() + " ~ " + other.toString()));
        } else {
          throw new TypingException.UnificationFailed(this, other);
        }
      }

      @Override
      public Set<TypeVar> freeTypeVars() {
        return Set.of();
      }
    }

    public static final class Tuple extends AlgorithmWType {

      public final List<AlgorithmWType> elements;

      public Tuple(List<AlgorithmWType> elements) {
        this.elements = elements;
      }

      @Override
      public String toString() {
        return ("(" +
            this.elements
                .stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "))
            +
            ")");
      }

      @Override
      public boolean equals(Object obj) {
        return (obj instanceof Tuple other && this.elements.equals(other.elements));
      }

      @Override
      public int hashCode() {
        return super.hashCode();
      }

      @Override
      public Set<TypeVar> freeTypeVars() {
        var set = new HashSet<TypeVar>();

        this.elements.stream().forEach(e -> set.addAll(e.freeTypeVars()));

        return Set.copyOf(set);
      }

      @Override
      public UnifyResult unify(TypeInference engine, AlgorithmWType other) {
        if (other instanceof Tuple b) {
          if (this.elements.size() != b.elements.size()) {
            throw new TypingException.TupleSizeMismatch(
                this.elements.size(),
                b.elements.size());
          }
          Subst subst = Subst.newEmpty();
          ArrayList<InferenceTree> trees = new ArrayList<>();

          for (int i = 0; i < this.elements.size(); i++) {
            UnifyResult result = engine.unify(
                subst.apply(this.elements.get(i)),
                subst.apply(b.elements.get(i)));
            subst = result.subst().compose(subst);
            trees.add(result.tree);
          }

          return new UnifyResult(
              subst,
              new InferenceTree(
                  "Unify-Tuple",
                  this + " ~ " + other,
                  subst + "",
                  List.copyOf(trees)));
        } else {
          throw new TypingException.UnificationFailed(this, other);
        }
      }
    }
  }
}

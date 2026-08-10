package dgir.core.ir.types.algorithmw;

import dgir.core.ir.Value;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;
import dgir.core.ir.types.InferenceTree;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeDialect;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.AlgorithmWType.LitType;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.AlgorithmWType.Tuple;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.AlgorithmWType.UnifyResult;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.AlgorithmWType.Var;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.AlgorithmWType.Arrow;
import dgir.core.ir.types.algorithmw.AlgorithmWInference.Expr.InferResult;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.compatibility.InferOrTransformResult;
import dgir.core.ir.types.compatibility.InferResultMarker;
import dgir.core.ir.types.compatibility.ConvertedOperationBuffer;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

public final class AlgorithmWInference
    extends
    TypeDialect<InferOrTransformResult<dgir.core.ir.types.algorithmw.AlgorithmWInference.Expr.InferResult, dgir.core.ir.types.algorithmw.AlgorithmWInference.Expr>, ExprOrOperator<dgir.core.ir.types.algorithmw.AlgorithmWInference.Expr>, dgir.core.ir.types.algorithmw.AlgorithmWInference.Expr, dgir.core.ir.types.algorithmw.AlgorithmWInference.AlgorithmWType> {

  private static Optional<TypeInference> instance = Optional.empty();

  @Override
  public TypeInferenceSolver<ExprOrOperator<Expr>, Expr> getSolverInstance() {
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

  private static AlgorithmWType generalNominalTypeToInferenceType(GeneralParameterizedNominalType type) {
    List<AlgorithmWType> paramTypes = type.getTypedParameters().stream().map(param -> switch (param) {
      case GeneralTypeParameter.Concrete con -> AlgorithmWInference.generalNominalTypeToInferenceType(con.ty());
      case GeneralTypeParameter.Unknown unk -> new AlgorithmWType.Var(new TypeVar());
    }).toList();

    return new AlgorithmWType.LitType(type.getIdent(), paramTypes);
  }

  public static interface Expr extends Expression, ExprOrOperator<AlgorithmWInference.Expr> {

    @Override
    default boolean isExpr() {
      return true;
    }

    @Override
    default boolean isOperator() {
      return false;
    }

    @Override
    default Expr getExpr() {
      return this;
    }

    public static record InferResult(
        Subst subst,
        AlgorithmWType type,
        InferenceTree tree) implements InferResultMarker<AlgorithmWType> {
    }

    public abstract InferResult infer(TypeInference engine, Env env);

    public static final class ExprAnn implements Expr {

      private final ExprOrOperator<Expr> expr;
      private final AlgorithmWType type;

      public ExprAnn(ExprOrOperator<Expr> expr, AlgorithmWType type) {
        this.expr = expr;
        this.type = type;
      }

      @Override
      public InferResult infer(TypeInference engine, Env env) {

        InferResult res = engine.infer(expr, env);

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

    public static final class ExprLit implements Expr {

      private GeneralParameterizedNominalType value;

      public ExprLit(Literal value) {
        this.value = value.toParameterizedNominalType();
      }

      @Override
      public final String toString() {
        return value.toString();
      }

      @Override
      public InferResult infer(TypeInference engine, Env env) {
        var algoWType = AlgorithmWInference.generalNominalTypeToInferenceType(value);
        return new InferResult(
            Subst.newEmpty(),
            algoWType,
            new InferenceTree(
                "T-" + algoWType,
                env + " |- " + this,
                algoWType.toString(),
                List.of()));
      }
    }

    public static final class ExprTuple implements Expr {

      private List<ExprOrOperator<Expr>> elements;

      public ExprTuple(List<ExprOrOperator<Expr>> elements) {
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

      private final Symbol name;

      public ExprVar(Symbol name) {
        this.name = name;
      }

      @Override
      public final String toString() {
        return name + "";
      }

      @Override
      public InferResult infer(TypeInference engine, Env env) {
        String input = env + " |- " + this;

        Scheme scheme = env.env.get(name);
        if (scheme != null) {
          AlgorithmWType instantiated = scheme.instantiate(engine, this.name);
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

      private final ExprOrOperator<Expr> func;
      private final List<ExprOrOperator<Expr>> args;

      public ExprApp(
          ExprOrOperator<Expr> func,
          ExprOrOperator<Expr> arg) {
        this.func = func;
        this.args = List.of(arg);
      }

      public ExprApp(
          ExprOrOperator<Expr> func,
          List<ExprOrOperator<Expr>> args) {
        this.func = func;
        this.args = List.copyOf(args);
      }

      @Override
      public final String toString() {
        if (args.size() > 1) {
          return func + " (" + args.stream().map(Object::toString).collect(Collectors.joining(",")) + ")";
        } else {
          return func + " " + args.get(0);
        }
      }

      @Override
      public InferResult infer(TypeInference engine, Env env) {
        String input = env + " |- " + this;

        AlgorithmWType resultType = new AlgorithmWType.Var(new TypeVar());

        InferResult funcInferRes = engine.infer(func, env);

        Env envSubst = env.apply(funcInferRes.subst);

        Subst finalSubst = funcInferRes.subst;
        AlgorithmWType builtArrowType = resultType;
        ArrayList<InferenceTree> trees = new ArrayList<>();
        trees.add(funcInferRes.tree);

        for (var arg : this.args.reversed()) {
          InferResult argRes = engine.infer(arg, envSubst);
          finalSubst = argRes.subst.compose(finalSubst);
          envSubst = envSubst.apply(finalSubst);
          builtArrowType = new Arrow(argRes.type, builtArrowType);
          trees.add(argRes.tree);
        }

        AlgorithmWType funcTypeSubst = finalSubst.apply(funcInferRes.type);
        UnifyResult unifyRes = engine.unify(funcTypeSubst, builtArrowType);

        finalSubst = unifyRes.subst.compose(finalSubst);
        // NOTE: subst the restltType not the builtArrowType, as the the arrow type is only needed for unification to build the final subst
        resultType = unifyRes.subst.apply(resultType);

        trees.add(unifyRes.tree);

        return new InferResult(
            finalSubst,
            resultType,
            new InferenceTree(
                "T-App",
                input,
                "" + builtArrowType,
                List.copyOf(trees)));
      }
    }

    public static final class ExprAbs implements Expr {

      private final List<Symbol> params;
      private final ExprOrOperator<Expr> body;

      public ExprAbs(Symbol param, ExprOrOperator<Expr> body) {
        this.params = List.of(param);
        this.body = body;
      }

      public ExprAbs(List<Symbol> params, ExprOrOperator<Expr> body) {
        this.params = List.copyOf(params);
        this.body = body;
      }

      @Override
      public final String toString() {
        if (params.size() > 1) {
          return "λ(" + params.stream().map(Object::toString).collect(Collectors.joining(",")) + ")." + body + "";
        } else {
          return "λ" + params.get(0) + "." + body + "";

        }
      }

      @Override
      public InferResult infer(TypeInference engine, Env env) {
        String input = env + " |- " + this;

        Env newEnv = env.copy();
        ArrayList<Pair<Symbol, TypeVar>> paramsAndTypeVars = new ArrayList<>();
        for (var param : this.params) {
          var typeVar = new TypeVar();
          AlgorithmWType freshTypeVar = new AlgorithmWType.Var(typeVar);
          Scheme newScheme = new Scheme(List.of(), freshTypeVar);
          newEnv.env.put(param, newScheme);
          paramsAndTypeVars.add(Pair.of(param, typeVar));
        }

        Scope functionScope = newEnv.addScope();
        AlgorithmWType retTypeVar = new AlgorithmWType.Var(new TypeVar());
        Subst subst = Subst.newEmpty();
        ArrayList<InferenceTree> trees = new ArrayList<>();

        // Unify all return values. If return values do not have the same type, error
        // will get thrown here!
        for (var retType : functionScope.getAllReturnTypesInScope()) {
          var unifyRes = engine.unify(retTypeVar, subst.apply(retType));
          subst = unifyRes.subst.compose(subst);
          retTypeVar = subst.apply(retTypeVar);
          trees.add(unifyRes.tree);
        }

        // In terms of IR, the body will not have a direct return parameter. Though it
        // can be assumed,
        // that the last expression is always a return in a function, even if nothing is
        // returned.
        // Hence, It would make sense to treat the last return expression as an
        // expression that actually returns a value of type T.
        // This must then be unified with the retTypeVar collected from all return
        // statements (if present)
        InferResult res = engine.infer(body, newEnv);
        retTypeVar = res.subst.apply(retTypeVar);
        subst = res.subst.compose(subst);
        UnifyResult retTypeUnify = engine.unify(res.type, retTypeVar);
        subst = retTypeUnify.subst.compose(subst);

        AlgorithmWType resultType = subst.apply(retTypeVar);
        for (var paramAndTypeVar : paramsAndTypeVars.reversed()) {
          resultType = new Arrow(subst.apply(new AlgorithmWType.Var(paramAndTypeVar.getRight())), resultType);
        }

        return new InferResult(
            subst,
            resultType,
            new InferenceTree(
                "T-Abs",
                input,
                resultType.toString(),
                List.of(res.tree)));
      }
    }

    /**
     * ExprLet is a multi let statement that infers multiple let expressions at the
     * same time.
     *
     * @implNote The lets are considered global scope: i.e. the let values are first
     *           assigned to a new {@link TypeVar}, that is then unified with the
     *           inferred assigned type.
     */
    public static final class ExprLet implements Expr {

      private final List<Pair<Symbol, ExprOrOperator<Expr>>> bindings;
      private final ExprOrOperator<Expr> body;

      public ExprLet(Symbol param, ExprOrOperator<Expr> value, ExprOrOperator<Expr> body) {
        this.bindings = List.of(Pair.of(param, value));
        this.body = body;
      }

      public ExprLet(List<Pair<Symbol, ExprOrOperator<Expr>>> bindings, ExprOrOperator<Expr> body) {
        this.bindings = List.copyOf(bindings);
        this.body = body;
      }

      @Override
      public final String toString() {
        return "let (" + this.bindings.stream().map(Object::toString).collect(Collectors.joining(", ")) + ") in "
            + body;
      }

      @Override
      public InferResult infer(TypeInference engine, Env env) {
        String input = env + " |- " + this;

        Env newEnv = env.copy();
        ArrayList<Triple<Symbol, AlgorithmWType, ExprOrOperator<Expr>>> notUnified = new ArrayList<>(
            this.bindings.size());
        for (var binding : this.bindings) {
          var typeVar = new TypeVar();
          notUnified.add(Triple.of(binding.getLeft(), new AlgorithmWType.Var(typeVar), binding.getRight()));
          newEnv.env.put(binding.getLeft(), new AlgorithmWType.Var(typeVar).generalize(newEnv));
        }

        Subst subst = Subst.newEmpty();
        ArrayList<InferenceTree> trees = new ArrayList<>();

        for (var binding : notUnified) {
          var param = binding.getLeft();
          var typeVar = binding.getMiddle();
          var value = binding.getRight();

          InferResult res1 = engine.infer(value, newEnv);
          subst = res1.subst.compose(subst);
          Env envSubst = newEnv.apply(subst);

          UnifyResult unifyRes = engine.unify(typeVar, res1.type);
          subst = unifyRes.subst.compose(subst);
          envSubst = envSubst.apply(subst);

          Scheme generalizedType = subst.apply(res1.type).generalize(envSubst);

          newEnv = envSubst.copy();
          newEnv.env.put(param, generalizedType);
          trees.add(res1.tree);
        }

        InferResult res2 = engine.infer(body, newEnv);

        Subst finalSubst = res2.subst.compose(subst);
        trees.add(res2.tree);

        return new InferResult(
            finalSubst,
            res2.type,
            new InferenceTree(
                "T-Let*",
                input,
                "" + res2.type,
                List.copyOf(trees)));
      }
    }

    public class ExprCustom implements Expr {

      @FunctionalInterface
      public interface InferFunction {
        InferResult infer(TypeInference engine, Env env, Object data);
      }

      private Object data;
      private InferFunction inferFn;

      public ExprCustom(
          Object data, InferFunction inferFn) {
        this.data = data;
        this.inferFn = inferFn;
      }

      @Override
      public InferResult infer(TypeInference engine, Env env) {
        return this.inferFn.infer(engine, env, data);
      }

    }
  }

  public static final class TypeInference
      extends TypeDialect.TypeInferenceSolver<ExprOrOperator<Expr>, Expr> {

    private ConvertedOperationBuffer<Expr> operationToExprBuffer;

    public TypeInference() {
      this(new TypeDialectConverterRegistry());
    }

    public TypeInference(TypeDialectConverterRegistry registry) {
      super(registry);
      operationToExprBuffer = new ConvertedOperationBuffer<>();
    }

    @Override
    public Expr generalBlockToInferenceExpr(GeneralBlock block) {
      ArrayList<Pair<Symbol, ExprOrOperator<Expr>>> bindings = new ArrayList<>();
      Optional<Value> lastValue = Optional.empty();

      for (var op : block.getOperations()) {
        var opOutput = op.getOutput();
        if (opOutput.isPresent()) {
          bindings.add(Pair.of(Symbol.of(opOutput.get().getValue()), ExprOrOperator.of(op)));
          lastValue = Optional.of(opOutput.get().getValue());
        } else {
          /*
           * NOTE: handle everything as a returnable value, even though something like a
           * function is not actually a expression! This is done to correctly typecheck
           * each function and their parameters!
           */
          var val = new Value();
          bindings.add(Pair.of(Symbol.of(val), ExprOrOperator.of(op)));
          lastValue = Optional.of(val);
        }
      }

      if (lastValue.isPresent()) {
        return new Expr.ExprLet(bindings, new Expr.ExprVar(Symbol.of(lastValue.get())));
      } else {
        return new Expr.ExprLet(bindings, new Expr.ExprLit(new Literal.Unit()));
      }
    }

    @Override
    public Type solve(ExprOrOperator<Expr> expr) {
      if (expr.isExpr()) {
        Env env = new Env();
        InferResult res = this.infer(expr, env);
        return (Type) res.subst.apply(res.type);
      } else {
        throw new TypingException.UnsupportedExpression(
            TypingException.UnsupportedExpression.AlgorithmType.AlgorithmW,
            expr);
      }
    }

    public InferResult infer(ExprOrOperator<Expr> expr, Env env) {
      if (expr.isExpr()) {
        return expr.getExpr().infer(this, env);
      } else if (expr.isOperator()) {
        var op = expr.getOp();
        return this.operationToExprBuffer.operationToExpr(op, this.registry, Expr.class).infer(this, env);
      }
      throw new RuntimeException("unimplemented for OPs");
    }

    public UnifyResult unify(AlgorithmWType left, AlgorithmWType right) {
      if (right instanceof AlgorithmWType.Var) {
        return right.unify(this, left);
      }
      return left.unify(this, right);
    }
  }

  public static final class Scope {
    private ArrayList<AlgorithmWType> returnTypes;

    public Scope() {
      this.returnTypes = new ArrayList<>();
    }

    public List<AlgorithmWType> getAllReturnTypesInScope() {
      return List.copyOf(this.returnTypes);
    }

    public void addReturnType(AlgorithmWType retType) {
      this.returnTypes.add(retType);
    }
  }

  public static final class Env {
    private HashMap<Symbol, Scheme> env;
    private ArrayList<Scope> scopeStack;

    public Env() {
      this(new HashMap<>(), new ArrayList<>());
    }

    public Env(HashMap<Symbol, Scheme> env, ArrayList<Scope> scopeStack) {
      this.env = env;
      this.scopeStack = scopeStack;
    }

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
      var newEnv = new HashMap<Symbol, Scheme>(env);
      for (var entry : this.env.entrySet()) {
        newEnv.put(entry.getKey(), entry.getValue().apply(subst));
      }
      return new Env(newEnv, new ArrayList<>(this.scopeStack));
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
      return new Env(new HashMap<>(this.env), new ArrayList<>(this.scopeStack));
    }

    public Scope addScope() {
      var scope = new Scope();
      this.scopeStack.add(scope);
      return scope;
    }

    public Optional<Scope> popScope() {
      return Optional.ofNullable(this.scopeStack.removeLast());
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

    public AlgorithmWType instantiate(TypeInference engine, Symbol value) {
      Subst s = Subst.newEmpty();

      for (var typeVar : this.vars) {
        var fresh = new TypeVar(value);
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
          // In the case that a substitution for a type is found, make sure to inform a
          // possibly present value that this substitution was performed and a type is
          // possibly solved.
          var resType = apply(t);
          var.tyVar.provideSolution(resType);
          return resType;
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

      public final TypeIdent tyName;
      public final List<AlgorithmWType> parameters;

      public LitType(TypeIdent tyName) {
        this.tyName = tyName;
        this.parameters = List.of();
      }

      public LitType(TypeIdent tyName, List<AlgorithmWType> parameters) {
        this.tyName = tyName;
        this.parameters = List.copyOf(parameters);
      }

      @Override
      public String toString() {
        return (tyName +
            (parameters.isEmpty() ? ""
                : "<" +
                    parameters
                        .stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(","))
                    +
                    ">"));
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

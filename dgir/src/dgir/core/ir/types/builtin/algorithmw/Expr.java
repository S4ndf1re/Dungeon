package dgir.core.ir.types.builtin.algorithmw;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.Value;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.InferenceTree;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.compatibility.Scope;
import dgir.core.ir.types.traits.IExpressionCell;
import dgir.core.ir.types.traits.IInstantiable;
import dgir.core.ir.types.traits.IVariable;
import dgir.core.ir.types.traits.IAbstraction;
import dgir.core.ir.types.traits.IApplication;

public abstract class Expr extends Expression<Expr, AlgorithmWType>
    implements IInstantiable<Expr, AlgorithmWType, Subst, TypeInference> {

  protected Expr() {
  }

  protected Expr(Expr other) {
    super(other);
  }

  // Make sure, that exprs always equals via object reference (needed for in-set
  // storage!)
  @Override
  public boolean equals(Object obj) {
    return obj instanceof Expr && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public Expr getCellFor(Expr origin, Expr assignment) {
    return new ExprCell(origin, assignment);
  }

  /**
   * Infer the type of the expression. This method MUST be implemented for every
   * {@link Expr}.
   * After inferring the type (the return value), the engine automatically stores
   * the inferred type within the inferred expression.
   *
   * @param engine
   * @param env
   * @return the inferred {@link AlgorithmWType} and the resulting {@link Subst},
   *         combined into an {@link InferenceTree}
   */
  public abstract InferResult infer(TypeInference engine, Env env);

  /**
   * ExprCell is an inherently mutable cell, that just references a changable
   * value in place!
   * Overall, it will just copy the behaviour of the inner expr!
   *
   * No instantiation, replace and other operations are permitted.
   *
   * <p>
   * NOTE: it can be expected, that the cell may only ever occur in recursive
   * functions to trace back the solutions to the original expression!
   * Hence, this expression represents a dead end and is therefore a recursion
   * breaker during instantiation!
   */
  private static final class ExprCell extends Expr implements IExpressionCell<Expr, AlgorithmWType> {
    private Expr reference;
    private Expr cellValue;

    // public Expr reference() {
    // return this.reference;
    // }
    //
    // public Expr cellValue() {
    // return this.cellValue;
    // }

    public ExprCell(Expr reference, Expr cellValue) {
      this.reference = reference;
      this.cellValue = cellValue;
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public Optional<AlgorithmWType> getInferredType() {
      return cellValue.getInferredType();
    }

    @Override
    public void setInferredType(Optional<AlgorithmWType> inferredType) {
      cellValue.setInferredType(inferredType);
    }

    @Override
    public Optional<Operation> getUnderlyingOperation() {
      return cellValue.getUnderlyingOperation();
    }

    @Override
    public void setParentScopeExpression(Optional<Expr> expr, Optional<Integer> position) {
      this.cellValue.setParentScopeExpression(expr, position);
    }

    @Override
    public Optional<Expr> getParentScopeExpr() {
      return this.cellValue.getParentScopeExpr();
    }

    @Override
    public Optional<Integer> getParentScopePosition() {
      return this.cellValue.getParentScopePosition();
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(cellValue);
    }

    @Override
    public void reinstantiateSymbols() {
      // Do nothing, as the referenced expression is already expected to be
      // re-instantiated!
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      return this;
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return false;
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      throw new UnsupportedOperationException("The memory cell is expected to only exist after instantiation!");
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      throw new UnsupportedOperationException("The memory cell is expected to only exist after instantiation!");
    }

    @Override
    public Expr unwrap() {
      return this.cellValue;
    }

    @Override
    public void replaceIfMatches(Expr reference, Expr replacement) {
      if (reference == this.reference) {
        this.cellValue = replacement;
      }
    }

    @Override
    public Expr copy() {
      return this;
    }

  }

  public static final class ExprAnn extends Expr {
    private final Expr expr;
    private final AlgorithmWType type;

    public Expr expr() {
      return this.expr.unwrapOrThis();
    }

    public AlgorithmWType type() {
      return this.type;
    }

    public ExprAnn(Expr expr, AlgorithmWType type) {
      this.expr = expr;
      this.type = type;
    }

    public ExprAnn(ExprAnn other) {
      super(other);
      this.expr = other.expr;
      this.type = other.type;
    }

    public ExprAnn(ExprAnn other, Expr expr, AlgorithmWType type) {
      super(other);
      this.expr = expr;
      this.type = type;
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(this.expr);
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {

      InferResult res = engine.infer(expr, env);

      var unifyRes = engine.unify(res.type(), type);

      return new InferResult(
          type,
          new InferenceTree(
              "T-Ann",
              env + " |- " + this,
              type.toString(),
              List.of(res.tree(), unifyRes)));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprAnn ann && this.expr.equals(ann.expr) && this.type.equals(ann.type)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.expr, this.type, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      return new ExprAnn(this, this.expr.replaceSymbol(original, replacement), this.type);
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.expr.containsSymbol(symbol);
    }

    @Override
    public Expr copy() {
      return new ExprAnn(this);
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      // Simply return the inner as fully instantiated!
      return this.expr.instantiate(engine, env, solution);
    }
  }

  public static final class ExprLit extends Expr {

    private Literal value;

    public Literal value() {
      return this.value;
    }

    public ExprLit(Literal value) {
      this.value = value;
    }

    @SuppressWarnings("unused")
    private ExprLit(ExprLit other) {
      super(other);
      this.value = other.value;
    }

    @Override
    public final String toString() {
      return value.toString();
    }

    @Override
    public List<Expr> getChildren() {
      return List.of();
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return false;
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      var algoWType = engine.generalNominalTypeToInferenceType(value.toParameterizedNominalType(), null);
      return new InferResult(
          algoWType.getLeft(),
          new InferenceTree(
              "T-" + algoWType,
              env + " |- " + this,
              algoWType.toString(),
              List.of()));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprLit lit && this.value.equals(lit.value) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.value, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      return this;
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      return this;
    }

    @Override
    public Expr copy() {
      return new ExprLit(this);
    }
  }

  public static final class ExprTuple extends Expr {

    private final List<Expr> elements;

    public List<Expr> elements() {
      return this.elements.stream().map(Expr::unwrapOrThis).toList();
    }

    @Override
    public Expr copy() {
      return new ExprTuple(this);
    }

    public ExprTuple(List<Expr> elements) {
      this.elements = elements;
    }

    public ExprTuple(Expr... elements) {
      ArrayList<Expr> elems = new ArrayList<>();
      for (var elem : elements) {
        elems.add(elem);
      }

      this.elements = List.copyOf(elems);
    }

    public ExprTuple(ExprTuple other) {
      super(other);
      this.elements = List.copyOf(other.elements);
    }

    public ExprTuple(ExprTuple other, List<Expr> elements) {
      super(other);
      this.elements = List.copyOf(elements);
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
    public List<Expr> getChildren() {
      return this.elements.stream().map(ExprOrOperator::getExpr).toList();
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.elements.stream().anyMatch(elem -> elem.containsSymbol(symbol));
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;
      ArrayList<AlgorithmWType> types = new ArrayList<>();
      ArrayList<InferenceTree> trees = new ArrayList<>();
      Env currentEnv = env.copy();

      for (var expr : elements) {
        var res = engine.infer(expr, currentEnv);
        types.add(res.type());
        trees.add(res.tree());
      }

      var resultType = new AlgorithmWType.Tuple(List.copyOf(types));

      return new InferResult(
          resultType,
          new InferenceTree(
              "T-Tuple",
              input,
              "" + resultType,
              List.copyOf(trees)));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprTuple other && this.elements.equals(other.elements) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.elements, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      return new ExprTuple(this,
          this.elements.stream().map(elem -> elem.replaceSymbol(original, replacement)).toList());
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      return new ExprTuple(this, this.elements.stream().map(elem -> elem.instantiate(engine, env, solution)).toList());
    }
  }

  public static final class ExprVar extends Expr implements IVariable<Expr, AlgorithmWType> {

    private final Symbol<Expr, AlgorithmWType> name;

    public Symbol<Expr, AlgorithmWType> name() {
      return this.name;
    }

    public ExprVar(Symbol<Expr, AlgorithmWType> name) {
      this.name = name;
    }

    @SuppressWarnings("unused")
    private ExprVar(ExprVar other) {
      super(other);
      this.name = other.name;
    }

    private ExprVar(ExprVar other, Symbol<Expr, AlgorithmWType> name) {
      super(other);
      this.name = name;
    }

    @Override
    public final String toString() {
      return name + "";
    }

    @Override
    public Symbol<Expr, AlgorithmWType> getReferencedVariable() {
      return this.name;
    }

    @Override
    public List<Expr> getChildren() {
      return List.of();
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.name.equals(symbol);
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;

      Scheme scheme = env.get(name);
      if (scheme != null) {
        AlgorithmWType instantiated = scheme.instantiate(engine);
        return new InferResult(
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

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprVar other && this.name.equals(other.name) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.name, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      if (this.name.equals(original)) {
        return new ExprVar(this, replacement);
      }
      return this;
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      // Nothing to instantiate;
      return this;
    }

    @Override
    public Expr copy() {
      return new ExprVar(this);
    }
  }

  public static final class ExprApp extends Expr implements IApplication<Expr, AlgorithmWType> {

    private final Expr func;
    private final List<Expr> args;

    public Expr func() {
      return this.func.unwrapOrThis();
    }

    public List<Expr> args() {
      return this.args.stream().map(Expr::unwrapOrThis).toList();
    }

    private Optional<AlgorithmWType> inferredFunctionType;

    public ExprApp(
        Expr func,
        Expr arg) {
      this.func = func;
      this.args = List.of(arg);
      this.inferredFunctionType = Optional.empty();
    }

    public ExprApp(
        Expr func,
        List<Expr> args) {
      this.func = func;
      this.args = List.copyOf(args);
      this.inferredFunctionType = Optional.empty();
    }

    public ExprApp(
        ExprApp other) {
      super(other);
      this.func = other.func;
      this.args = List.copyOf(other.args);
      this.inferredFunctionType = other.inferredFunctionType;
    }

    public ExprApp(ExprApp other, Expr func,
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
        var newSolution = solution.expand(engine, funcExpr, this.inferredFunctionType.get());

        return new ExprApp(this, this.func.instantiate(engine, env, newSolution),
            this.args.stream().map(arg -> arg.instantiate(engine, env, newSolution)).toList());
      } else {
        return new ExprApp(this, this.func.instantiate(engine, env, solution),
            this.args.stream().map(arg -> arg.instantiate(engine, env, solution)).toList());
      }

    }

    @Override
    public Expr copy() {
      return new ExprApp(this);
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;

      AlgorithmWType resultType = new AlgorithmWType.Var(new TypeVar<>(engine.getCurrentLevel()));

      InferResult funcInferRes = engine.infer(func, env);

      AlgorithmWType builtArrowType = resultType;
      ArrayList<InferenceTree> trees = new ArrayList<>();
      trees.add(funcInferRes.tree());

      for (var arg : this.args.reversed()) {
        InferResult argRes = engine.infer(arg, env);
        builtArrowType = new AlgorithmWType.Arrow(argRes.type(), builtArrowType);
        trees.add(argRes.tree());
      }

      // NOTE: in case the APP parameters are empty, i.e. a function without
      // parameters is configured, the APP behaviour and the ABS behaviour are
      // identical and treat the function type as a Unit -> t0. This means, that the
      // final
      // type is an arrow type that accepts a unit value as a parameter.
      if (builtArrowType == resultType) {
        builtArrowType = new AlgorithmWType.Arrow(new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_UNIT),
            builtArrowType);
      }

      this.inferredFunctionType = Optional.of(funcInferRes.type());
      var unifyRes = engine.unify(funcInferRes.type(), builtArrowType);

      trees.add(unifyRes);

      return new InferResult(
          resultType,
          new InferenceTree(
              "T-App",
              input,
              "" + builtArrowType,
              List.copyOf(trees)));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprApp other && this.func.equals(other.func) && this.args.equals(other.args)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.func, this.args, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      var newFunc = this.func.replaceSymbol(original, replacement);
      var newArgs = this.args.stream().map(arg -> arg.replaceSymbol(original, replacement)).toList();

      var newApp = new ExprApp(this, newFunc, newArgs);
      return newApp;
    }

    public Optional<AlgorithmWType> getInferredFunctionType() {
      return this.inferredFunctionType;
    }
  }

  public static final class ExprAbs extends Expr implements IAbstraction<Expr, AlgorithmWType> {

    private List<Symbol<Expr, AlgorithmWType>> params;
    private Expr body;

    public List<Symbol<Expr, AlgorithmWType>> params() {
      return List.copyOf(this.params);
    }

    public Expr body() {
      return this.body.unwrapOrThis();
    }

    public ExprAbs(Symbol<Expr, AlgorithmWType> param, Expr body) {
      this.params = List.of(param);
      this.body = body;
    }

    public ExprAbs(List<Symbol<Expr, AlgorithmWType>> params, Expr body) {
      this.params = List.copyOf(params);
      this.body = body;
    }

    public ExprAbs(ExprAbs other) {
      super(other);
      this.params = List.copyOf(other.params);
      this.body = other.body;
    }

    public ExprAbs(ExprAbs other, List<Symbol<Expr, AlgorithmWType>> params, Expr body) {
      super(other);
      this.params = List.copyOf(params);
      this.body = body;
    }

    @Override
    public final String toString() {
      if (params.size() > 1) {
        return "λ(" + params.stream().map(Object::toString).collect(Collectors.joining(",")) + ")." + body + "";
      } else if (!params.isEmpty()) {
        return "λ" + params.get(0) + "." + body + "";
      } else {
        return "λ()" + "." + body + "";
      }
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(this.body.getExpr());
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.params.stream().anyMatch(param -> param.equals(symbol)) || this.body.containsSymbol(symbol);
    }

    @Override
    public List<Symbol<Expr, AlgorithmWType>> getAbstractionsOverSymbols() {
      return this.params();
    }

    @Override
    public Expr getAbstractionBody() {
      return this.body();
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;

      Env newEnv = env.copy();
      ArrayList<Pair<Symbol<Expr, AlgorithmWType>, TypeVar<AlgorithmWType>>> paramsAndTypeVars = new ArrayList<>();
      for (var param : this.params) {
        var typeVar = new TypeVar<AlgorithmWType>(engine.getCurrentLevel());
        AlgorithmWType freshTypeVar = new AlgorithmWType.Var(typeVar);
        Scheme newScheme = new Scheme(List.of(), freshTypeVar);
        newEnv.put(param, newScheme);
        paramsAndTypeVars.add(Pair.of(param, typeVar));
      }

      Scope<AlgorithmWType> functionScope = newEnv.addScope();
      AlgorithmWType retTypeVar = new AlgorithmWType.Var(new TypeVar<>(engine.getCurrentLevel()));
      InferResult res = engine.infer(body, newEnv);
      ArrayList<InferenceTree> trees = new ArrayList<>();

      // Unify all return values. If return values do not have the same type, error
      // will get thrown here!
      for (var retType : functionScope.getAllReturnTypesInScope()) {
        var unifyRes = engine.unify(retTypeVar, retType);
        trees.add(unifyRes);
      }

      // In terms of IR, the body will not have a direct return parameter. Though it
      // can be assumed,
      // that the last expression is always a return in a function, even if nothing is
      // returned.
      // Hence, It would make sense to treat the last return expression as an
      // expression that actually returns a value of type T.
      // This must then be unified with the retTypeVar collected from all return
      // statements (if present)
      var retTypeUnify = engine.unify(res.type(), retTypeVar);

      AlgorithmWType appliedRetType = retTypeVar;
      AlgorithmWType resultType = appliedRetType;
      for (var paramAndTypeVar : paramsAndTypeVars.reversed()) {
        resultType = new AlgorithmWType.Arrow(new AlgorithmWType.Var(paramAndTypeVar.getRight()),
            resultType);
      }

      // NOTE: in case the ABS parameters are empty, i.e. a function without
      // parameters is configured, the APP behaviour and the ABS behaviour are
      // identical and treat the function type as a Unit -> t0. This means, that the
      // final
      // type is an arrow type that accepts a unit value as a parameter.
      if (resultType == appliedRetType) {
        resultType = new AlgorithmWType.Arrow(new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_UNIT), resultType);
      }

      return new InferResult(
          resultType,
          new InferenceTree(
              "T-Abs",
              input,
              resultType.toString(),
              List.of(res.tree(), retTypeUnify)));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprAbs other && this.params.equals(other.params) && this.body.equals(other.body)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.params, this.body, super.hashCode());
    }

    @Override
    public void reinstantiateSymbols() {
      var inferredType = this.getInferredType();
      assert inferredType.isPresent() : "can only reinstante expression values if the expression is well typed!";

      // In cases where the type is not fully specified, the instantiation actually
      // failed!
      if (!inferredType.get().isFullySpecified()) {
        return;
      }

      ArrayList<Symbol<Expr, AlgorithmWType>> newParams = new ArrayList<>(this.params.size());
      var currentParamType = inferredType.get();
      for (int i = 0; i < this.params.size(); i++) {
        assert currentParamType instanceof AlgorithmWType.Arrow;
        var arrowType = (AlgorithmWType.Arrow) currentParamType;

        assert arrowType.from.deref() instanceof AlgorithmWType.LitType
            : "expected fully type literal, received " + arrowType;
        var litType = arrowType.from.deref();

        var nominalType = litType.asTypeParameter().getConcrete();
        var irType = Type.fromGeneralParameterizedNominalType(nominalType);
        var debugInfo = params.get(i).getValue().getDebugInfo();

        newParams.add(Symbol.of(new Value(irType, debugInfo)));
      }

      var oldParams = List.copyOf(this.params);
      this.params = newParams;
      var bodyExpr = this.body;

      for (int i = 0; i < newParams.size(); i++) {
        bodyExpr = bodyExpr.replaceSymbol(newParams.get(i), oldParams.get(i));
      }

      this.body = bodyExpr;
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      // If the symbol is shadowed by one of the bound values, don't replace in the
      // body!
      if (this.params.stream().anyMatch(param -> param.equals(original))) {
        return this;
      }

      // Only replace in the body, as the functions parameters are always the same
      // value!
      return new ExprAbs(this, this.params, body.replaceSymbol(original, replacement));
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      return new ExprAbs(this, List.copyOf(this.params), this.body.instantiate(engine, env, solution));
    }

    @Override
    public Expr copy() {
      return new ExprAbs(this);
    }

  }

  /**
   * ExprLetSeq is a multi let statement that infers multiple let
   * expressions at the
   * same time.
   *
   * <p>
   * This is mainly considered to be useful with sequential definitions, i.e.
   * normal blocks, and thus
   * should be expected to be only used with sequential block operations that may
   * even allow shadowing!
   *
   * <p>
   * As all expresions are considered local and are only accessible by following
   * expressions, to use this with global expressions, for example for recursion,
   * ExprLetRec is required!
   */
  public static final class ExprLetSeq extends Expr {

    private List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings;
    private Expr body;

    public ExprLetSeq(Symbol<Expr, AlgorithmWType> param, Expr value,
        Expr body) {
      this.bindings = List.of(Pair.of(param, value));
      this.body = body;
    }

    public ExprLetSeq(List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings,
        Expr body) {
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public ExprLetSeq(ExprLetSeq other) {
      super(other);
      this.bindings = List.copyOf(other.bindings);
      this.body = other.body;
    }

    public ExprLetSeq(ExprLetSeq other, List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings,
        Expr body) {
      super(other);
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings() {
      return this.bindings.stream()
          .map(bnd -> Pair.of(bnd.getLeft(), bnd.getRight().unwrapOrThis())).toList();
    }

    public Expr body() {
      return this.body.unwrapOrThis();
    }

    @Override
    public final String toString() {
      return "let (" + this.bindings.stream().map(Object::toString).collect(Collectors.joining(", ")) + ") in "
          + body;
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      var newLetExpr = new ExprLetSeq(this,
          List.copyOf(this.bindings),
          new Expr.ExprLit(new Literal.Unit()));

      // This line is key, as the defining scope expression, in this case `newLetExpr`
      // is bound to the scope
      var newEnv = new InstEnv<Expr, AlgorithmWType, Subst>(env, newLetExpr);
      for (int i = 0; i < this.bindings.size(); i++) {
        var bnd = this.bindings.get(i);
        newEnv.put(bnd.getLeft(), bnd.getRight(), i);
      }

      newLetExpr.body = this.body.instantiate(engine, newEnv, solution);

      return newLetExpr;
    }

    @Override
    public Expr copy() {
      return new ExprLetSeq(this);
    }

    @Override
    public List<Expr> getChildren() {
      var list = new ArrayList<Expr>();
      this.bindings.forEach(bnd -> list.add(bnd.getRight().getExpr()));
      list.add(this.body.getExpr());
      return List.copyOf(list);
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.bindings.stream().anyMatch(bnd -> bnd.getLeft().equals(symbol)) || this.body.containsSymbol(symbol);
    }

    @Override
    public List<Expr> getInstantiableChildren() {
      return List.of(this.body);
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;

      Env newEnv = env.copy();

      ArrayList<InferenceTree> trees = new ArrayList<>();

      for (var binding : this.bindings) {
        var param = binding.getLeft();
        var value = binding.getRight();

        InferResult res1 = engine.infer(value, newEnv);

        Scheme generalizedType = res1.type().generalize(engine.getCurrentLevel());

        newEnv.put(param, generalizedType);
        trees.add(res1.tree());
      }

      InferResult res2 = engine.infer(body, newEnv);

      trees.add(res2.tree());

      return new InferResult(
          res2.type(),
          new InferenceTree(
              "T-Let*",
              input,
              "" + res2.type(),
              List.copyOf(trees)));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprLetRec other && this.bindings.equals(other.bindings)
          && this.body.equals(other.body)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.bindings, this.body, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      // If symbol is shadowed by the env created by this let binding, do not
      // overwrite the value!
      if (this.bindings.stream().anyMatch(bnd -> bnd.getLeft().equals(original))) {
        return this;
      }

      var newBody = this.body.replaceSymbol(original, replacement);
      var newBindings = this.bindings.stream()
          .map(binding -> Pair.of(binding.getLeft(), binding.getRight().replaceSymbol(original, replacement))).toList();
      return new ExprLetSeq(this, newBindings, newBody);
    }
  }

  /**
   * ExprLetRec is a multi let statement that infers multiple let
   * expressions at the
   * same time.
   *
   * <p>
   * This is mainly considered to be useful with function definitions, and thus
   * should be expected to be only used with fn defs
   *
   * <p>
   * As all expresions are considered global, to use this with ordered expressions
   * and their values, and allowing shadowing, LetExprSeq are required!
   *
   * @implNote The lets are considered global scope: i.e. the let values are first
   *           assigned to a new TypeVar, that is then unified with the
   *           inferred assigned type.
   *
   *
   */
  public static final class ExprLetRec extends Expr {

    private List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings;
    private Expr body;

    public ExprLetRec(Symbol<Expr, AlgorithmWType> param, Expr value,
        Expr body) {
      this.bindings = List.of(Pair.of(param, value));
      this.body = body;
    }

    public ExprLetRec(List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings,
        Expr body) {
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public ExprLetRec(ExprLetRec other) {
      super(other);
      this.bindings = List.copyOf(other.bindings);
      this.body = other.body;
    }

    public ExprLetRec(ExprLetRec other, List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings,
        Expr body) {
      super(other);
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings() {
      return this.bindings.stream()
          .map(bnd -> Pair.of(bnd.getLeft(), bnd.getRight().unwrapOrThis())).toList();
    }

    public Expr body() {
      return this.body.unwrapOrThis();
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      var newLetExpr = new ExprLetRec(this,
          List.copyOf(this.bindings),
          new Expr.ExprLit(new Literal.Unit()));

      // This line is key, as the defining scope expression, in this case `newLetExpr`
      // is bound to the scope
      var newEnv = new InstEnv<Expr, AlgorithmWType, Subst>(env, newLetExpr);
      for (int i = 0; i < this.bindings.size(); i++) {
        var bnd = this.bindings.get(i);
        newEnv.put(bnd.getLeft(), bnd.getRight(), i);
      }

      newLetExpr.body = this.body.instantiate(engine, newEnv, solution);

      return newLetExpr;
    }

    @Override
    public Expr copy() {
      return new ExprLetRec(this);
    }

    @Override
    public List<Expr> getChildren() {
      var list = new ArrayList<Expr>();
      this.bindings.forEach(bnd -> list.add(bnd.getRight().getExpr()));
      list.add(this.body.getExpr());
      return List.copyOf(list);
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.bindings.stream().anyMatch(bnd -> bnd.getLeft().equals(symbol)) || this.body.containsSymbol(symbol);
    }

    @Override
    public List<Expr> getInstantiableChildren() {
      return List.of(this.body);
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;

      Env newEnv = env.copy();
      ArrayList<Triple<Symbol<Expr, AlgorithmWType>, AlgorithmWType, Expr>> notUnified = new ArrayList<>(
          this.bindings.size());
      for (var binding : this.bindings) {
        var typeVar = new TypeVar<AlgorithmWType>(engine.getCurrentLevel());
        notUnified.add(Triple.of(binding.getLeft(), new AlgorithmWType.Var(typeVar), binding.getRight()));
        newEnv.put(binding.getLeft(), new AlgorithmWType.Var(typeVar).generalize(engine.getCurrentLevel()));
      }

      ArrayList<InferenceTree> trees = new ArrayList<>();

      for (var binding : notUnified) {
        var param = binding.getLeft();
        var typeVar = binding.getMiddle();
        var value = binding.getRight();

        InferResult res1 = engine.infer(value, newEnv);

        var unifyRes = engine.unify(typeVar, res1.type());
        trees.add(unifyRes);

        Scheme generalizedType = res1.type().generalize(engine.getCurrentLevel());

        newEnv.put(param, generalizedType);
        trees.add(res1.tree());
      }

      InferResult res2 = engine.infer(body, newEnv);

      trees.add(res2.tree());

      return new InferResult(
          res2.type(),
          new InferenceTree(
              "T-Let*",
              input,
              "" + res2.type(),
              List.copyOf(trees)));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprLetRec other && this.bindings.equals(other.bindings)
          && this.body.equals(other.body)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.bindings, this.body, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      // If symbol is shadowed by the env created by this let binding, do not
      // overwrite the value!
      if (this.bindings.stream().anyMatch(bnd -> bnd.getLeft().equals(original))) {
        return this;
      }

      var newBody = this.body.replaceSymbol(original, replacement);
      var newBindings = this.bindings.stream()
          .map(binding -> Pair.of(binding.getLeft(), binding.getRight().replaceSymbol(original, replacement))).toList();
      return new ExprLetRec(this, newBindings, newBody);
    }
  }

  public static class ExprSeq extends Expr {
    private List<Expr> expressions;

    public List<Expr> expressions() {
      return this.expressions.stream().map(Expr::unwrapOrThis).toList();
    }

    public ExprSeq(Expr expr) {
      super();
      this.expressions = List.of(expr);
    }

    public ExprSeq(List<Expr> exprs) {
      super();
      this.expressions = List.copyOf(exprs);
    }

    public ExprSeq(ExprSeq other) {
      super(other);
      this.expressions = List.copyOf(other.expressions);
    }

    public ExprSeq(ExprSeq other, List<Expr> exprs) {
      super(other);
      this.expressions = List.copyOf(exprs);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.expressions, super.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprSeq seq && this.expressions.equals(seq.expressions) && super.equals(obj);
    }

    @Override
    public List<Expr> getChildren() {
      return List.copyOf(this.expressions);
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      return new ExprSeq(this, this.expressions.stream().map(e -> e.replaceSymbol(original, replacement)).toList());
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.expressions.stream().anyMatch(e -> e.containsSymbol(symbol));
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;

      ArrayList<InferenceTree> trees = new ArrayList<>();
      AlgorithmWType lastType = new AlgorithmWType.LitType(TypeIdent.TYPE_IDENT_UNIT);
      for (var expr : this.expressions) {
        var infRes = engine.infer(expr, env);
        trees.add(infRes.tree());
        lastType = infRes.type();
      }

      var resType = lastType;
      return new InferResult(resType, new InferenceTree("Inf-Seq", input, "" + resType, List.copyOf(trees)));
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      return new ExprSeq(this, this.expressions.stream().map(e -> e.instantiate(engine, env, solution)).toList());
    }

    @Override
    public Expr copy() {
      return new ExprSeq(this);
    }

  }

  public static class ExprReturn extends Expr {
    private Expr value;

    public Expr value() {
      return this.value.unwrapOrThis();
    }

    public ExprReturn(Expr value) {
      this.value = value;
    }

    public ExprReturn(ExprReturn other) {
      super(other);
      this.value = other.value;
    }

    public ExprReturn(ExprReturn other, Expr value) {
      super(other);
      this.value = value;
    }

    @Override
    public String toString() {
      return "return " + this.value;
    }

    @Override
    public List<Expr> getChildren() {
      return List.of(this.value.getExpr());
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      return this.value.containsSymbol(symbol);
    }

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;

      InferResult inferred = engine.infer(this.value, env);
      var topScope = env.topScope();

      var inferredType = inferred.type();
      if (topScope.isPresent()) {
        topScope.get().addReturnType(inferredType);
      }

      return new InferResult(inferredType,
          new InferenceTree("T-Return", input, "" + inferredType, List.of(inferred.tree())));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprReturn other && this.value.equals(other.value) && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.value, super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      return new ExprReturn(this, this.value.replaceSymbol(original, replacement));
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      return new ExprReturn(this, this.value.instantiate(engine, env, solution));
    }

    @Override
    public Expr copy() {
      return new ExprReturn(this);
    }
  }

  public static class ExprCustom<D> extends Expr {

    @FunctionalInterface
    public interface InferFunction<D> {
      AlgorithmWType infer(TypeInference engine, Env env, D data);
    }

    @FunctionalInterface
    public interface InstantiateFunction<D> {
      Expr instantiate(ExprCustom<D> oldExpr, TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env,
          Subst solution, D data);
    }

    @FunctionalInterface
    public interface GetChildrenFunction<D> {
      List<Expr> getChildren(D data);
    }

    @FunctionalInterface
    public interface ReplaceSymbolFunction<D> {
      Expr replaceSymbol(ExprCustom<D> oldExpr, Symbol<Expr, AlgorithmWType> original,
          Symbol<Expr, AlgorithmWType> replacement, D data);
    }

    private D data;
    private InferFunction<D> inferFn;
    private Optional<InstantiateFunction<D>> instFn;
    private Optional<GetChildrenFunction<D>> getChildrenFn;
    private Optional<ReplaceSymbolFunction<D>> replaceSymbolFn;

    public ExprCustom(
        D data, InferFunction<D> inferFn, InstantiateFunction<D> instFn, GetChildrenFunction<D> getChildrenFn,
        ReplaceSymbolFunction<D> replaceSymbolFn) {
      this.data = data;
      this.inferFn = inferFn;
      this.instFn = Optional.ofNullable(instFn);
      this.getChildrenFn = Optional.ofNullable(getChildrenFn);
      this.replaceSymbolFn = Optional.ofNullable(replaceSymbolFn);
    }

    public ExprCustom(ExprCustom<D> other) {
      super(other);
      this.data = other.data;
      this.inferFn = other.inferFn;
      this.instFn = other.instFn;
      this.getChildrenFn = other.getChildrenFn;
      this.replaceSymbolFn = other.replaceSymbolFn;
    }

    public ExprCustom(ExprCustom<D> other, D newData) {
      super(other);
      this.data = newData;
      this.inferFn = other.inferFn;
      this.instFn = other.instFn;
      this.getChildrenFn = other.getChildrenFn;
      this.replaceSymbolFn = other.replaceSymbolFn;
    }

    public D getData() {
      return this.data;
    }

    @Override
    public String toString() {
      return "custom";
    }

    @Override
    public List<Expr> getChildren() {
      if (this.getChildrenFn.isPresent()) {
        return this.getChildrenFn.get().getChildren(this.data).stream().map(ExprOrOperator::getExpr).toList();
      } else {
        return List.of();
      }
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, AlgorithmWType> symbol) {
      // NOTE: for the expr custom, this safety check may not work correctly, hence it
      // just returns false.
      // This also implies that this Expr requires careful handling!
      return false;
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
      if (this.instFn.isPresent()) {
        return this.instFn.get().instantiate(this, engine, env, solution, this.data);
      }
      return this;
    };

    @Override
    public InferResult infer(TypeInference engine, Env env) {
      String input = env + " |- " + this;
      var infRes = this.inferFn.infer(engine, env, data);
      return new InferResult(infRes,
          new InferenceTree("T-Cust", input, "" + infRes, List.of()));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprCustom<?> other &&
          this.data.equals(other.data) &&
          this.inferFn.equals(other.inferFn) &&
          this.instFn.equals(other.instFn) &&
          this.getChildrenFn.equals(other.getChildrenFn) &&
          this.replaceSymbolFn.equals(other.replaceSymbolFn)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.data, this.inferFn, this.instFn, this.getChildrenFn, this.replaceSymbolFn,
          super.hashCode());
    }

    @Override
    public Expr replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      if (this.replaceSymbolFn.isPresent()) {
        return this.replaceSymbolFn.get().replaceSymbol(this, original, replacement, this.data);
      }
      return this;
    }

    @Override
    public Expr copy() {
      return new ExprCustom<>(this);
    }
  }
}

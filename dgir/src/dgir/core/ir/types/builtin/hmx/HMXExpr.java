package dgir.core.ir.types.builtin.hmx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

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
import dgir.core.ir.types.builtin.hmx.Constraint.LetBinding;
import dgir.core.ir.types.compatibility.ExprOrOperator;
import dgir.core.ir.types.compatibility.Scope;
import dgir.core.ir.types.traits.IExpressionCell;
import dgir.core.ir.types.traits.IInstantiable;
import dgir.core.ir.types.traits.IVariable;
import dgir.core.ir.types.traits.IAbstraction;
import dgir.core.ir.types.traits.IApplication;

public abstract class HMXExpr extends Expression<HMXExpr, HMXType>
    implements IInstantiable<HMXExpr, HMXType, Subst, TypeInference> {

  protected HMXExpr() {
  }

  protected HMXExpr(HMXExpr other) {
    super(other);
  }

  // Make sure, that exprs always equals via object reference (needed for in-set
  // storage!)
  @Override
  public boolean equals(Object obj) {
    return obj instanceof HMXExpr && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public HMXExpr getCellFor(HMXExpr origin, HMXExpr assignment) {
    return new ExprCell(origin, assignment);
  }

  /**
   * Infer the type of the expression. This method MUST be implemented for every
   * {@link HMXExpr}.
   * After inferring the type (the return value), the engine automatically stores
   * the inferred type within the inferred expression.
   *
   * @param engine
   * @param env
   * @return the inferred {@link HMXType} and the resulting {@link Subst},
   *         combined into an {@link InferenceTree}
   */
  public abstract GenerateResult generate(TypeInference engine, Env env, HMXType type);

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
  private static final class ExprCell extends HMXExpr implements IExpressionCell<HMXExpr, HMXType> {
    private HMXExpr reference;
    private HMXExpr cellValue;

    // public Expr reference() {
    // return this.reference;
    // }
    //
    // public Expr cellValue() {
    // return this.cellValue;
    // }

    public ExprCell(HMXExpr reference, HMXExpr cellValue) {
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
    public Optional<HMXType> getInferredType() {
      return cellValue.getInferredType();
    }

    @Override
    public void setInferredType(Optional<HMXType> inferredType) {
      cellValue.setInferredType(inferredType);
    }

    @Override
    public Optional<Operation> getUnderlyingOperation() {
      return cellValue.getUnderlyingOperation();
    }

    @Override
    public void setParentScopeExpression(Optional<HMXExpr> expr, Optional<Integer> position) {
      this.cellValue.setParentScopeExpression(expr, position);
    }

    @Override
    public Optional<HMXExpr> getParentScopeExpr() {
      return this.cellValue.getParentScopeExpr();
    }

    @Override
    public Optional<Integer> getParentScopePosition() {
      return this.cellValue.getParentScopePosition();
    }

    @Override
    public List<HMXExpr> getChildren() {
      return List.of(cellValue);
    }

    @Override
    public void reinstantiateSymbols() {
      // Do nothing, as the referenced expression is already expected to be
      // re-instantiated!
    }

    @Override
    public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
      return this;
    }

    @Override
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      return false;
    }

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      throw new UnsupportedOperationException("The memory cell is expected to only exist after instantiation!");
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      throw new UnsupportedOperationException("The memory cell is expected to only exist after instantiation!");
    }

    @Override
    public HMXExpr unwrap() {
      return this.cellValue;
    }

    @Override
    public void replaceIfMatches(HMXExpr reference, HMXExpr replacement) {
      if (reference == this.reference) {
        this.cellValue = replacement;
      }
    }

    @Override
    public HMXExpr copy() {
      return this;
    }

  }

  public static final class ExprAnn extends HMXExpr {
    private final HMXExpr expr;
    private final HMXType type;

    public HMXExpr expr() {
      return this.expr.unwrapOrThis();
    }

    public HMXType type() {
      return this.type;
    }

    public ExprAnn(HMXExpr expr, HMXType type) {
      this.expr = expr;
      this.type = type;
    }

    public ExprAnn(ExprAnn other) {
      super(other);
      this.expr = other.expr;
      this.type = other.type;
    }

    public ExprAnn(ExprAnn other, HMXExpr expr, HMXType type) {
      super(other);
      this.expr = expr;
      this.type = type;
    }

    @Override
    public List<HMXExpr> getChildren() {
      return List.of(this.expr);
    }

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      GenerateResult res = engine.generate(expr, env, type);

      return new GenerateResult(
          new Constraint.And(new Constraint.Equal(this.type, type), res.constr()),
          new InferenceTree(
              "T-Ann",
              env + " |- " + this,
              type.toString(),
              List.of(res.tree())));
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
    public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
      return new ExprAnn(this, this.expr.replaceSymbol(original, replacement), this.type);
    }

    @Override
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      return this.expr.containsSymbol(symbol);
    }

    @Override
    public HMXExpr copy() {
      return new ExprAnn(this);
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      // Simply return the inner as fully instantiated!
      return this.expr.instantiate(engine, env, solution);
    }
  }

  public static final class ExprLit extends HMXExpr {

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
    public List<HMXExpr> getChildren() {
      return List.of();
    }

    @Override
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      return false;
    }

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      var hmxType = engine.generalNominalTypeToInferenceType(value.toParameterizedNominalType(), null);
      return new GenerateResult(
          new Constraint.Equal(type, hmxType.getLeft()),
          new InferenceTree(
              "T-" + hmxType,
              env + " |- " + this,
              hmxType.toString(),
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
    public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
      return this;
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      return this;
    }

    @Override
    public HMXExpr copy() {
      return new ExprLit(this);
    }
  }

  public static final class ExprTuple extends HMXExpr {

    private final List<HMXExpr> elements;

    public List<HMXExpr> elements() {
      return this.elements.stream().map(HMXExpr::unwrapOrThis).toList();
    }

    @Override
    public HMXExpr copy() {
      return new ExprTuple(this);
    }

    public ExprTuple(List<HMXExpr> elements) {
      this.elements = elements;
    }

    public ExprTuple(HMXExpr... elements) {
      ArrayList<HMXExpr> elems = new ArrayList<>();
      for (var elem : elements) {
        elems.add(elem);
      }

      this.elements = List.copyOf(elems);
    }

    public ExprTuple(ExprTuple other) {
      super(other);
      this.elements = List.copyOf(other.elements);
    }

    public ExprTuple(ExprTuple other, List<HMXExpr> elements) {
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
    public List<HMXExpr> getChildren() {
      return this.elements.stream().map(ExprOrOperator::getExpr).toList();
    }

    @Override
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      return this.elements.stream().anyMatch(elem -> elem.containsSymbol(symbol));
    }

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      String input = env + " |- " + this;
      ArrayList<TypeVar<HMXType>> typeVars = new ArrayList<>();
      ArrayList<HMXType> types = new ArrayList<>();
      ArrayList<Constraint> constraints = new ArrayList<>();
      ArrayList<InferenceTree> trees = new ArrayList<>();
      Env currentEnv = env.copy();

      for (var expr : elements) {
        var freshTypVar = new TypeVar<HMXType>();
        typeVars.add(freshTypVar);
        var res = engine.generate(expr, currentEnv, new HMXType.Var(freshTypVar));
        types.add(new HMXType.Var(freshTypVar));
        trees.add(res.tree());
        constraints.add(res.constr());
      }

      var resultType = new HMXType.Tuple(List.copyOf(types));

      return new GenerateResult(
          new Constraint.Exists(typeVars,
              new Constraint.And(new Constraint.Equal(resultType, type), new Constraint.And(constraints))),
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
    public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
      return new ExprTuple(this,
          this.elements.stream().map(elem -> elem.replaceSymbol(original, replacement)).toList());
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      return new ExprTuple(this, this.elements.stream().map(elem -> elem.instantiate(engine, env, solution)).toList());
    }
  }

  public static final class ExprVar extends HMXExpr implements IVariable<HMXExpr, HMXType> {

    private final Symbol<HMXExpr, HMXType> name;

    public Symbol<HMXExpr, HMXType> name() {
      return this.name;
    }

    public ExprVar(Symbol<HMXExpr, HMXType> name) {
      this.name = name;
    }

    @SuppressWarnings("unused")
    private ExprVar(ExprVar other) {
      super(other);
      this.name = other.name;
    }

    private ExprVar(ExprVar other, Symbol<HMXExpr, HMXType> name) {
      super(other);
      this.name = name;
    }

    @Override
    public final String toString() {
      return name + "";
    }

    @Override
    public Symbol<HMXExpr, HMXType> getReferencedVariable() {
      return this.name;
    }

    @Override
    public List<HMXExpr> getChildren() {
      return List.of();
    }

    @Override
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      return this.name.equals(symbol);
    }

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      String input = env + " |- " + this;

      return new GenerateResult(
          new Constraint.Inst(this.name, type),
          new InferenceTree(
              "T-Var",
              input,
              "" + type,
              List.of()));
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
    public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
      if (this.name.equals(original)) {
        return new ExprVar(this, replacement);
      }
      return this;
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      // Nothing to instantiate;
      return this;
    }

    @Override
    public HMXExpr copy() {
      return new ExprVar(this);
    }
  }

  public static final class ExprApp extends HMXExpr implements IApplication<HMXExpr, HMXType> {

    private final HMXExpr func;
    private final List<HMXExpr> args;

    public HMXExpr func() {
      return this.func.unwrapOrThis();
    }

    public List<HMXExpr> args() {
      return this.args.stream().map(HMXExpr::unwrapOrThis).toList();
    }

    private Optional<HMXType> inferredFunctionType;

    public ExprApp(
        HMXExpr func,
        HMXExpr arg) {
      this.func = func;
      this.args = List.of(arg);
      this.inferredFunctionType = Optional.empty();
    }

    public ExprApp(
        HMXExpr func,
        List<HMXExpr> args) {
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

    public ExprApp(ExprApp other, HMXExpr func,
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
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      return this.func.containsSymbol(symbol) || this.args.stream().anyMatch(arg -> arg.containsSymbol(symbol));
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
    public HMXExpr copy() {
      return new ExprApp(this);
    }

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      String input = env + " |- " + this;

      HMXType resultType = type;

      HMXType builtArrowType = resultType;
      ArrayList<TypeVar<HMXType>> quantifiedVars = new ArrayList<>();
      ArrayList<Constraint> argConstraints = new ArrayList<>();
      ArrayList<InferenceTree> trees = new ArrayList<>();

      for (var arg : this.args.reversed()) {
        var freshTypeVar = new TypeVar<HMXType>();
        quantifiedVars.add(freshTypeVar);
        GenerateResult argRes = engine.generate(arg, env, new HMXType.Var(freshTypeVar));
        argConstraints.add(argRes.constr());
        builtArrowType = new HMXType.Arrow(new HMXType.Var(freshTypeVar), builtArrowType);
        trees.add(argRes.tree());
      }

      // NOTE: in case the APP parameters are empty, i.e. a function without
      // parameters is configured, the APP behaviour and the ABS behaviour are
      // identical and treat the function type as a Unit -> t0. This means, that the
      // final
      // type is an arrow type that accepts a unit value as a parameter.
      if (builtArrowType == resultType) {
        builtArrowType = new HMXType.Arrow(new HMXType.LitType(TypeIdent.TYPE_IDENT_UNIT),
            builtArrowType);
      }

      var fnTypeVar = new TypeVar<HMXType>();
      var fnType = new HMXType.Var(fnTypeVar);
      GenerateResult funcInferRes = engine.generate(func, env, fnType);
      trees.add(funcInferRes.tree());
      quantifiedVars.add(fnTypeVar);

      this.inferredFunctionType = Optional.of(builtArrowType);

      return new GenerateResult(
          new Constraint.Exists(quantifiedVars,
              new Constraint.And(new Constraint.Sub(fnType, builtArrowType), funcInferRes.constr(),
                  new Constraint.And(argConstraints))),
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
    public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
      var newFunc = this.func.replaceSymbol(original, replacement);
      var newArgs = this.args.stream().map(arg -> arg.replaceSymbol(original, replacement)).toList();

      var newApp = new ExprApp(this, newFunc, newArgs);
      return newApp;
    }

    public Optional<HMXType> getInferredFunctionType() {
      return this.inferredFunctionType;
    }
  }

  public static final class ExprAbs extends HMXExpr implements IAbstraction<HMXExpr, HMXType> {

    private List<Symbol<HMXExpr, HMXType>> params;
    private HMXExpr body;

    public List<Symbol<HMXExpr, HMXType>> params() {
      return List.copyOf(this.params);
    }

    public HMXExpr body() {
      return this.body.unwrapOrThis();
    }

    public ExprAbs(Symbol<HMXExpr, HMXType> param, HMXExpr body) {
      this.params = List.of(param);
      this.body = body;
    }

    public ExprAbs(List<Symbol<HMXExpr, HMXType>> params, HMXExpr body) {
      this.params = List.copyOf(params);
      this.body = body;
    }

    public ExprAbs(ExprAbs other) {
      super(other);
      this.params = List.copyOf(other.params);
      this.body = other.body;
    }

    public ExprAbs(ExprAbs other, List<Symbol<HMXExpr, HMXType>> params, HMXExpr body) {
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
    public List<HMXExpr> getChildren() {
      return List.of(this.body.getExpr());
    }

    @Override
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      return this.params.stream().anyMatch(param -> param.equals(symbol)) || this.body.containsSymbol(symbol);
    }

    @Override
    public List<Symbol<HMXExpr, HMXType>> getAbstractionsOverSymbols() {
      return this.params();
    }

    @Override
    public HMXExpr getAbstractionBody() {
      return this.body();
    }

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      String input = env + " |- " + this;

      ArrayList<TypeVar<HMXType>> paramsAndTypeVars = new ArrayList<>();
      ArrayList<LetBinding> bindings = new ArrayList<>();
      for (var param : this.params) {
        var typeVar = new TypeVar<HMXType>();
        HMXType freshTypeVar = new HMXType.Var(typeVar);
        paramsAndTypeVars.add(typeVar);
        bindings.add(new LetBinding(param, freshTypeVar));
      }

      var newEnv = env.copy();
      Scope<HMXType> functionScope = newEnv.addScope();

      TypeVar<HMXType> retTypeVar = new TypeVar<>();
      GenerateResult b = engine.generate(body, newEnv, new HMXType.Var(retTypeVar));

      // In terms of IR, the body will not have a direct return parameter. Though it
      // can be assumed,
      // that the last expression is always a return in a function, even if nothing is
      // returned.
      // Hence, It would make sense to treat the last return expression as an
      // expression that actually returns a value of type T.
      // This must then be unified with the retTypeVar collected from all return
      // statements (if present)
      // var retTypeUnify = engine.unify(res.type(), retTypeVar);

      HMXType appliedRetType = new HMXType.Var(retTypeVar);
      HMXType resultType = appliedRetType;
      for (var paramAndTypeVar : paramsAndTypeVars.reversed()) {
        resultType = new HMXType.Arrow(new HMXType.Var(paramAndTypeVar),
            resultType);
      }

      // NOTE: in case the ABS parameters are empty, i.e. a function without
      // parameters is configured, the APP behaviour and the ABS behaviour are
      // identical and treat the function type as a Unit -> t0. This means, that the
      // final
      // type is an arrow type that accepts a unit value as a parameter.
      if (resultType == appliedRetType) {
        resultType = new HMXType.Arrow(new HMXType.LitType(TypeIdent.TYPE_IDENT_UNIT), resultType);
      }

      paramsAndTypeVars.add(retTypeVar);

      // Unify all return values. If return values do not have the same type, error
      // will get thrown here!
      return new GenerateResult(
          new Constraint.And(
              new Constraint.Exists(paramsAndTypeVars,
                  new Constraint.LetSeq(bindings, b.constr())),
              new Constraint.Equal(type, resultType),
              new Constraint.And(functionScope.getAllReturnTypesInScope().stream()
                  .map(t -> (Constraint) new Constraint.Equal(new HMXType.Var(retTypeVar), t)).toList())),
          new InferenceTree(
              "T-Abs",
              input,
              resultType.toString(),
              List.of()));
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

      ArrayList<Symbol<HMXExpr, HMXType>> newParams = new ArrayList<>(this.params.size());
      var currentParamType = inferredType.get().deref();
      for (int i = 0; i < this.params.size(); i++) {
        assert currentParamType instanceof HMXType.Arrow;
        var arrowType = (HMXType.Arrow) currentParamType;

        assert arrowType.from.deref() instanceof HMXType.LitType
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
    public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
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
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      return new ExprAbs(this, List.copyOf(this.params), this.body.instantiate(engine, env, solution));
    }

    @Override
    public HMXExpr copy() {
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
  public static final class ExprLetSeq extends HMXExpr {

    private List<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings;
    private HMXExpr body;

    public ExprLetSeq(Symbol<HMXExpr, HMXType> param, HMXExpr value,
        HMXExpr body) {
      this.bindings = List.of(Pair.of(param, value));
      this.body = body;
    }

    public ExprLetSeq(List<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings,
        HMXExpr body) {
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public ExprLetSeq(ExprLetSeq other) {
      super(other);
      this.bindings = List.copyOf(other.bindings);
      this.body = other.body;
    }

    public ExprLetSeq(ExprLetSeq other, List<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings,
        HMXExpr body) {
      super(other);
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public List<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings() {
      return this.bindings.stream()
          .map(bnd -> Pair.of(bnd.getLeft(), bnd.getRight().unwrapOrThis())).toList();
    }

    public HMXExpr body() {
      return this.body.unwrapOrThis();
    }

    @Override
    public final String toString() {
      return "let (" + this.bindings.stream().map(Object::toString).collect(Collectors.joining(", ")) + ") in "
          + body;
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      var newLetExpr = new ExprLetSeq(this,
          List.copyOf(this.bindings),
          new HMXExpr.ExprLit(new Literal.Unit()));

      // This line is key, as the defining scope expression, in this case `newLetExpr`
      // is bound to the scope
      var newEnv = new InstEnv<HMXExpr, HMXType, Subst>(env, newLetExpr);
      for (int i = 0; i < this.bindings.size(); i++) {
        var bnd = this.bindings.get(i);
        newEnv.put(bnd.getLeft(), bnd.getRight(), i);
      }

      newLetExpr.body = this.body.instantiate(engine, newEnv, solution);

      return newLetExpr;
    }

    @Override
    public HMXExpr copy() {
      return new ExprLetSeq(this);
    }

    @Override
    public List<HMXExpr> getChildren() {
      var list = new ArrayList<HMXExpr>();
      this.bindings.forEach(bnd -> list.add(bnd.getRight().getExpr()));
      list.add(this.body.getExpr());
      return List.copyOf(list);
    }

    @Override
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      return this.bindings.stream().anyMatch(bnd -> bnd.getLeft().equals(symbol)) || this.body.containsSymbol(symbol);
    }

    @Override
    public List<HMXExpr> getInstantiableChildren() {
      return List.of(this.body);
    }

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      String input = env + " |- " + this;

      var bindings = new ArrayList<LetBinding>();
      for (var binding : this.bindings) {
        var param = binding.getLeft();
        var value = binding.getRight();
        var freshTypeVar = new TypeVar<HMXType>();

        GenerateResult res1 = engine.generate(value, env, new HMXType.Var(freshTypeVar));
        bindings.add(
            new LetBinding(param,
                new Scheme(List.of(freshTypeVar.find()), new HMXType.Var(freshTypeVar), res1.constr())));
      }

      GenerateResult res2 = engine.generate(body, env, type);

      return new GenerateResult(
          new Constraint.LetSeq(bindings, res2.constr()),
          new InferenceTree(
              "T-Let*",
              input,
              "" + type,
              List.of()));
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
    public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
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
  public static final class ExprLetRec extends HMXExpr {

    private List<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings;
    private HMXExpr body;

    public ExprLetRec(Symbol<HMXExpr, HMXType> param, HMXExpr value,
        HMXExpr body) {
      this.bindings = List.of(Pair.of(param, value));
      this.body = body;
    }

    public ExprLetRec(List<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings,
        HMXExpr body) {
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public ExprLetRec(ExprLetRec other) {
      super(other);
      this.bindings = List.copyOf(other.bindings);
      this.body = other.body;
    }

    public ExprLetRec(ExprLetRec other, List<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings,
        HMXExpr body) {
      super(other);
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public List<Pair<Symbol<HMXExpr, HMXType>, HMXExpr>> bindings() {
      return this.bindings.stream()
          .map(bnd -> Pair.of(bnd.getLeft(), bnd.getRight().unwrapOrThis())).toList();
    }

    public HMXExpr body() {
      return this.body.unwrapOrThis();
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      var newLetExpr = new ExprLetRec(this,
          List.copyOf(this.bindings),
          new HMXExpr.ExprLit(new Literal.Unit()));

      // This line is key, as the defining scope expression, in this case `newLetExpr`
      // is bound to the scope
      var newEnv = new InstEnv<HMXExpr, HMXType, Subst>(env, newLetExpr);
      for (int i = 0; i < this.bindings.size(); i++) {
        var bnd = this.bindings.get(i);
        newEnv.put(bnd.getLeft(), bnd.getRight(), i);
      }

      newLetExpr.body = this.body.instantiate(engine, newEnv, solution);

      return newLetExpr;
    }

    @Override
    public HMXExpr copy() {
      return new ExprLetRec(this);
    }

    @Override
    public List<HMXExpr> getChildren() {
      var list = new ArrayList<HMXExpr>();
      this.bindings.forEach(bnd -> list.add(bnd.getRight().getExpr()));
      list.add(this.body.getExpr());
      return List.copyOf(list);
    }

    @Override
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      return this.bindings.stream().anyMatch(bnd -> bnd.getLeft().equals(symbol)) || this.body.containsSymbol(symbol);
    }

    @Override
    public List<HMXExpr> getInstantiableChildren() {
      return List.of(this.body);
    }

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      String input = env + " |- " + this;

      var bindings = new ArrayList<LetBinding>();
      for (var binding : this.bindings) {
        var param = binding.getLeft();
        var value = binding.getRight();
        var freshTypeVar = new TypeVar<HMXType>();

        GenerateResult res1 = engine.generate(value, env, new HMXType.Var(freshTypeVar));
        bindings.add(
            new LetBinding(param, new Scheme(List.of(freshTypeVar), new HMXType.Var(freshTypeVar), res1.constr())));
      }

      GenerateResult res2 = engine.generate(body, env, type);

      return new GenerateResult(
          new Constraint.LetRec(bindings, res2.constr()),
          new InferenceTree(
              "T-Let*",
              input,
              "" + type,
              List.of()));
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
    public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
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

  public static class ExprSeq extends HMXExpr {
    private List<HMXExpr> expressions;

    public List<HMXExpr> expressions() {
      return this.expressions.stream().map(HMXExpr::unwrapOrThis).toList();
    }

    public ExprSeq(HMXExpr expr) {
      super();
      this.expressions = List.of(expr);
    }

    public ExprSeq(List<HMXExpr> exprs) {
      super();
      this.expressions = List.copyOf(exprs);
    }

    public ExprSeq(ExprSeq other) {
      super(other);
      this.expressions = List.copyOf(other.expressions);
    }

    public ExprSeq(ExprSeq other, List<HMXExpr> exprs) {
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
    public List<HMXExpr> getChildren() {
      return List.copyOf(this.expressions);
    }

    @Override
    public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
      return new ExprSeq(this, this.expressions.stream().map(e -> e.replaceSymbol(original, replacement)).toList());
    }

    @Override
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      return this.expressions.stream().anyMatch(e -> e.containsSymbol(symbol));
    }

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      String input = env + " |- " + this;

      var sequentialTypes = new ArrayList<TypeVar<HMXType>>();
      var sequentialConstraints = new ArrayList<Constraint>();
      for (var expr : this.expressions) {
        var freshTypeVar = new TypeVar<HMXType>();
        var infRes = engine.generate(expr, env, new HMXType.Var(freshTypeVar));
        sequentialTypes.add(freshTypeVar);
        sequentialConstraints.add(infRes.constr());
      }

      HMXType resType = new HMXType.LitType(TypeIdent.TYPE_IDENT_UNIT);
      if (!sequentialTypes.isEmpty()) {
        resType = new HMXType.Var(sequentialTypes.getLast());
      }

      return new GenerateResult(
          new Constraint.And(new Constraint.Equal(resType, type), new Constraint.And(sequentialConstraints)),
          new InferenceTree("Inf-Seq", input, "" + resType, List.of()));
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      return new ExprSeq(this, this.expressions.stream().map(e -> e.instantiate(engine, env, solution)).toList());
    }

    @Override
    public HMXExpr copy() {
      return new ExprSeq(this);
    }

  }

  public static class ExprReturn extends HMXExpr {
    private HMXExpr value;

    public HMXExpr value() {
      return this.value.unwrapOrThis();
    }

    public ExprReturn(HMXExpr value) {
      this.value = value;
    }

    public ExprReturn(ExprReturn other) {
      super(other);
      this.value = other.value;
    }

    public ExprReturn(ExprReturn other, HMXExpr value) {
      super(other);
      this.value = value;
    }

    @Override
    public String toString() {
      return "return " + this.value;
    }

    @Override
    public List<HMXExpr> getChildren() {
      return List.of(this.value.getExpr());
    }

    @Override
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      return this.value.containsSymbol(symbol);
    }

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      String input = env + " |- " + this;

      GenerateResult inferred = engine.generate(this.value, env, type);
      var topScope = env.topScope();

      if (topScope.isPresent()) {
        topScope.get().addReturnType(type);
      }

      return new GenerateResult(inferred.constr(),
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
    public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
      return new ExprReturn(this, this.value.replaceSymbol(original, replacement));
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      return new ExprReturn(this, this.value.instantiate(engine, env, solution));
    }

    @Override
    public HMXExpr copy() {
      return new ExprReturn(this);
    }
  }

  public static class ExprCustom<D> extends HMXExpr {

    @FunctionalInterface
    public interface GenerateFunction<D> {
      Constraint generate(TypeInference engine, Env env, HMXType type, D data);
    }

    @FunctionalInterface
    public interface InstantiateFunction<D> {
      HMXExpr instantiate(ExprCustom<D> oldExpr, TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env,
          Subst solution, D data);
    }

    @FunctionalInterface
    public interface GetChildrenFunction<D> {
      List<HMXExpr> getChildren(D data);
    }

    @FunctionalInterface
    public interface ReplaceSymbolFunction<D> {
      HMXExpr replaceSymbol(ExprCustom<D> oldExpr, Symbol<HMXExpr, HMXType> original,
          Symbol<HMXExpr, HMXType> replacement, D data);
    }

    private D data;
    private GenerateFunction<D> generateFn;
    private Optional<InstantiateFunction<D>> instFn;
    private Optional<GetChildrenFunction<D>> getChildrenFn;
    private Optional<ReplaceSymbolFunction<D>> replaceSymbolFn;

    public ExprCustom(
        D data, GenerateFunction<D> generateFn, InstantiateFunction<D> instFn, GetChildrenFunction<D> getChildrenFn,
        ReplaceSymbolFunction<D> replaceSymbolFn) {
      this.data = data;
      this.generateFn = generateFn;
      this.instFn = Optional.ofNullable(instFn);
      this.getChildrenFn = Optional.ofNullable(getChildrenFn);
      this.replaceSymbolFn = Optional.ofNullable(replaceSymbolFn);
    }

    public ExprCustom(ExprCustom<D> other) {
      super(other);
      this.data = other.data;
      this.generateFn = other.generateFn;
      this.instFn = other.instFn;
      this.getChildrenFn = other.getChildrenFn;
      this.replaceSymbolFn = other.replaceSymbolFn;
    }

    public ExprCustom(ExprCustom<D> other, D newData) {
      super(other);
      this.data = newData;
      this.generateFn = other.generateFn;
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
    public List<HMXExpr> getChildren() {
      if (this.getChildrenFn.isPresent()) {
        return this.getChildrenFn.get().getChildren(this.data).stream().map(ExprOrOperator::getExpr).toList();
      } else {
        return List.of();
      }
    }

    @Override
    public boolean containsSymbol(Symbol<HMXExpr, HMXType> symbol) {
      // NOTE: for the expr custom, this safety check may not work correctly, hence it
      // just returns false.
      // This also implies that this Expr requires careful handling!
      return false;
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      if (this.instFn.isPresent()) {
        return this.instFn.get().instantiate(this, engine, env, solution, this.data);
      }
      return this;
    };

    @Override
    public GenerateResult generate(TypeInference engine, Env env, HMXType type) {
      String input = env + " |- " + this;
      var infRes = this.generateFn.generate(engine, env, type, data);
      return new GenerateResult(infRes,
          new InferenceTree("T-Cust", input, "" + infRes, List.of()));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExprCustom<?> other &&
          this.data.equals(other.data) &&
          this.generateFn.equals(other.generateFn) &&
          this.instFn.equals(other.instFn) &&
          this.getChildrenFn.equals(other.getChildrenFn) &&
          this.replaceSymbolFn.equals(other.replaceSymbolFn)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.data, this.generateFn, this.instFn, this.getChildrenFn, this.replaceSymbolFn,
          super.hashCode());
    }

    @Override
    public HMXExpr replaceSymbol(Symbol<HMXExpr, HMXType> original, Symbol<HMXExpr, HMXType> replacement) {
      if (this.replaceSymbolFn.isPresent()) {
        return this.replaceSymbolFn.get().replaceSymbol(this, original, replacement, this.data);
      }
      return this;
    }

    @Override
    public HMXExpr copy() {
      return new ExprCustom<>(this);
    }
  }
}

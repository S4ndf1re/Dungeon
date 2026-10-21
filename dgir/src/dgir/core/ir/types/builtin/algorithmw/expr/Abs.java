package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.debug.Location;
import dgir.core.ir.Type;
import dgir.core.ir.Value;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.compatibility.Scope;
import dgir.core.ir.types.traits.IAbstraction;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Env;
import dgir.core.ir.types.builtin.algorithmw.InferResult;
import dgir.core.ir.types.builtin.algorithmw.Scheme;
import dgir.core.ir.types.builtin.algorithmw.Subst;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;

public final class Abs extends Expr implements IAbstraction<Expr, AlgorithmWType> {

  private List<Symbol<Expr, AlgorithmWType>> params;
  private Expr body;

  public List<Symbol<Expr, AlgorithmWType>> params() {
    return List.copyOf(this.params);
  }

  public Expr body() {
    return this.body.unwrapOrThis();
  }

  public Abs(Symbol<Expr, AlgorithmWType> param, Expr body) {
    this.params = List.of(param);
    this.body = body;
  }

  public Abs(Location loc, Symbol<Expr, AlgorithmWType> param, Expr body) {
    super(loc);
    this.params = List.of(param);
    this.body = body;
  }

  public Abs(List<Symbol<Expr, AlgorithmWType>> params, Expr body) {
    this.params = List.copyOf(params);
    this.body = body;
  }

  public Abs(Location loc, List<Symbol<Expr, AlgorithmWType>> params, Expr body) {
    super(loc);
    this.params = List.copyOf(params);
    this.body = body;
  }

  public Abs(Abs other) {
    super(other);
    this.params = List.copyOf(other.params);
    this.body = other.body;
  }

  public Abs(Abs other, List<Symbol<Expr, AlgorithmWType>> params, Expr body) {
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
    Env newEnv = env.copy();
    ArrayList<Pair<Symbol<Expr, AlgorithmWType>, TypeVar<AlgorithmWType>>> paramsAndTypeVars = new ArrayList<>();
    for (var param : this.params) {
      var typeVar = new TypeVar<AlgorithmWType>(engine.getCurrentLevel());
      AlgorithmWType freshTypeVar = new AlgorithmWType.Var(this.getLocation(), typeVar);
      Scheme newScheme = new Scheme(List.of(), freshTypeVar);
      newEnv.put(param, newScheme);
      paramsAndTypeVars.add(Pair.of(param, typeVar));
    }

    Scope<AlgorithmWType> functionScope = newEnv.addScope();
    AlgorithmWType retTypeVar = new AlgorithmWType.Var(this.getLocation(), new TypeVar<>(engine.getCurrentLevel()));
    InferResult res = engine.infer(body, newEnv);

    // Unify all return values. If return values do not have the same type, error
    // will get thrown here!
    for (var retType : functionScope.getAllReturnTypesInScope()) {
      engine.unify(retTypeVar, retType);
    }

    // In terms of IR, the body will not have a direct return parameter. Though it
    // can be assumed,
    // that the last expression is always a return in a function, even if nothing is
    // returned.
    // Hence, It would make sense to treat the last return expression as an
    // expression that actually returns a value of type T.
    // This must then be unified with the retTypeVar collected from all return
    // statements (if present)
    engine.unify(res.type(), retTypeVar);

    AlgorithmWType appliedRetType = retTypeVar;
    AlgorithmWType resultType = appliedRetType;
    for (var paramAndTypeVar : paramsAndTypeVars.reversed()) {
      resultType = new AlgorithmWType.Arrow(this.getLocation(),
          new AlgorithmWType.Var(this.getLocation(), paramAndTypeVar.getRight()), resultType);
    }

    // NOTE: in case the ABS parameters are empty, i.e. a function without
    // parameters is configured, the APP behaviour and the ABS behaviour are
    // identical and treat the function type as a Unit -> t0. This means, that the
    // final
    // type is an arrow type that accepts a unit value as a parameter.
    if (resultType == appliedRetType) {
      resultType = new AlgorithmWType.Arrow(this.getLocation(),
          new AlgorithmWType.LitType(this.getLocation(), TypeIdent.TYPE_IDENT_UNIT), resultType);
    }

    return new InferResult(resultType);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Abs other && this.params.equals(other.params) && this.body.equals(other.body)
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

    // NOTE: in-place replacement — the tree is private after solving, and
    // copying here would break structural sharing (see replaceSymbol).
    for (int i = 0; i < newParams.size(); i++) {
      this.body.replaceSymbol(oldParams.get(i), newParams.get(i));
    }
  }

  @Override
  public void replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
    // If the symbol is shadowed by one of the bound values, don't replace in the
    // body!
    if (this.params.stream().anyMatch(param -> param.equals(original))) {
      return;
    }

    // Only replace in the body, as the functions parameters are always the same
    // value!
    this.body.replaceSymbol(original, replacement);
  }

  @Override
  public Expr instantiateInner(TypeInference engine, InstEnv<Expr, AlgorithmWType, Subst> env, Subst solution) {
    return new Abs(this, List.copyOf(this.params), this.body.instantiate(engine, env, solution));
  }

  @Override
  public Expr copy() {
    return new Abs(this);
  }

}

package dgir.core.ir.types.builtin.hmx.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dgir.core.debug.Location;
import dgir.core.ir.Type;
import dgir.core.ir.Value;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.hmx.Constraint;
import dgir.core.ir.types.builtin.hmx.Constraint.LetBinding;
import dgir.core.ir.types.builtin.hmx.Env;
import dgir.core.ir.types.builtin.hmx.GenerateResult;
import dgir.core.ir.types.builtin.hmx.HMXExpr;
import dgir.core.ir.types.builtin.hmx.HMXType;
import dgir.core.ir.types.builtin.hmx.Subst;
import dgir.core.ir.types.builtin.hmx.TypeInference;
import dgir.core.ir.types.compatibility.Scope;
import dgir.core.ir.types.traits.IAbstraction;

public final class Abs extends HMXExpr implements IAbstraction<HMXExpr, HMXType> {

    private List<Symbol<HMXExpr, HMXType>> params;
    private HMXExpr body;

    public List<Symbol<HMXExpr, HMXType>> params() {
      return List.copyOf(this.params);
    }

    public HMXExpr body() {
      return this.body.unwrapOrThis();
    }

    public Abs(Symbol<HMXExpr, HMXType> param, HMXExpr body) {
      this.params = List.of(param);
      this.body = body;
    }

    public Abs(Location loc, Symbol<HMXExpr, HMXType> param, HMXExpr body) {
      super(loc);
      this.params = List.of(param);
      this.body = body;
    }

    public Abs(List<Symbol<HMXExpr, HMXType>> params, HMXExpr body) {
      this.params = List.copyOf(params);
      this.body = body;
    }

    public Abs(Location loc, List<Symbol<HMXExpr, HMXType>> params, HMXExpr body) {
      super(loc);
      this.params = List.copyOf(params);
      this.body = body;
    }

    public Abs(Abs other) {
      super(other);
      this.params = List.copyOf(other.params);
      this.body = other.body;
    }

    public Abs(Abs other, List<Symbol<HMXExpr, HMXType>> params, HMXExpr body) {
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
      ArrayList<TypeVar<HMXType>> paramsAndTypeVars = new ArrayList<>();
      ArrayList<LetBinding> bindings = new ArrayList<>();
      for (var param : this.params) {
        var typeVar = new TypeVar<HMXType>();
        HMXType freshTypeVar = new HMXType.Var(this.getLocation(), typeVar);
        paramsAndTypeVars.add(typeVar);
        bindings.add(new LetBinding(param, freshTypeVar));
      }

      var newEnv = env.copy();
      Scope<HMXType> functionScope = newEnv.addScope();

      TypeVar<HMXType> retTypeVar = new TypeVar<>();
      GenerateResult b = engine.generate(body, newEnv, new HMXType.Var(this.getLocation(), retTypeVar));

      // In terms of IR, the body will not have a direct return parameter. Though it
      // can be assumed,
      // that the last expression is always a return in a function, even if nothing is
      // returned.
      // Hence, It would make sense to treat the last return expression as an
      // expression that actually returns a value of type T.
      // This must then be unified with the retTypeVar collected from all return
      // statements (if present)
      // var retTypeUnify = engine.unify(res.type(), retTypeVar);

      HMXType appliedRetType = new HMXType.Var(this.getLocation(), retTypeVar);
      HMXType resultType = appliedRetType;
      for (var paramAndTypeVar : paramsAndTypeVars.reversed()) {
        resultType = new HMXType.Arrow(this.getLocation(),
            new HMXType.Var(this.getLocation(), paramAndTypeVar), resultType);
      }

      // NOTE: in case the ABS parameters are empty, i.e. a function without
      // parameters is configured, the APP behaviour and the ABS behaviour are
      // identical and treat the function type as a Unit -> t0. This means, that the
      // final
      // type is an arrow type that accepts a unit value as a parameter.
      if (resultType == appliedRetType) {
        resultType = new HMXType.Arrow(this.getLocation(),
            new HMXType.LitType(this.getLocation(), TypeIdent.TYPE_IDENT_UNIT), resultType);
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
                  .map(t -> (Constraint) new Constraint.Equal(new HMXType.Var(this.getLocation(), retTypeVar), t))
                  .toList())));
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
        bodyExpr = bodyExpr.replaceSymbol(oldParams.get(i), newParams.get(i));
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
      return new Abs(this, this.params, body.replaceSymbol(original, replacement));
    }

    @Override
    public HMXExpr instantiateInner(TypeInference engine, InstEnv<HMXExpr, HMXType, Subst> env, Subst solution) {
      return new Abs(this, List.copyOf(this.params), this.body.instantiate(engine, env, solution));
    }

    @Override
    public HMXExpr copy() {
      return new Abs(this);
    }

  }

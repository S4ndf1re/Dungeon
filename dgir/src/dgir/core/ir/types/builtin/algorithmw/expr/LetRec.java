package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Env;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.InferResult;
import dgir.core.ir.types.builtin.algorithmw.Scheme;
import dgir.core.ir.types.builtin.algorithmw.Subst;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;

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
  public final class LetRec extends Expr {

    private List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings;
    private Expr body;

    public LetRec(Symbol<Expr, AlgorithmWType> param, Expr value,
        Expr body) {
      this.bindings = List.of(Pair.of(param, value));
      this.body = body;
    }

    public LetRec(Location loc, Symbol<Expr, AlgorithmWType> param, Expr value,
        Expr body) {
      super(loc);
      this.bindings = List.of(Pair.of(param, value));
      this.body = body;
    }

    public LetRec(List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings,
        Expr body) {
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public LetRec(Location loc, List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings,
        Expr body) {
      super(loc);
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public LetRec(LetRec other) {
      super(other);
      this.bindings = List.copyOf(other.bindings);
      this.body = other.body;
    }

    public LetRec(LetRec other, List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings,
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
      var newLetExpr = new LetRec(this,
          List.copyOf(this.bindings),
          new Lit(new Literal.Unit()));

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
      return new LetRec(this);
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
      Env newEnv = env.copy();
      var typeVars = new ArrayList<TypeVar<AlgorithmWType>>(this.bindings.size());

      // Fill env with new variables
      for (var binding : this.bindings) {
        var typeVar = new TypeVar<AlgorithmWType>(engine.getCurrentLevel());
        typeVars.add(typeVar);
        newEnv.put(binding.getLeft(),
            new Scheme(List.of(), new AlgorithmWType.Var(this.getLocation(), typeVar)));
      }

      // Solve and unify with new type vars from step 1
      for (int i = 0; i < this.bindings.size(); i++) {
        var binding = this.bindings.get(i);
        InferResult res1 = engine.infer(binding.getRight(), newEnv);
        engine.unify(new AlgorithmWType.Var(this.getLocation(), typeVars.get(i)), res1.type());
      }

      // Fill env with generalized, this step requires full solve of all previous
      // bindings!
      for (int i = 0; i < this.bindings.size(); i++) {
        newEnv.put(this.bindings.get(i).getLeft(),
            new AlgorithmWType.Var(this.getLocation(), typeVars.get(i)).generalize(engine.getCurrentLevel()));
      }

      InferResult res2 = engine.infer(body, newEnv);

      return new InferResult(res2.type());
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof LetRec other && this.bindings.equals(other.bindings)
          && this.body.equals(other.body)
          && super.equals(obj);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.bindings, this.body, super.hashCode());
    }

    @Override
    public void replaceSymbol(Symbol<Expr, AlgorithmWType> original, Symbol<Expr, AlgorithmWType> replacement) {
      // If symbol is shadowed by the env created by this let binding, do not
      // overwrite the value!
      if (this.bindings.stream().anyMatch(bnd -> bnd.getLeft().equals(original))) {
        return;
      }

      // NOTE: the scope instance itself is kept, so parent-scope references of
      // the children remain valid and must not be re-pointed.
      this.body.replaceSymbol(original, replacement);
      this.bindings.forEach(bnd -> bnd.getRight().replaceSymbol(original, replacement));
    }
  }

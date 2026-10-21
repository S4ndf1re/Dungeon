package dgir.core.ir.types.builtin.algorithmw.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWType;
import dgir.core.ir.types.builtin.algorithmw.Env;
import dgir.core.ir.types.builtin.algorithmw.Expr;
import dgir.core.ir.types.builtin.algorithmw.InferResult;
import dgir.core.ir.types.builtin.algorithmw.Scheme;
import dgir.core.ir.types.builtin.algorithmw.Subst;
import dgir.core.ir.types.builtin.algorithmw.TypeInference;

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
  public final class LetSeq extends Expr {

    private List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings;
    private Expr body;

    public LetSeq(Symbol<Expr, AlgorithmWType> param, Expr value,
        Expr body) {
      this.bindings = List.of(Pair.of(param, value));
      this.body = body;
    }

    public LetSeq(Location loc, Symbol<Expr, AlgorithmWType> param, Expr value,
        Expr body) {
      super(loc);
      this.bindings = List.of(Pair.of(param, value));
      this.body = body;
    }

    public LetSeq(List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings,
        Expr body) {
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public LetSeq(Location loc, List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings,
        Expr body) {
      super(loc);
      this.bindings = List.copyOf(bindings);
      this.body = body;
    }

    public LetSeq(LetSeq other) {
      super(other);
      this.bindings = List.copyOf(other.bindings);
      this.body = other.body;
    }

    public LetSeq(LetSeq other, List<Pair<Symbol<Expr, AlgorithmWType>, Expr>> bindings,
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
      var newLetExpr = new LetSeq(this,
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
      return new LetSeq(this);
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

      for (var binding : this.bindings) {
        var param = binding.getLeft();
        var value = binding.getRight();

        InferResult res1 = engine.infer(value, newEnv);

        Scheme generalizedType = res1.type().generalize(engine.getCurrentLevel());

        newEnv.put(param, generalizedType);
      }

      InferResult res2 = engine.infer(body, newEnv);

      return new InferResult(res2.type());
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof LetSeq other && this.bindings.equals(other.bindings)
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

package dgir.core.ir.types.algorithmw;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.compatibility.ExprOrOperator;

public final class Scheme {
    private List<TypeVar> vars;
    private AlgorithmWType type;
    private Optional<ExprOrOperator<Expr, AlgorithmWType>> originExpr;

    public Scheme(List<TypeVar> vars, AlgorithmWType type) {
      this.vars = vars;
      this.type = type;

      this.originExpr = Optional.empty();
    }

    public Scheme(List<TypeVar> vars, AlgorithmWType type, ExprOrOperator<Expr, AlgorithmWType> originExpr) {
      this(vars, type);
      this.originExpr = Optional.ofNullable(originExpr);
    }

    public Scheme(List<TypeVar> vars, AlgorithmWType type, Optional<ExprOrOperator<Expr, AlgorithmWType>> originExpr) {
      this(vars, type);
      this.originExpr = originExpr;
    }

    /**
     * Apply the subst to this scheme. First filter all bound variables from the
     * subst, then apply
     * the filtered subst to the type.
     *
     * @param subst the subst to apply with
     * @return the applied scheme where subst is applied to this
     */
    public Scheme apply(Subst subst) {
      var filtered = new HashMap<TypeVar, AlgorithmWType>(subst.types());

      for (var s : this.vars) {
        filtered.remove(s);
      }

      var newType = new Subst(filtered).apply(this.type);
      return new Scheme(this.vars, newType, this.originExpr);
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

    public AlgorithmWType instantiate(TypeInference engine, Symbol<Expr, AlgorithmWType> value) {
      Subst s = Subst.newEmpty();

      for (var typeVar : this.vars) {
        var fresh = new TypeVar(value);
        s.types().put(typeVar, new AlgorithmWType.Var(fresh));
      }

      return s.apply(this.type);
    }
  }

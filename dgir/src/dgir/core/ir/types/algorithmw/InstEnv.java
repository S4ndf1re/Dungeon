package dgir.core.ir.types.algorithmw;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.types.HashConsing;
import dgir.core.ir.types.Symbol;

public final class InstEnv extends HashConsing<Expr, AlgorithmWType> {
    private HashMap<Symbol<Expr, AlgorithmWType>, Expr> env;
    private HashSet<Pair<Expr, Subst>> visited;

    public InstEnv() {
      this.env = new HashMap<>();
      this.visited = new HashSet<>();
    }

    public InstEnv(InstEnv env) {
      this.env = new HashMap<>(env.env);
      // This is not a copy, but a completely shared reference!
      this.visited = env.visited;
    }

    public void put(Symbol<Expr, AlgorithmWType> sym, Expr expr) {
      this.env.put(sym, expr);
    }

    public Optional<Expr> get(Symbol<Expr, AlgorithmWType> sym) {
      return Optional.ofNullable(this.env.get(sym));
    }

    public void visit(Pair<Expr, Subst> expr) {
      this.visited.add(expr);
    }

    public boolean isVisisted(Pair<Expr, Subst> expr) {
      return this.visited.contains(expr);
    }
  }

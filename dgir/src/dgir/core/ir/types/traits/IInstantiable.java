package dgir.core.ir.types.traits;

import java.util.HashSet;
import java.util.Set;

import dgir.core.ir.types.Expression;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeInferenceSolver;
import dgir.core.ir.types.TypeVar;

public interface IInstantiable<E extends Expression<E, T> & IInstantiable<E, T, S, EngineT>, T extends Type<T>, S extends dgir.core.ir.types.traits.IInstantiable.SolutionContext<E, T, EngineT, S>, EngineT extends TypeInferenceSolver<EngineT, E, T>> {

  public interface SolutionContext<E extends Expression<E, T>, T extends Type<T>, EngineT extends TypeInferenceSolver<EngineT, E, T>, SCtxT extends SolutionContext<E, T, EngineT, SCtxT>> {
    /**
     * Apply the type to the solution, resolting in an applied type that contains
     * the solution in its described type!
     *
     * <p>
     * A second application of an applied type must not result in any changes, as
     * the solution context is already applied to the input type
     */
    public T apply(T type);

    /**
     * Expand a solution using the two types. Normally, this method may jsut return
     * `this`. Algorithms like AlgorithmW actually require solution expansion during
     * beta-reduction of variables
     */
    public SCtxT expand(EngineT engine, E target, T expectedType);

    /**
     * Restrict the solution to the given type variables, each mapped to its
     * fully applied type. The result is canonical: two solutions that agree on
     * the given type variables restrict to equal solutions, no matter what else
     * they contain. Used to key the instantiation memo, as only the type
     * variables reachable from an expression decide its instance.
     */
    public SCtxT restrictTo(Set<TypeVar<T>> typeVars);
  }

  /**
   * Get a mutable cell Expression for the instantiation algorithm!
   */
  public E getCellFor(E origin, E assignment);

  /**
   * Instantiate the correct type instance for code generation.
   * This instance is stored within the expression.
   * Normally, a default implementation is provided,
   * that just applies the solution {@link Subst} to to the previous inferred
   * type.
   * Some expressions, like {@link Abs} or {@link App} need to implement a
   * different
   * version of `instantiateInner`.
   * Especially {@link Abs} needs to collect all fully instantiated instances.
   *
   * <p>
   * The {@link InstEnv} will act as an {@link Env}, but does not store types but
   * rather
   * expressions.
   * Additionally, the {@link InstEnv} will store all visited expressions in
   * combination
   * with the {@link Subst}.
   * Hence further unified solutions will get applied to all Expressions in the
   * tree,
   * even though an expression was visited for a partial solution earlier.
   *
   * @param engine   the inference engine that provides useful helper methods,
   *                 like `unify` and `asExpression`
   * @param env      the instance env, collecting visited expressions and acting
   *                 as a scope-like Env
   * @param solution a partial of full solution that can be used to infer all
   *                 types and instantiations
   */
  public E instantiateInner(EngineT engine, InstEnv<E, T, S> env, S solutionContext);

  /**
   * Instantiate the full expression tree to find and store all instantiations.
   * Additionally, every expression and its inferred type (determined during type
   * inference)
   * is substituted, resulting in a fully typed Expression tree.
   *
   * <p>
   * In addition to the instantiation, a simple form of variable resolution is
   * performed, by beta-reducing variables into the concrete expressions
   * referenced by the ExprVars. This is important for later stage code generation
   *
   * @param engine   the type inference engine used to infer all types
   * @param env      a env storing all in scope expressions
   * @param solution a solution Subst that may be extended with further
   *                 instantioation Substs
   */
  public default E instantiate(EngineT engine, InstEnv<E, T, S> env, S solutionContext) {
    // The memo and hash consing are keyed by the solution restricted to the type
    // variables deciding this instance: the ones reachable from this expression,
    // so equal instances reached under different (caller) solutions are shared,
    // and the ones of the enclosing function's signature, as everything within a
    // function belongs to that function's instance, even if its own type does not
    // mention them.
    // TODO: this is O(n^3) in the worst case: for each of the n visits, restrictTo
    // applies the solution (O(|S|)) to each reachable type variable (O(n)).
    // Collecting the free type variables bottom up only removes the O(n) walk;
    // apply also has to stop being linear in the solution.
    var typeVars = new HashSet<>(env.getReachableTypeVars((E) this));
    typeVars.addAll(env.getFunctionTypeVars());
    return this.instantiate(engine, env, solutionContext, solutionContext.restrictTo(typeVars));
  }

  /**
   * Instantiate this expression, using the given solution as the key of the memo
   * and the hash consing.
   *
   * @param keySolution the solution identifying this instance
   */
  private E instantiate(EngineT engine, InstEnv<E, T, S> env, S solutionContext, S keySolution) {
    // NOTE(jan): it is not 100% certain, that this extra copy and solution
    // application is actually needed. Though one might make the argument, that in
    // later iterations of this code, the solution context may be redunant entirely.
    E exprCopy = ((E) this).copy();
    exprCopy.setInferredType(exprCopy.getInferredType().map(ty -> solutionContext.apply(ty)));

    // Not hash consing: this expression is not an instance yet. It only gives every
    // visit of this expression and key the same identity, for a recursive visit to
    // find its way back through a cell.
    E expr = env.getVisitIdentity(exprCopy, keySolution);

    // Variables must always be visited, while other epxressions must be
    // instantiated, as long as its not a recursive instantiation.
    if (!(expr instanceof IVariable) && env.isVisisted(expr, keySolution)) {
      // In sequential soltuions, this call works, as the soltuion is already
      // registered! hash cons again, just in case!
      if (env.hasSolution(expr, keySolution)) {
        return env.getSolutionOrThrow(expr, keySolution);
      } else {
        var cell = getCellFor(expr, expr);
        assert cell instanceof IExpressionCell;
        env.addCellForExpr(expr, (IExpressionCell<E, T>) cell);
        return (E) cell;
      }
    }

    env.visit(expr, keySolution);

    E instantiated = expr.instantiateInner(engine, env, solutionContext);
    instantiated.setInferredType(instantiated.getInferredType().map(ty -> solutionContext.apply(ty)));

    // The final instance is only known now: different expressions may result in an
    // equal instance.
    E instance = env.getConsed(instantiated, keySolution);

    env.getCellsForExpressions(expr).stream().forEach(e -> e.replaceIfMatches(expr, instance));
    // The beta-reduction for variables.
    // When the variable is in scope, actually replace the returned
    // expression with the referenced instantiated Expr instance.
    // This will not work for abstract ExprAbs parameters,
    // as those are not bound to concrete expressions.
    //
    // FUTURE_WORK(jan): return a fully beta-reduced expression tree
    if (instance instanceof IVariable) {
      var instantiatedVariable = (IVariable<E, T>) instance;
      var referencedFromEnv = env.getExprAndPosition(instantiatedVariable.getReferencedVariable());
      if (referencedFromEnv.isPresent()) {
        var scopeExpression = env.getScopeExpression(instantiatedVariable.getReferencedVariable());

        var referencedExprAsExpr = engine.asExpression(referencedFromEnv.get().getLeft());
        var referencedInferredType = referencedExprAsExpr.getInferredType();

        if (referencedInferredType.isPresent() && instance.getInferredType().isPresent()) {
          S finalSolutionCtx = solutionContext.expand(engine, referencedExprAsExpr,
              instance.getInferredType().get());

          var copiedExpr = referencedExprAsExpr.copy();
          copiedExpr.setParentScopeExpression(scopeExpression, referencedFromEnv.map(e -> e.getRight()));

          // A referenced function is its own instance, independent of the function
          // calling it: only the type variables reachable from it decide its key,
          // and it starts a new enclosing function for everything within it.
          E instantiatedReferenced;
          if (copiedExpr.isA(IAbstraction.class)) {
            IInstantiable<E, T, S, EngineT> function = copiedExpr;
            instantiatedReferenced = function.instantiate(engine, env.forFunction(copiedExpr), finalSolutionCtx,
                finalSolutionCtx.restrictTo(env.getReachableTypeVars(copiedExpr)));
          } else {
            instantiatedReferenced = copiedExpr.instantiate(engine, env, finalSolutionCtx);
          }

          // After instantiation, return the actual expression not the variable!
          // NOTE: the instantiatedReferenced is already hash-consed
          env.setSolution(instance, keySolution, instantiatedReferenced);
          env.setSolution(expr, keySolution, instantiatedReferenced);
          return instantiatedReferenced;
        } else {
          throw new RuntimeException("The infered type is present, hence no instantiation can occur");
        }
      }
    }

    env.setSolution(expr, keySolution, instance);
    env.setSolution(instance, keySolution, instance);
    return instance;
  }

}

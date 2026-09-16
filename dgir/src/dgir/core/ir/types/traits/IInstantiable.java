package dgir.core.ir.types.traits;

import dgir.core.ir.types.Expression;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeInferenceSolver;

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
   * Some expressions, like {@link ExprAbs} or {@link ExprApp} need to implement a
   * different
   * version of `instantiateInner`.
   * Especially {@link ExprAbs} needs to collect all fully instantiated instances.
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
  @SuppressWarnings("unchecked")
  public default E instantiate(EngineT engine, InstEnv<E, T, S> env, S solutionContext) {
    E expr = env.getConsed((E) this);
    // Variables must always be visited, while other epxressions must be
    // instantiated, as long as its not a recursive instantiation.
    if (!(expr instanceof IVariable) && env.isVisisted(expr, solutionContext)) {
      // In sequential soltuions, this call works, as the soltuion is already
      // registered! hash cons again, just in case!
      if (env.hasSolution(expr, solutionContext)) {
        return env.getSolutionOrThrow(expr, solutionContext);
      } else {
        var cell = getCellFor(expr, expr);
        assert cell instanceof IExpressionCell;
        env.addCellForExpr(expr, (IExpressionCell<E, T>) cell);
        return (E) cell;
      }
    }

    // FIXME: this actually will hog a lot of memory in the long term, depending on
    // the expression size!
    // A possible solution would be to filter the subst to only occuring type
    // variables! And removing all already applied solutions!
    env.visit(expr, solutionContext);

    E instantiated = expr.instantiateInner(engine, env, solutionContext);
    instantiated.setInferredType(instantiated.getInferredType().map(ty -> solutionContext.apply(ty)));

    env.getCellsForExpressions(expr).stream().forEach(e -> e.replaceIfMatches(expr, instantiated));
    var instantiatedTarget = env.getConsed(instantiated);

    // The beta-reduction for variables.
    // When the variable is in scope, actually replace the returned
    // expression with the referenced instantiated Expr instance.
    // This will not work for abstract ExprAbs parameters,
    // as those are not bound to concrete expressions.
    //
    // FUTURE_WORK(jan): return a fully beta-reduced expression tree
    if (instantiatedTarget instanceof IVariable) {
      var instantiatedVariable = (IVariable<E, T>) instantiatedTarget;
      var referencedFromEnv = env.getExprAndPosition(instantiatedVariable.getReferencedVariable());
      if (referencedFromEnv.isPresent()) {
        var scopeExpression = env.getScopeExpression(instantiatedVariable.getReferencedVariable());

        var referencedExprAsExpr = engine.asExpression(referencedFromEnv.get().getLeft());
        var referencedInferredType = referencedExprAsExpr.getInferredType();

        if (referencedInferredType.isPresent() && instantiatedTarget.getInferredType().isPresent()) {
          S finalSolutionCtx = solutionContext.expand(engine, referencedExprAsExpr,
              instantiatedTarget.getInferredType().get());

          var copiedExpr = referencedExprAsExpr.copy();
          copiedExpr.setParentScopeExpression(scopeExpression, referencedFromEnv.map(e -> e.getRight()));

          E instantiatedReferenced = copiedExpr.instantiate(engine, env, finalSolutionCtx);

          // After instantiation, return the actual expression not the variable!
          // NOTE: the instantiatedReferenced is already hash-consed
          env.setSolution(instantiatedTarget, solutionContext, instantiatedReferenced);
          env.setSolution(expr, solutionContext, instantiatedReferenced);
          return instantiatedReferenced;
        }
      }
    }

    env.setSolution(expr, solutionContext, instantiatedTarget);
    env.setSolution(instantiatedTarget, solutionContext, instantiatedTarget);
    return instantiatedTarget;
  }

}

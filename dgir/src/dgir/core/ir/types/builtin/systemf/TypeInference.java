package dgir.core.ir.types.builtin.systemf;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.TypeInferenceSolver;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypeVar.TypeVarScope;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.builtin.systemf.expr.Abs;
import dgir.core.ir.types.builtin.systemf.expr.Ann;
import dgir.core.ir.types.builtin.systemf.expr.App;
import dgir.core.ir.types.builtin.systemf.expr.Let;
import dgir.core.ir.types.builtin.systemf.expr.LitExpr;
import dgir.core.ir.types.builtin.systemf.expr.Seq;
import dgir.core.ir.types.builtin.systemf.expr.Var;
import dgir.core.ir.types.builtin.systemf.expr.Block;
import dgir.core.ir.types.compatibility.ConvertedOperationBuffer;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;

public final class TypeInference
    extends TypeInferenceSolver<TypeInference, Expr, SystemFType> {

  private ConvertedOperationBuffer<Expr, SystemFType, TypeInference> operationToExprBuffer;
  private Context startContext;

  public TypeInference() {
    this(new TypeDialectConverterRegistry());
  }

  public TypeInference(TypeDialectConverterRegistry registry) {
    super(registry);
    operationToExprBuffer = new ConvertedOperationBuffer<>();
    startContext = new Context();
  }

  private static SystemFType convertInnerGeneralParameterized(GeneralParameterizedNominalType type) {
    List<SystemFType> paramTypes = type.getTypedParameters().stream().map(param -> switch (param) {
      case GeneralTypeParameter.Concrete con -> convertInnerGeneralParameterized(con.ty());
      case GeneralTypeParameter.Unknown unk -> new SystemFType.EtVar(new TypeVar<>());
      case GeneralTypeParameter.Numeric num -> new SystemFType.NumericType(num.number());
    }).toList();

    return new SystemFType.Lit(type.getIdent(), paramTypes);
  }

  @Override
  public Pair<SystemFType, Optional<ConversionContext<Expr, SystemFType>>> generalNominalTypeToInferenceType(
      GeneralParameterizedNominalType type,
      Optional<ConversionContext<Expr, SystemFType>> ctx) {

    var mappedCtx = ctx.map(c -> {
      if (!(c instanceof Context)) {
        throw new IllegalArgumentException("ctx must be of instance Context");
      }

      return (Context) c;
    });

    try (TypeVarScope<SystemFType> scope = TypeVar.addScope()) {
      var resultType = convertInnerGeneralParameterized(type);
      var newCtx = mappedCtx.map(c -> {
        var newC = c.copy();
        for (var etvar : scope.createdVars()) {
          newC.push(new Entry.ETVarBnd(etvar));
        }

        return newC;
      });

      return Pair.of(resultType, newCtx.map(c -> (ConversionContext<Expr, SystemFType>) c));
    } catch (Exception e) {
      throw new IllegalArgumentException("This will never happen!");
    }
  }

  @Override
  public Expr Let(List<Pair<Symbol<Expr, SystemFType>, Expr>> bindings, Expr body) {
    return new Let(bindings, body);
  }

  @Override
  public Expr Seq(List<Expr> exprs) {
    return new Seq(exprs);
  }

  @Override
  public Expr Var(Symbol<Expr, SystemFType> symbol) {
    return new Var(symbol);
  }

  @Override
  public Expr Lit(Literal lit) {
    return new LitExpr(lit);
  }

  @Override
  public Expr App(Expr func, List<Expr> args) {
    if (args.isEmpty()) {
      return new App(func);
    }
    Expr app = func;
    for (var arg : args) {
      app = new App(app, arg);
    }
    return app;
  }

  @Override
  public Expr Abs(List<Symbol<Expr, SystemFType>> params, Expr body) {
    // System F abstractions require a type annotation per parameter. Following
    // generalNominalTypeToInferenceType, unannotated parameters are introduced
    // as fresh existentials. The conversion helpers always run before solve, so
    // the existential binders can be pushed directly into the start context.
    Expr expr = body;
    if (params.isEmpty()) {
      expr = new Abs(expr);
    } else {
      for (int i = params.size() - 1; i >= 0; i--) {
        var etvar = new TypeVar<SystemFType>();
        this.startContext.push(new Entry.ETVarBnd(etvar));
        expr = new Abs(params.get(i), new SystemFType.EtVar(etvar), expr);
      }
    }
    return expr;
  }

  @Override
  public Expr Ann(Expr body, SystemFType type) {
    return new Ann(body, type);
  }

  @Override
  public Expr Block(Expr body) {
    return new Block(body);
  }

  @Override
  public SolveResult<Expr, SystemFType> solve(ExprOrOperator<Expr, SystemFType> exprOrOp) {
    var expr = this.asExpression(exprOrOp);
    var res = this.infer(this.getStartContext(), expr);
    var solutionCtx = res.ctx().copy();
    SystemFType finalType = solutionCtx.apply(res.type());

    var instantiated = expr.instantiate(this, new InstEnv<>(expr), solutionCtx);

    instantiated = this.postSolve(instantiated);

    return new SolveResult<>(finalType, expr, instantiated);
  }

  public Context getStartContext() {
    return this.startContext;
  }

  public void setStartContext(Context newCtx) {
    this.startContext = newCtx;
  }

  public SystemFType substType(
      TypeVar<SystemFType> tyVar,
      SystemFType replacement,
      SystemFType target) {
    return target.substType(tyVar, replacement);
  }

  @Override
  public SystemFType Arrow(SystemFType from, SystemFType to) {
    return new SystemFType.Arrow(from, to);
  }

  public TypeResult infer(Context ctx, Expr expr) {
    var inferredResult = expr.infer(this, ctx);
    expr.setInferredType(Optional.ofNullable(inferredResult.ctx().apply(inferredResult.type())));
    return inferredResult;
  }

  public CheckResult check(Context ctx, ExprOrOperator<Expr, SystemFType> exprParam, SystemFType ty) {
    Expr expr = null;
    if (exprParam.isExpr()) {
      expr = exprParam.getExpr();
    } else if (exprParam.isOperator()) {
      expr = this.operationToExprBuffer.operationToExpr(this, exprParam.getOp(), this.registry, Expr.class);
    } else {
      throw new RuntimeException("Can never happen, due to exhaustive If");
    }

    if (ty instanceof SystemFType.ForAll forall) {
      var newCtx = ctx.copy();
      newCtx.push(new Entry.TVarBnd(forall.boundVar));
      var bodyCheck = this.check(newCtx, expr, forall.body);

      var break3Result = bodyCheck.ctx().break3(
          entry -> entry instanceof Entry.TVarBnd bnd &&
              bnd.tyVar().equals(forall.boundVar));

      var finalCtx = new Context(break3Result.left(), bodyCheck.ctx());

      return new CheckResult(finalCtx);
    } else {
      return expr.check(this, ctx, ty);
    }
  }

  public SubtypeResult subtype(
      Context ctx,
      SystemFType ty1,
      SystemFType ty2) {

    if (ty1 instanceof SystemFType.Lit lit1 && ty2 instanceof SystemFType.Lit lit2) {
      if (lit1.parameters.size() != lit2.parameters.size()) {
        throw new TypingException.SubtypingFailed(ty1, ty2);
      } else if (!lit1.ident.equals(lit2.ident)) {
        throw new TypingException.SubtypingFailed(ty1, ty2);
      }
      var context = ctx.copy();
      for (int i = 0; i < lit1.parameters.size(); i++) {
        var param1 = lit1.parameters.get(i);
        var param2 = lit2.parameters.get(i);
        var subRes = this.subtype(context, param1, param2);
        context = subRes.ctx();
      }
      if (lit1.ident.equals(lit2.ident)) {

        return new SubtypeResult(context.copy());
      } else {
        throw new RuntimeException("Error");
      }
    } else if (ty1 instanceof SystemFType.NumericType n1 && ty2 instanceof SystemFType.NumericType n2) {
      if (n1.size != n2.size) {
        throw new TypingException.SubtypingFailed(ty1, ty2);
      }
      return new SubtypeResult(ctx.copy());
    } else if (ty1 instanceof SystemFType.Var v1 &&
        ty2 instanceof SystemFType.Var v2 &&
        v1.tyVar.equals(v2.tyVar)) {
      return new SubtypeResult(ctx.copy());
    } else if (ty1 instanceof SystemFType.EtVar v1 &&
        ty2 instanceof SystemFType.EtVar v2 &&
        v1.tyVar.equals(v2.tyVar)) {
      return new SubtypeResult(ctx.copy());
    } else if (ty1 instanceof SystemFType.Arrow a1 &&
        ty2 instanceof SystemFType.Arrow a2) {
      var covArg = this.subtype(ctx, a1.from, a2.from);
      var covRes = this.subtype(covArg.ctx(), a1.to, a2.to);

      return new SubtypeResult(covRes.ctx());
    } else if (ty1 instanceof SystemFType.Tuple t1 && ty2 instanceof SystemFType.Tuple t2) {
      if (t1.elements.size() != t2.elements.size()) {
        throw new TypingException.SubtypingFailed(ty1, ty2);
      }

      var context = ctx.copy();
      for (int i = 0; i < t1.elements.size(); i++) {
        var subRes = this.subtype(context, t1.elements.get(i), t2.elements.get(i));
        context = subRes.ctx();
      }

      return new SubtypeResult(context);
    } else if (ty2 instanceof SystemFType.ForAll forall) {
      Context newCtx = ctx.copy();
      newCtx.push(new Entry.TVarBnd(forall.boundVar));
      SubtypeResult subtypeRes = this.subtype(newCtx, ty1, forall.body);
      Break3Result breakRes = subtypeRes.ctx().break3(
          entry -> entry instanceof Entry.TVarBnd bnd &&
              bnd.tyVar().equals(forall.boundVar));
      Context finalCtx = new Context(breakRes.left(), subtypeRes.ctx());

      return new SubtypeResult(finalCtx);
    } else if (ty1 instanceof SystemFType.ForAll forall) {
      var substT1 = this.substType(
          forall.boundVar,
          new SystemFType.EtVar(forall.boundVar),
          forall.body);

      var newCtx = ctx.copy();
      newCtx.push(new Entry.ETVarBnd(forall.boundVar));
      var mark = new Entry.Mark();
      newCtx.push(mark);

      var subtypeRes = this.subtype(newCtx, substT1, ty2);
      var breakRes = subtypeRes.ctx().break3(
          entry -> entry instanceof Entry.Mark m && m.equals(mark));
      var finalCtx = new Context(breakRes.left(), subtypeRes.ctx());
      return new SubtypeResult(finalCtx);
    } else if (ty1 instanceof SystemFType.EtVar etvar && !ty2.occursCheck(etvar.tyVar)) {
      var instLRes = this.instL(ctx, etvar.tyVar, ty2);
      return new SubtypeResult(instLRes.ctx());
    } else if (ty2 instanceof SystemFType.EtVar etvar && !ty1.occursCheck(etvar.tyVar)) {
      var instRRes = this.instR(ctx, ty1, etvar.tyVar);
      return new SubtypeResult(instRRes.ctx());
    } else {
      throw new TypingException.SubtypingFailed(ty1, ty2);
    }
  }

  InstResult instL(Context ctx, TypeVar<SystemFType> a, SystemFType ty) {
    if (ty instanceof SystemFType.EtVar etvar && ctx.before(a, etvar.tyVar)) {
      var breakRes = ctx.break3(
          entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(etvar.tyVar));
      var newCtx = Context.fromParts(
          breakRes.left(),
          new Entry.SETVarBnd(etvar.tyVar, new SystemFType.EtVar(a)),
          breakRes.right(), ctx);
      return new InstResult(newCtx);
    } else if (ty instanceof SystemFType.Lit lit) {
      try (var scope = TypeVar.<SystemFType>addScope()) {
        var litType = new SystemFType.Lit(lit.ident,
            lit.parameters.stream().map(p -> (SystemFType) new SystemFType.EtVar(new TypeVar<>())).toList());

        List<TypeVar<SystemFType>> existentials = scope.createdVars();

        var breakRes = ctx.break3(entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(a));

        Context newCtx = new Context(breakRes.left(), ctx);
        newCtx.push(new Entry.SETVarBnd(a, litType));
        for (var ext : existentials) {
          newCtx.push(new Entry.ETVarBnd(ext));
        }
        newCtx.extend(breakRes.right());

        for (int i = 0; i < existentials.size(); i++) {
          var ext = existentials.get(i);
          var param = lit.parameters.get(i);
          var paramApplied = newCtx.apply(param);
          var instLRes = this.instL(newCtx, ext, paramApplied);
          newCtx = instLRes.ctx();
        }

        return new InstResult(newCtx);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else if (ty instanceof SystemFType.Arrow arrow) {
      var a1 = new TypeVar<SystemFType>();
      var a2 = new TypeVar<SystemFType>();
      var breakRes = ctx.break3(
          entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(a));
      var arrowType = new SystemFType.Arrow(
          new SystemFType.EtVar(a1),
          new SystemFType.EtVar(a2));

      var newCtx = new Context(breakRes.left(), ctx);
      newCtx.push(new Entry.SETVarBnd(a, arrowType));
      newCtx.push(new Entry.ETVarBnd(a1));
      newCtx.push(new Entry.ETVarBnd(a2));
      newCtx.extend(breakRes.right());

      var instRRes = this.instR(newCtx, arrow.from, a1);
      var t2Applied = instRRes.ctx().apply(arrow.to);
      var instLRes = this.instL(instRRes.ctx(), a2, t2Applied);

      return new InstResult(instLRes.ctx());
    } else if (ty instanceof SystemFType.Tuple tuple) {
      try (var scope = TypeVar.<SystemFType>addScope()) {
        var tupleType = new SystemFType.Tuple(
            tuple.elements.stream().map(p -> (SystemFType) new SystemFType.EtVar(new TypeVar<>())).toList());

        List<TypeVar<SystemFType>> existentials = scope.createdVars();

        var breakRes = ctx.break3(entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(a));

        Context newCtx = new Context(breakRes.left(), ctx);
        newCtx.push(new Entry.SETVarBnd(a, tupleType));
        for (var ext : existentials) {
          newCtx.push(new Entry.ETVarBnd(ext));
        }
        newCtx.extend(breakRes.right());

        for (int i = 0; i < existentials.size(); i++) {
          var ext = existentials.get(i);
          var paramApplied = newCtx.apply(tuple.elements.get(i));
          var instLRes = this.instL(newCtx, ext, paramApplied);
          newCtx = instLRes.ctx();
        }

        return new InstResult(newCtx);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else if (ty instanceof SystemFType.ForAll forall) {
      var newCtx = ctx.copy();
      newCtx.push(new Entry.TVarBnd(forall.boundVar));
      var instLRes = this.instL(newCtx, a, forall.body);
      var breakRes = instLRes.ctx().break3(
          entry -> entry instanceof Entry.TVarBnd bnd &&
              bnd.tyVar().equals(forall.boundVar));
      var finalCtx = new Context(breakRes.left(), instLRes.ctx());
      return new InstResult(finalCtx);
    } else if (ty.isMono()) {
      if (ty.occursCheck(a)) {
        throw new TypingException.OccursCheckFailed(ty, a);
      }

      var breakRes = ctx.break3(
          entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(a));
      var newCtx = Context.fromParts(
          breakRes.left(),
          new Entry.SETVarBnd(a, ty),
          breakRes.right(), ctx);
      return new InstResult(newCtx);
    } else {
      throw new TypingException.InstantiationError(
          "InstL Instantiation error " + ctx + " |- " + ty);
    }
  }

  InstResult instR(Context ctx, SystemFType ty, TypeVar<SystemFType> a) {
    if (ty instanceof SystemFType.EtVar etvar) {
      var breakRes = ctx.break3(
          entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(etvar.tyVar));
      var newCtx = Context.fromParts(
          breakRes.left(),
          new Entry.SETVarBnd(etvar.tyVar, new SystemFType.EtVar(a)),
          breakRes.right(), ctx);

      return new InstResult(newCtx);
    } else if (ty instanceof SystemFType.Lit lit) {
      try (var scope = TypeVar.<SystemFType>addScope()) {
        var litType = new SystemFType.Lit(lit.ident,
            lit.parameters.stream().map(p -> (SystemFType) new SystemFType.EtVar(new TypeVar<>())).toList());

        List<TypeVar<SystemFType>> existentials = scope.createdVars();

        var breakRes = ctx.break3(entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(a));

        Context newCtx = new Context(breakRes.left(), ctx);
        newCtx.push(new Entry.SETVarBnd(a, litType));
        for (var ext : existentials) {
          newCtx.push(new Entry.ETVarBnd(ext));
        }
        newCtx.extend(breakRes.right());

        for (int i = 0; i < existentials.size(); i++) {
          var ext = existentials.get(i);
          var param = lit.parameters.get(i);
          var paramApplied = newCtx.apply(param);
          var instRRes = this.instR(newCtx, paramApplied, ext);
          newCtx = instRRes.ctx();
        }

        return new InstResult(newCtx);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

    } else if (ty instanceof SystemFType.Arrow arrow) {
      var a1 = new TypeVar<SystemFType>();
      var a2 = new TypeVar<SystemFType>();
      var breakRes = ctx.break3(
          entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(a));
      var arrowType = new SystemFType.Arrow(
          new SystemFType.EtVar(a1),
          new SystemFType.EtVar(a2));

      var newCtx = new Context(breakRes.left(), ctx);
      newCtx.push(new Entry.SETVarBnd(a, arrowType));
      newCtx.push(new Entry.ETVarBnd(a1));
      newCtx.push(new Entry.ETVarBnd(a2));
      newCtx.extend(breakRes.right());

      var instLRes = this.instL(newCtx, a1, arrow.from);
      var t2Applied = instLRes.ctx().apply(arrow.to);
      var instRRes = this.instR(instLRes.ctx(), t2Applied, a2);

      return new InstResult(instRRes.ctx());
    } else if (ty instanceof SystemFType.Tuple tuple) {
      try (var scope = TypeVar.<SystemFType>addScope()) {
        var tupleType = new SystemFType.Tuple(
            tuple.elements.stream().map(p -> (SystemFType) new SystemFType.EtVar(new TypeVar<>())).toList());

        List<TypeVar<SystemFType>> existentials = scope.createdVars();

        var breakRes = ctx.break3(entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(a));

        Context newCtx = new Context(breakRes.left(), ctx);
        newCtx.push(new Entry.SETVarBnd(a, tupleType));
        for (var ext : existentials) {
          newCtx.push(new Entry.ETVarBnd(ext));
        }
        newCtx.extend(breakRes.right());

        for (int i = 0; i < existentials.size(); i++) {
          var ext = existentials.get(i);
          var paramApplied = newCtx.apply(tuple.elements.get(i));
          var instRRes = this.instR(newCtx, paramApplied, ext);
          newCtx = instRRes.ctx();
        }

        return new InstResult(newCtx);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else if (ty instanceof SystemFType.ForAll forall) {
      var substT = this.substType(
          forall.boundVar,
          new SystemFType.EtVar(forall.boundVar),
          forall.body);

      var newCtx = ctx.copy();
      newCtx.push(new Entry.ETVarBnd(forall.boundVar));
      var mark = new Entry.Mark();
      newCtx.push(mark);

      var instRRes = this.instR(newCtx, substT, a);
      var breakRes = instRRes.ctx().break3(
          entry -> entry instanceof Entry.Mark m && m.equals(mark));
      var finalCtx = new Context(breakRes.left(), instRRes.ctx());
      return new InstResult(finalCtx);
    } else if (ty.isMono()) {
      if (ty.occursCheck(a)) {
        throw new TypingException.OccursCheckFailed(ty, a);
      }

      var breakRes = ctx.break3(
          entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(a));
      var newCtx = Context.fromParts(
          breakRes.left(),
          new Entry.SETVarBnd(a, ty),
          breakRes.right(), ctx);
      return new InstResult(newCtx);
    } else {
      throw new TypingException.InstantiationError(
          "InstR Instantiation error " + ctx + " |- " + ty);
    }
  }

}

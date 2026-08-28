package dgir.core.ir.types.systemf;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Value;
import dgir.core.ir.Operation;
import dgir.core.ir.types.GeneralBlock;
import dgir.core.ir.types.GeneralParameterizedNominalType;
import dgir.core.ir.types.GeneralParameterizedNominalType.GeneralTypeParameter;
import dgir.core.ir.types.InferenceTree;
import dgir.core.ir.types.Literal;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeDialect;
import dgir.core.ir.types.TypeVar;
import dgir.core.ir.types.TypeVar.TypeVarScope;
import dgir.core.ir.types.TypingException;
import dgir.core.ir.types.compatibility.ConvertedOperationBuffer;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;
import dgir.core.ir.types.compatibility.ExprOrOperator;

public final class TypeInference
    extends TypeDialect.TypeInferenceSolver<ExprOrOperator<Expr, SystemFType>, Expr, SystemFType> {

  private ConvertedOperationBuffer<ExprOrOperator<Expr, SystemFType>, Expr, SystemFType, TypeInference> operationToExprBuffer;

  public TypeInference() {
    this(new TypeDialectConverterRegistry());
  }

  public TypeInference(TypeDialectConverterRegistry registry) {
    super(registry);
    operationToExprBuffer = new ConvertedOperationBuffer<>();
  }

  private static SystemFType convertInnerGeneralParameterized(GeneralParameterizedNominalType type) {
    List<SystemFType> paramTypes = type.getTypedParameters().stream().map(param -> switch (param) {
      case GeneralTypeParameter.Concrete con -> convertInnerGeneralParameterized(con.ty());
      case GeneralTypeParameter.Unknown unk -> new SystemFType.EtVar(new TypeVar());
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

    try (TypeVarScope scope = TypeVar.addScope()) {
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
  public Expr generalBlockToInferenceExpr(GeneralBlock block) {
    ArrayList<Pair<Symbol<Expr, SystemFType>, Expr>> bindings = new ArrayList<>();
    Optional<Symbol<Expr, SystemFType>> lastValue = Optional.empty();

    for (var op : block.getOperations()) {
      var opOutput = op.getOutput();
      if (opOutput.isPresent()) {
        Symbol<Expr, SystemFType> sym = Symbol.<Expr, SystemFType>of(opOutput.get().getValue());
        bindings.add(Pair.of(sym, this.asExpression(ExprOrOperator.of(op))));
        lastValue = Optional.of(sym);
      } else {
        /*
         * NOTE: handle everything as a returnable value, even though something like a
         * function is not actually a expression! This is done to correctly typecheck
         * each function and their parameters!
         */
        Symbol<Expr, SystemFType> val = Symbol.<Expr, SystemFType>of(new Value());
        bindings.add(Pair.of(val, this.asExpression(ExprOrOperator.of(op))));
        lastValue = Optional.of(val);
      }
    }

    if (lastValue.isPresent()) {
      return new Expr.Let(bindings, new Expr.Var(lastValue.get()));
    } else {
      return new Expr.Let(bindings, new Expr.LitExpr(new Literal.Unit()));
    }
  }

  @Override
  public Pair<Type, Expr> solve(ExprOrOperator<Expr, SystemFType> exprOrOp) {
    var expr = this.asExpression(exprOrOp);
    var res = this.infer(new Context(), expr);
    var solutionCtx = res.ctx().copy();

    SystemFType finalType = solutionCtx.apply(res.type());
    expr.instantiate(this, solutionCtx);

    return Pair.of(finalType, expr);
  }

  SystemFType substType(
      TypeVar tyVar,
      SystemFType replacement,
      SystemFType target) {
    return target.substType(tyVar, replacement);
  }

  public TypeResult infer(Context ctx, Expr expr) {
    var inferredResult = expr.infer(this, ctx);
    expr.setInferredType(inferredResult.ctx().apply(inferredResult.type()));
    return inferredResult;
  }

  public Expr asExpression(ExprOrOperator<Expr, SystemFType> expr) {
    if (expr.isExpr()) {
      return expr.getExpr();
    } else if (expr.isOperator()) {
      Operation op = expr.getOp();
      return this.operationToExprBuffer.operationToExpr(this, op, this.registry, Expr.class);
    } else {
      throw new RuntimeException("unimplemented for OPs");
    }
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

    var input = ctx + " |- " + expr + " <=" + ty;

    if (ty instanceof SystemFType.ForAll forall) {
      var newCtx = ctx.copy();
      newCtx.push(new Entry.TVarBnd(forall.boundVar));
      var bodyCheck = this.check(newCtx, expr, forall.body);

      var break3Result = bodyCheck.ctx().break3(
          entry -> entry instanceof Entry.TVarBnd bnd &&
              bnd.tyVar().equals(forall.boundVar));

      var finalCtx = new Context(break3Result.left(), bodyCheck.ctx());

      return new CheckResult(
          finalCtx,
          new InferenceTree(
              "ChkAll",
              input,
              "" + finalCtx,
              List.of(bodyCheck.tree())));
    } else {
      return expr.check(this, ctx, ty);
    }
  }

  public SubtypeResult subtype(
      Context ctx,
      SystemFType ty1,
      SystemFType ty2) {
    var input = ctx + " |- " + ty1 + " <: " + ty2;

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

        return new SubtypeResult(
            context.copy(),
            new InferenceTree("SubRefl", input, "" + ctx, List.of()));
      } else {
        throw new RuntimeException("Error");
      }
    } else if (ty1 instanceof SystemFType.NumericType n1 && ty2 instanceof SystemFType.NumericType n2) {
      if (n1.size != n2.size) {
        throw new TypingException.SubtypingFailed(ty1, ty2);
      }
      return new SubtypeResult(ctx.copy(), new InferenceTree("SubReflNum", input, "" + ctx, List.of()));
    } else if (ty1 instanceof SystemFType.Var v1 &&
        ty2 instanceof SystemFType.Var v2 &&
        v1.tyVar.equals(v2.tyVar)) {
      return new SubtypeResult(
          ctx.copy(),
          new InferenceTree("SubReflTVar", input, "" + ctx, List.of()));
    } else if (ty1 instanceof SystemFType.EtVar v1 &&
        ty2 instanceof SystemFType.EtVar v2 &&
        v1.tyVar.equals(v2.tyVar)) {
      return new SubtypeResult(
          ctx.copy(),
          new InferenceTree("SubReflETVar", input, "" + ctx, List.of()));
    } else if (ty1 instanceof SystemFType.Arrow a1 &&
        ty2 instanceof SystemFType.Arrow a2) {
      var covArg = this.subtype(ctx, a1.from, a2.from);
      var covRes = this.subtype(covArg.ctx(), a1.to, a2.to);

      return new SubtypeResult(
          covRes.ctx(),
          new InferenceTree(
              "SubArr",
              input,
              "" + covRes.ctx(),
              List.of(covArg.tree(), covRes.tree())));
    } else if (ty2 instanceof SystemFType.ForAll forall) {
      Context newCtx = ctx.copy();
      newCtx.push(new Entry.TVarBnd(forall.boundVar));
      SubtypeResult subtypeRes = this.subtype(newCtx, ty1, forall.body);
      Break3Result breakRes = subtypeRes.ctx().break3(
          entry -> entry instanceof Entry.TVarBnd bnd &&
              bnd.tyVar().equals(forall.boundVar));
      Context finalCtx = new Context(breakRes.left(), subtypeRes.ctx());

      return new SubtypeResult(
          finalCtx,
          new InferenceTree(
              "SubAllR",
              input,
              "" + finalCtx,
              List.of(subtypeRes.tree())));
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
      return new SubtypeResult(
          finalCtx,
          new InferenceTree(
              "SubAllL",
              input,
              "" + finalCtx,
              List.of(subtypeRes.tree())));
    } else if (ty1 instanceof SystemFType.EtVar etvar && !ty2.occursCheck(etvar.tyVar)) {
      var instLRes = this.instL(ctx, etvar.tyVar, ty2);
      var output = "" + instLRes.ctx();
      return new SubtypeResult(
          instLRes.ctx(),
          new InferenceTree("SubInstL", input, output, List.of(instLRes.tree())));
    } else if (ty2 instanceof SystemFType.EtVar etvar && !ty1.occursCheck(etvar.tyVar)) {
      var instRRes = this.instR(ctx, ty1, etvar.tyVar);
      var output = "" + instRRes.ctx();
      return new SubtypeResult(
          instRRes.ctx(),
          new InferenceTree("SubInstR", input, output, List.of(instRRes.tree())));
    } else {
      throw new TypingException.SubtypingFailed(ty1, ty2);
    }
  }

  InstResult instL(Context ctx, TypeVar a, SystemFType ty) {
    var input = ctx + " |- ^" + a + " :=< " + ty;

    if (ty instanceof SystemFType.EtVar etvar && ctx.before(a, etvar.tyVar)) {
      var breakRes = ctx.break3(
          entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(etvar.tyVar));
      var newCtx = Context.fromParts(
          breakRes.left(),
          new Entry.SETVarBnd(etvar.tyVar, new SystemFType.EtVar(a)),
          breakRes.right(), ctx);
      return new InstResult(
          newCtx,
          new InferenceTree("InstLReach", input, "" + newCtx, List.of()));
    } else if (ty instanceof SystemFType.Lit lit) {
      try (var scope = TypeVar.addScope()) {
        var litType = new SystemFType.Lit(lit.ident,
            lit.parameters.stream().map(p -> (SystemFType) new SystemFType.EtVar(new TypeVar())).toList());

        List<TypeVar> existentials = scope.createdVars();

        var breakRes = ctx.break3(entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(a));

        Context newCtx = new Context(breakRes.left(), ctx);
        newCtx.push(new Entry.SETVarBnd(a, litType));
        for (var ext : existentials) {
          newCtx.push(new Entry.ETVarBnd(ext));
        }
        newCtx.extend(breakRes.right());

        ArrayList<InferenceTree> trees = new ArrayList<>();

        for (int i = 0; i < existentials.size(); i++) {
          var ext = existentials.get(i);
          var param = lit.parameters.get(i);
          var paramApplied = newCtx.apply(param);
          var instLRes = this.instL(newCtx, ext, paramApplied);
          newCtx = instLRes.ctx();
          trees.add(instLRes.tree());
        }

        return new InstResult(newCtx, new InferenceTree("InstLLit", input, "" + newCtx, List.copyOf(trees)));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else if (ty instanceof SystemFType.Arrow arrow) {
      var a1 = new TypeVar();
      var a2 = new TypeVar();
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

      return new InstResult(
          instLRes.ctx(),
          new InferenceTree(
              "InstLArr",
              input,
              "" + instLRes.ctx(),
              List.of(instRRes.tree(), instLRes.tree())));
    } else if (ty instanceof SystemFType.ForAll forall) {
      var newCtx = ctx.copy();
      newCtx.push(new Entry.TVarBnd(forall.boundVar));
      var instLRes = this.instL(newCtx, a, forall.body);
      var breakRes = instLRes.ctx().break3(
          entry -> entry instanceof Entry.TVarBnd bnd &&
              bnd.tyVar().equals(forall.boundVar));
      var finalCtx = new Context(breakRes.left(), instLRes.ctx());
      return new InstResult(
          finalCtx,
          new InferenceTree(
              "InstLAllR",
              input,
              "" + finalCtx,
              List.of(instLRes.tree())));
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
      return new InstResult(
          newCtx,
          new InferenceTree("InstLSolve", input, "" + newCtx, List.of()));
    } else {
      throw new TypingException.InstantiationError(
          "InstL Instantiation error " + ctx + " |- " + ty);
    }
  }

  InstResult instR(Context ctx, SystemFType ty, TypeVar a) {
    var input = ctx + " |- " + ty + " :=< ^" + a;
    if (ty instanceof SystemFType.EtVar etvar) {
      var breakRes = ctx.break3(
          entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(etvar.tyVar));
      var newCtx = Context.fromParts(
          breakRes.left(),
          new Entry.SETVarBnd(etvar.tyVar, new SystemFType.EtVar(a)),
          breakRes.right(), ctx);

      return new InstResult(
          newCtx,
          new InferenceTree("InstRReach", input, "" + newCtx, List.of()));
    } else if (ty instanceof SystemFType.Lit lit) {
      try (var scope = TypeVar.addScope()) {
        var litType = new SystemFType.Lit(lit.ident,
            lit.parameters.stream().map(p -> (SystemFType) new SystemFType.EtVar(new TypeVar())).toList());

        List<TypeVar> existentials = scope.createdVars();

        var breakRes = ctx.break3(entry -> entry instanceof Entry.ETVarBnd bnd && bnd.tyVar().equals(a));

        Context newCtx = new Context(breakRes.left(), ctx);
        newCtx.push(new Entry.SETVarBnd(a, litType));
        for (var ext : existentials) {
          newCtx.push(new Entry.ETVarBnd(ext));
        }
        newCtx.extend(breakRes.right());

        ArrayList<InferenceTree> trees = new ArrayList<>();

        for (int i = 0; i < existentials.size(); i++) {
          var ext = existentials.get(i);
          var param = lit.parameters.get(i);
          var paramApplied = newCtx.apply(param);
          var instRRes = this.instR(newCtx, paramApplied, ext);
          newCtx = instRRes.ctx();
          trees.add(instRRes.tree());
        }

        return new InstResult(newCtx, new InferenceTree("InstRLit", input, "" + newCtx, List.copyOf(trees)));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

    } else if (ty instanceof SystemFType.Arrow arrow) {
      var a1 = new TypeVar();
      var a2 = new TypeVar();
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

      return new InstResult(
          instRRes.ctx(),
          new InferenceTree(
              "InstRArr",
              input,
              "" + instLRes.ctx(),
              List.of(instRRes.tree(), instLRes.tree())));
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
      return new InstResult(
          finalCtx,
          new InferenceTree(
              "InstRAllL",
              input,
              "" + instRRes.ctx(),
              List.of(instRRes.tree())));
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
      return new InstResult(
          newCtx,
          new InferenceTree("InstRSolve", input, "" + ctx, List.of()));
    } else {
      throw new TypingException.InstantiationError(
          "InstR Instantiation error " + ctx + " |- " + ty);
    }
  }

}

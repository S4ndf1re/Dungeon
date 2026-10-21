package dgir.core.ir.types.builtin.systemf.expr;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dgir.core.debug.Location;
import dgir.core.ir.types.InstEnv;
import dgir.core.ir.types.Symbol;
import dgir.core.ir.types.builtin.systemf.CheckResult;
import dgir.core.ir.types.builtin.systemf.Context;
import dgir.core.ir.types.builtin.systemf.Expr;
import dgir.core.ir.types.builtin.systemf.SystemFType;
import dgir.core.ir.types.builtin.systemf.TypeInference;
import dgir.core.ir.types.builtin.systemf.TypeResult;

public final class Custom<D> extends Expr {

    @FunctionalInterface
    public interface InferFunction<D> {
      TypeResult infer(TypeInference engine, Context ctx, D data);
    }

    @FunctionalInterface
    public interface CheckFunction<D> {
      CheckResult check(TypeInference engine, Context ctx, SystemFType ty, D data);
    }

    @FunctionalInterface
    public interface InstantiateFunction<D> {
      Expr instantiate(Custom<D> toInstantiate, TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
          Context solution, D data);
    }

    @FunctionalInterface
    public interface GetChildrenFunction<D> {
      List<Expr> getChildren(D data);
    }

    private D data;
    private InferFunction<D> inferFn;
    private Optional<CheckFunction<D>> checkFn;
    private Optional<InstantiateFunction<D>> instFn;
    private Optional<GetChildrenFunction<D>> getChildrenFn;

    public Custom(D data, InferFunction<D> inferFn) {
      this(data, inferFn, null, null, null);
    }

    public Custom(Location loc, D data, InferFunction<D> inferFn) {
      this(loc, data, inferFn, null);
    }

    public Custom(D data, InferFunction<D> inferFn, CheckFunction<D> checkFn) {
      this(data, inferFn, checkFn, null, null);
    }

    public Custom(Location loc, D data, InferFunction<D> inferFn, CheckFunction<D> checkFn) {
      this(loc, data, inferFn, checkFn, null, null);
    }

    public Custom(D data, InferFunction<D> inferFn, CheckFunction<D> checkFn,
        InstantiateFunction<D> instFn, GetChildrenFunction<D> getChildrenFn) {
      this.data = data;
      this.inferFn = inferFn;
      this.checkFn = Optional.ofNullable(checkFn);
      this.instFn = Optional.ofNullable(instFn);
      this.getChildrenFn = Optional.ofNullable(getChildrenFn);
    }

    public Custom(Location loc, D data, InferFunction<D> inferFn, CheckFunction<D> checkFn,
        InstantiateFunction<D> instFn, GetChildrenFunction<D> getChildrenFn) {
      super(loc);
      this.data = data;
      this.inferFn = inferFn;
      this.checkFn = Optional.ofNullable(checkFn);
      this.instFn = Optional.ofNullable(instFn);
      this.getChildrenFn = Optional.ofNullable(getChildrenFn);
    }

    public Custom(Custom<D> other) {
      super(other);
      this.data = other.data;
      this.inferFn = other.inferFn;
      this.checkFn = other.checkFn;
      this.instFn = other.instFn;
      this.getChildrenFn = other.getChildrenFn;
    }

    public Custom(Custom<D> other, D newData) {
      super(other);
      this.data = newData;
      this.inferFn = other.inferFn;
      this.checkFn = other.checkFn;
      this.instFn = other.instFn;
      this.getChildrenFn = other.getChildrenFn;
    }

    public D getData() {
      return this.data;
    }

    @Override
    public TypeResult infer(TypeInference engine, Context ctx) {
      return this.inferFn.infer(engine, ctx, data);
    }

    @Override
    public CheckResult check(TypeInference engine, Context ctx, SystemFType ty) {
      if (this.checkFn.isPresent()) {
        return this.checkFn.get().check(engine, ctx, ty, data);
      } else {
        return super.check(engine, ctx, ty);
      }
    }

    @Override
    public void replaceSymbol(Symbol<Expr, SystemFType> original, Symbol<Expr, SystemFType> replacement) {
      // Replacement mutates the child expressions in place, so the immutable
      // data record keeps referencing the (now updated) instances.
      for (var child : this.getChildren()) {
        child.replaceSymbol(original, replacement);
      }
    }

    @Override
    public List<Expr> getChildren() {
      if (this.getChildrenFn.isPresent()) {
        return this.getChildrenFn.get().getChildren(this.data);
      }

      return List.of();
    }

    @Override
    public boolean containsSymbol(Symbol<Expr, SystemFType> symbol) {
      return false;
    }

    @Override
    public Expr instantiateInner(TypeInference engine, InstEnv<Expr, SystemFType, Context> env,
        Context solution) {
      if (this.instFn.isPresent()) {
        // The rebuilt node (created via the copy constructor) inherits the
        // scope link and the operation reconstruction callback of the original!
        return this.instFn.get().instantiate(this, engine, env, solution, this.data);
      }
      return this;
    }

    @Override
    public Expr copy() {
      return new Custom<>(this);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.data, this.inferFn, this.instFn, this.getChildrenFn, super.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Custom<?> other &&
          this.data.equals(other.data) &&
          this.inferFn.equals(other.inferFn) &&
          this.instFn != null && this.instFn.equals(other.instFn) &&
          this.getChildrenFn != null && this.getChildrenFn.equals(other.getChildrenFn)
          && super.equals(obj);
    }

  }

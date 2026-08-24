package dgir.core.ir.types.systemf;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dgir.core.ir.types.Type;
import dgir.core.ir.types.TypeIdent;
import dgir.core.ir.types.TypeVar;

public abstract sealed class SystemFType extends Type {

    public abstract boolean isMono();

    public abstract Set<TypeVar> freeVariables();

    public boolean occursCheck(TypeVar varName) {
      return this.freeVariables().contains(varName);
    }

    public abstract SystemFType substType(
        TypeVar tyVar,
        SystemFType replacement);

    public static final class Var extends SystemFType {

      public final TypeVar tyVar;

      public Var(TypeVar name) {
        this.tyVar = name;
      }

      @Override
      public String toString() {
        return tyVar.toString();
      }

      @Override
      public boolean equals(Object obj) {
        return obj instanceof Var other && this.tyVar.equals(other.tyVar);
      }

      @Override
      public int hashCode() {
        return super.hashCode();
      }

      @Override
      public boolean isMono() {
        return true;
      }

      @Override
      public Set<TypeVar> freeVariables() {
        return Set.of(this.tyVar);
      }

      @Override
      public SystemFType substType(TypeVar tyVar, SystemFType replacement) {
        if (this.tyVar.equals(tyVar)) {
          return replacement;
        } else {
          return this;
        }
      }
    }

    public static final class EtVar extends SystemFType {

      public final TypeVar tyVar;

      public EtVar(TypeVar tyVar) {
        this.tyVar = tyVar;
      }

      @Override
      public String toString() {
        return tyVar.toString();
      }

      @Override
      public boolean equals(Object obj) {
        return obj instanceof EtVar other && this.tyVar.equals(other.tyVar);
      }

      @Override
      public int hashCode() {
        return super.hashCode();
      }

      @Override
      public boolean isMono() {
        return true;
      }

      @Override
      public Set<TypeVar> freeVariables() {
        return Set.of(this.tyVar);
      }

      @Override
      public SystemFType substType(TypeVar tyVar, SystemFType replacement) {
        if (this.tyVar.equals(tyVar)) {
          return replacement;
        } else {
          return this;
        }
      }
    }

    public static final class Arrow extends SystemFType {

      public final SystemFType from;
      public final SystemFType to;

      public Arrow(SystemFType from, SystemFType to) {
        this.from = from;
        this.to = to;
      }

      @Override
      public String toString() {
        return from + " -> " + to;
      }

      @Override
      public boolean equals(Object obj) {
        return (obj instanceof Arrow other &&
            this.from.equals(other.from) &&
            this.to.equals(other.to));
      }

      @Override
      public int hashCode() {
        return super.hashCode();
      }

      @Override
      public boolean isMono() {
        return this.from.isMono() && this.to.isMono();
      }

      @Override
      public Set<TypeVar> freeVariables() {
        var fromVars = this.from.freeVariables();
        var toVars = this.to.freeVariables();
        var set = new HashSet<TypeVar>();
        set.addAll(fromVars);
        set.addAll(toVars);
        return Set.copyOf(set);
      }

      @Override
      public SystemFType substType(TypeVar tyVar, SystemFType replacement) {
        return new SystemFType.Arrow(
            this.from.substType(tyVar, replacement),
            this.to.substType(tyVar, replacement));
      }
    }

    public static final class ForAll extends SystemFType {

      public final TypeVar boundVar;
      public final SystemFType body;

      public ForAll(TypeVar name, SystemFType type) {
        this.boundVar = name;
        this.body = type;
      }

      @Override
      public String toString() {
        return "∀" + boundVar + ". " + body;
      }

      @Override
      public boolean equals(Object obj) {
        return (obj instanceof ForAll other &&
            this.boundVar.equals(other.boundVar) &&
            this.body.equals(other.body));
      }

      @Override
      public int hashCode() {
        return super.hashCode();
      }

      @Override
      public boolean isMono() {
        return false;
      }

      @Override
      public Set<TypeVar> freeVariables() {
        var set = new HashSet<TypeVar>();
        set.addAll(this.body.freeVariables());
        set.remove(this.boundVar);
        return Set.copyOf(set);
      }

      @Override
      public SystemFType substType(TypeVar tyVar, SystemFType replacement) {
        if (this.boundVar.equals(tyVar)) {
          // The type variable is shadowed by the ForAll binder
          return this;
        } else {
          return new SystemFType.ForAll(
              this.boundVar,
              this.body.substType(tyVar, replacement));
        }
      }
    }

    public static final class Lit extends SystemFType {
      public final TypeIdent ident;
      public final List<SystemFType> parameters;

      public Lit(TypeIdent ident, List<SystemFType> params) {
        this.ident = ident;
        this.parameters = params;
      }

      public Lit(TypeIdent ident) {
        this.ident = ident;
        this.parameters = List.of();
      }

      @Override
      public String toString() {
        return this.ident + (this.parameters.isEmpty() ? ""
            : "<" + this.parameters.stream().map(Object::toString).collect(Collectors.joining(", ")) + ">");
      }

      @Override
      public boolean equals(Object obj) {
        return obj instanceof Lit other && other.ident.equals(this.ident) && other.parameters.equals(this.parameters);
      }

      @Override
      public int hashCode() {
        return super.hashCode();
      }

      @Override
      public boolean isMono() {
        return true;
      }

      @Override
      public Set<TypeVar> freeVariables() {
        HashSet<TypeVar> freeVars = new HashSet<>();

        for (var param : parameters) {
          freeVars.addAll(param.freeVariables());
        }

        return Set.copyOf(freeVars);
      }

      @Override
      public SystemFType substType(TypeVar tyVar, SystemFType replacement) {
        return new Lit(this.ident, this.parameters.stream().map(param -> param.substType(tyVar, replacement)).toList());
      }
    }

    public static final class NumericType extends SystemFType {
      public long size;

      public NumericType(long size) {
        this.size = size;
      }

      @Override
      public boolean isMono() {
        return true;
      }

      @Override
      public Set<TypeVar> freeVariables() {
        return Set.of();
      }

      @Override
      public SystemFType substType(TypeVar tyVar, SystemFType replacement) {
        return new NumericType(this.size);
      }
    }

  }

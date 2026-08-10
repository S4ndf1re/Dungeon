package dgir.core.ir.types;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import dgir.core.ir.Value;

public class GeneralFunctionType {

  public final String name;
  public final List<Pair<Symbol, Optional<Type>>> parameters;
  public final Optional<Pair<Value, Optional<Type>>> returnValue;
  public final GeneralBlock body;

  public GeneralFunctionType(String name, List<Pair<Symbol, Optional<Type>>> parameters,
      GeneralBlock body,
      Optional<Pair<Value, Optional<Type>>> returnValue) {
    this.name = name;
    this.body = body;
    this.parameters = parameters;
    this.returnValue = returnValue;
  }

}

package dgir.core.ir.types;

import java.util.ArrayList;
import java.util.List;

import dgir.core.debug.Location;
import dgir.core.ir.Region;
import dgir.core.ir.Value;

public class OperationExprConversionUtils {

  public static <E extends Expression<E, T>, T extends Type<T>, EngineT extends TypeInferenceSolver<EngineT, E, T>> E regionToExpr(
      EngineT engine, Region region) {
    GeneralBlock generalBlock = GeneralBlock.fromBlock(region.getBlocks().getFirst());

    return engine.generalBlockToInferenceExpr(generalBlock);
  }

  public static <E extends Expression<E, T>, T extends Type<T>, EngineT extends TypeInferenceSolver<EngineT, E, T>> E buildIdentityApplication(
      EngineT engine, E arg, Location loc) {
    var param = Symbol.<E, T>of(new Value());
    return engine.App(loc, engine.Abs(loc, List.of(param), engine.Var(loc, param)), List.of(arg));
  }

  public static <E extends Expression<E, T>, T extends Type<T>, EngineT extends TypeInferenceSolver<EngineT, E, T>> E buildAnnotatedApplication(
      EngineT engine, List<E> args, List<Symbol<E, T>> params, E body, T type, Location loc) {

    var abs = engine.Abs(loc, params, body);
    var ann = engine.Ann(loc, abs, type);
    var app = engine.App(loc, ann, args);

    return app;
  }

  public static <E extends Expression<E, T>, T extends Type<T>, EngineT extends TypeInferenceSolver<EngineT, E, T>> E buildAnnotatedApplication(
      EngineT engine, List<E> args, E body, T type, Location loc) {
    List<Symbol<E, T>> params = new ArrayList<>();
    for (int i = 0; i < args.size(); i++) {
      params.add(Symbol.of(new Value()));
    }
    var abs = engine.Abs(loc, params, body);
    var ann = engine.Ann(loc, abs, type);
    var app = engine.App(loc, ann, args);

    return app;
  }
}

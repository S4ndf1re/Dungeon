import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dgir.core.ir.Op;
import dgir.core.ir.types.TypeDialect;
import dgir.core.ir.types.builtin.algorithmw.AlgorithmWInference;
import dgir.core.ir.types.builtin.hmx.HMXInference;
import dgir.core.ir.types.builtin.systemf.SystemFInference;
import dgir.core.ir.types.compatibility.ConverterRegistry;
import dgir.core.ir.types.compatibility.ConverterRegistry.TypeDialectConverterRegistry;
import dgir.dialect.arith.ArithAlgoWConversion;
import dgir.dialect.arith.ArithHMXConversion;
import dgir.dialect.arith.ArithOps;
import dgir.dialect.arith.ArithSystemFConversion;
import dgir.dialect.builtin.BuiltinAlgoWConversion;
import dgir.dialect.builtin.BuiltinHMXConversion;
import dgir.dialect.builtin.BuiltinOps;
import dgir.dialect.builtin.BuiltinSystemFConversion;
import dgir.dialect.cell.CellAlgoWConversion;
import dgir.dialect.cell.CellHMXConversion;
import dgir.dialect.cell.CellOps;
import dgir.dialect.cell.CellSystemFConversion;
import dgir.dialect.cf.CfAlgoWConversion;
import dgir.dialect.cf.CfHMXConversion;
import dgir.dialect.cf.CfOps;
import dgir.dialect.cf.CfSystemFConversion;
import dgir.dialect.func.FuncAlgoWConversion;
import dgir.dialect.func.FuncHMXConversion;
import dgir.dialect.func.FuncOps;
import dgir.dialect.func.FuncSystemFConversion;
import dgir.dialect.io.IoAlgoWConversion;
import dgir.dialect.io.IoHMXConversion;
import dgir.dialect.io.IoOps;
import dgir.dialect.io.IoSystemFConversion;
import dgir.dialect.mem.MemAlgoWConversion;
import dgir.dialect.mem.MemHMXConversion;
import dgir.dialect.mem.MemOps;
import dgir.dialect.mem.MemSystemFConversion;
import dgir.dialect.scf.ScfAlgoWConversion;
import dgir.dialect.scf.ScfHMXConversion;
import dgir.dialect.scf.ScfOps;
import dgir.dialect.scf.ScfSystemFConversion;
import dgir.dialect.str.StringAlgoWConversion;
import dgir.dialect.str.StringHMXConversion;
import dgir.dialect.str.StringSystemFConversion;
import dgir.dialect.str.StrOps;

/**
 * Guards against op↔converter drift: every concrete op defined in a dialect's
 * {@code *Ops} container must be registered in the converter registry for each
 * engine, unless it is explicitly allowlisted here as intentionally unhandled.
 *
 * A new op added to an {@code *Ops} class without a converter registration
 * fails this test loudly instead of failing later (or silently) at inference.
 */
public class ConverterCompletenessTest {

  /** intentionally unhandled ops: {op class -> reason}. */
  private static final Map<Class<? extends Op>, String> ALLOWLIST = buildAllowlist();

  private static Map<Class<? extends Op>, String> buildAllowlist() {
    Map<Class<? extends Op>, String> allow = new HashMap<>();

    // mem: ops with no expression lowering in AlgoW/HMX (no subtyping for
    // realloc casts / sizes); they are typed directly from IR results.
    allow.put(MemOps.ReallocGcOp.class, "no expr lowering yet (AlgoW/HMX)");
    allow.put(MemOps.CastOp.class, "no expr lowering yet (AlgoW/HMX)");
    allow.put(MemOps.SizeofOp.class, "no expr lowering yet (AlgoW/HMX)");

    return allow;
  }

  private record Engine(String name, Class<? extends TypeDialect<?, ?, ?>> dialectClass,
      Runnable register) {}

  private static final List<Engine> ENGINES = List.of(
      new Engine("AlgoW", AlgorithmWInference.class, () -> {
        FuncAlgoWConversion.registerBuiltinAlgoWConversion();
        BuiltinAlgoWConversion.registerBuiltinAlgoWConversion();
        ArithAlgoWConversion.registerBuiltinAlgoWConversion();
        CfAlgoWConversion.registerBuiltinAlgoWConversion();
        CellAlgoWConversion.registerBuiltinAlgoWConversion();
        IoAlgoWConversion.registerBuiltinAlgoWConversion();
        MemAlgoWConversion.registerBuiltinAlgoWConversion();
        ScfAlgoWConversion.registerBuiltinAlgoWConversion();
        StringAlgoWConversion.registerBuiltinAlgoWConversion();
      }),
      new Engine("HMX", HMXInference.class, () -> {
        FuncHMXConversion.registerBuiltinHMXConversion();
        BuiltinHMXConversion.registerBuiltinHMXConversion();
        ArithHMXConversion.registerBuiltinHMXConversion();
        CfHMXConversion.registerBuiltinHMXConversion();
        CellHMXConversion.registerBuiltinHMXConversion();
        IoHMXConversion.registerBuiltinHMXConversion();
        MemHMXConversion.registerBuiltinHMXConversion();
        ScfHMXConversion.registerBuiltinHMXConversion();
        StringHMXConversion.registerBuiltinHMXConversion();
      }),
      new Engine("SystemF", SystemFInference.class, () -> {
        FuncSystemFConversion.registerBuiltinSystemFConversion();
        BuiltinSystemFConversion.registerBuiltinSystemFConversion();
        ArithSystemFConversion.registerBuiltinSystemFConversion();
        CfSystemFConversion.registerBuiltinSystemFConversion();
        StringSystemFConversion.registerBuiltinSystemFConversion();
        MemSystemFConversion.registerBuiltinSystemFConversion();
        CellSystemFConversion.registerBuiltinSystemFConversion();
        IoSystemFConversion.registerBuiltinSystemFConversion();
        ScfSystemFConversion.registerBuiltinSystemFConversion();
      }));

  private static final List<Class<?>> OP_CONTAINERS = List.of(
      ArithOps.class, BuiltinOps.class, CellOps.class, CfOps.class,
      FuncOps.class, IoOps.class, MemOps.class, ScfOps.class, StrOps.class);

  @BeforeEach
  public void setup() {
    ENGINES.forEach(e -> ConverterRegistry.registerDialect(e.dialectClass()));
    ENGINES.forEach(e -> e.register().run());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static TypeDialectConverterRegistry registryFor(
      Class<? extends TypeDialect<?, ?, ?>> dialectClass) {
    return (TypeDialectConverterRegistry) ConverterRegistry.getConverterForDialect((Class) dialectClass)
        .orElseThrow();
  }

  @Test
  public void everyConcreteOpIsRegisteredPerEngineOrAllowlisted() {
    for (Engine engine : ENGINES) {
      TypeDialectConverterRegistry registry = registryFor(engine.dialectClass());
      Set<Class<? extends Op>> registered = registry.registeredOps();

      for (Class<?> container : OP_CONTAINERS) {
        for (Class<? extends Op> op : concreteOps(container)) {
          if (registered.contains(op)) {
            var reason = ALLOWLIST.get(op);
            assertTrue(
                reason == null || !(reason.startsWith("SystemF converter") && engine.name().equals("SystemF")),
                op.getName() + " is registered for " + engine.name()
                    + " but still allowlisted as unimplemented — remove the allowlist entry.");
            continue;
          }
          assertTrue(ALLOWLIST.containsKey(op),
              op.getName() + " has no converter registered for " + engine.name()
                  + " and is not in the completeness allowlist. Either register a converter"
                  + " or add an explicit allowlist entry with a reason.");
        }
      }
    }
  }

  @Test
  public void registryContainsNoUnknownOps() {
    Set<Class<? extends Op>> allOps = new HashSet<>();
    for (Class<?> container : OP_CONTAINERS) {
      allOps.addAll(concreteOps(container));
    }
    for (Engine engine : ENGINES) {
      TypeDialectConverterRegistry registry = registryFor(engine.dialectClass());
      var unknown = new HashSet<>(registry.registeredOps());
      unknown.removeAll(allOps);
      assertEquals(Set.of(), unknown,
          engine.name() + " registers converters for ops that do not exist in any *Ops container.");
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends Op> Set<Class<? extends Op>> concreteOps(Class<?> container) {
    Set<Class<? extends Op>> result = new HashSet<>();
    for (Class<?> nested : container.getDeclaredClasses()) {
      if (!Op.class.isAssignableFrom(nested) || Modifier.isAbstract(nested.getModifiers()))
        continue;
      result.add((Class<? extends Op>) nested);
    }
    return result;
  }
}

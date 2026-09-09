import static org.junit.jupiter.api.Assertions.assertEquals;
import static dgir.dialect.builtin.BuiltinOps.ProgramOp;
import static dgir.dialect.func.FuncOps.FuncOp;
import static java.nio.charset.StandardCharsets.UTF_8;
import dgir.core.analysis.DotExpression;
import dgir.core.analysis.DotType;

import dgir.core.analysis.DotCFG;
import dgir.core.analysis.OperationVerifier.VerifyOptions;
import dgir.core.utility.DgirCoreUtils;
import dgir.core.ir.Op;
import dgir.core.debug.Location;
import dgir.core.ir.Operation;
import dgir.core.serialization.Utils;
import dgir.core.ir.types.Expression;
import dgir.core.ir.types.Type;
import guru.nidi.graphviz.engine.Engine;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.Pair;
import tools.jackson.databind.ObjectMapper;

public class DgirTestUtils {
  public static ObjectMapper mapper = Utils.getMapper(true);
  public static boolean printResult = true;
  public static boolean saveResult = true;
  public static boolean printCfg = true;
  public static boolean saveCfg = true;
  public static boolean saveCfgImage = true;
  // The file path for saved files (cfg and image)
  public static String savePath = "test_results/";

  private static final Pattern UUID_PATTERN = Pattern.compile(
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  public static boolean testValidityAndSerialization(Op op) {
    String result = mapper.writeValueAsString(op);
    if (printResult)
      System.out.println(result);

    assertEquals(
        "",
        DgirTestUtils.compareSerializedOperations(mapper, op.getOperation(), result),
        "Serialization mismatch for op: " + op.getClass().getSimpleName());

    String callerName = DgirCoreUtils.STACK_WALKER.walk(
        stream -> stream
            .skip(1)
            .findFirst()
            .map(
                stackFrame -> stackFrame.getDeclaringClass().getSimpleName()
                    + "."
                    + stackFrame.getMethodName())
            .orElse("unknown"));

    // Ensure the output directory exists before writing files
    try {
      Files.createDirectories(Paths.get(savePath));
    } catch (IOException e) {
      System.out.println("Failed to create output directory '" + savePath + "': " + e);
    }

    if (saveResult) {
      String filePath = savePath + callerName + ".json";
      try {
        BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath), UTF_8);
        writer.write(result);
        writer.close();
        System.out.println("Saved result to " + filePath);
      } catch (IOException e) {
        System.out.println("Failed to save result to " + filePath + ": " + e.getMessage());
      }
    }

    // Check that this is a valid op, otherwise we can't generate a cfg
    if (!op.verify( VerifyOptions.FULL_VERIFICATION)) {
      System.out.println(
          "Skipping cfg generation for invalid op: "
              + op.getClass().getSimpleName()
              + " for test "
              + callerName);
      return false;
    }

    if (printCfg || saveCfg || saveCfgImage) {
      DotCFG.Cluster cfg = DotCFG.buildCfgCluster(op.getOperation());

      // Print the cfg to console
      if (printCfg)
        System.out.println(cfg);

      // Save the cfg to a file with the caller name as the file name
      if (saveCfg) {
        String filePath = savePath + callerName + ".dot";
        try {
          BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath), UTF_8);
          writer.write(cfg.toString());
          writer.close();
          System.out.println("Saved CFG to " + filePath);
        } catch (IOException e) {
          System.out.println("Failed to save CFG to " + filePath + ": " + e.getMessage());
        }
      }

      // Generate an image of the cfg and save it to a file with the caller name as
      // the file name
      if (saveCfgImage) {
        String filePath = savePath + callerName + ".png";
        try {
          Graphviz.fromString(cfg.toString())
              .engine(Engine.DOT)
              .render(Format.PNG)
              .toFile(new File(filePath));
        } catch (IOException e) {
          System.out.println("Failed to save CFG image to " + filePath + ": " + e.getMessage());
        }
      }
    }
    return true;
  }

  /**
   * Save a DOT rendering of the given expression tree (with inferred types),
   * following only instantiable children so stale pre-inference subtrees are
   * not drawn. Also renders a PNG image. The file name is derived from the
   * calling test method via the stack walker and suffixed with {@code .expr}.
   */
  public static <E extends Expression<E, T>, T extends Type> void saveDotExpr(E expr) {
    saveDotAndPng(".expr", DotExpression.toDot(expr, DotExpression.VisitGetChildrenOption.ONLY_INSTANTIATED));
  }

  /**
   * Save a DOT rendering of the given type tree alongside its rendered PNG
   * image. The file name is derived from the calling test method via the stack
   * walker and suffixed with {@code .type}.
   */
  public static void saveDotType(Type type) {
    saveDotAndPng(".type", DotType.toDot(type));
  }


  /**
   * Save the given dot string and its rendered PNG image in
   * {@link #savePath}, named after the calling test method ({@code
   * <TestClass>.<testMethod><suffix>.dot/.png}). Helper frames (e.g. shared
   * {@code solve} methods) are skipped so the file name always refers to the
   * actual unit test.
   */
  public static void saveDotAndPng(String suffix, String dot) {
    Set<String> helperMethods = Set.of(
        "setup", "solve", "solveAlgoW", "solveSystemF", "assertParity", "countOps");
    String callerName = DgirCoreUtils.STACK_WALKER.walk(
        stream -> stream
            .skip(1)
            .filter(
                frame -> frame.getDeclaringClass().getSimpleName().endsWith("Test")
                    && !helperMethods.contains(frame.getMethodName()))
            .findFirst()
            .map(
                stackFrame -> stackFrame.getDeclaringClass().getSimpleName()
                    + "."
                    + stackFrame.getMethodName())
            .orElse("unknown"));

    // Ensure the output directory exists before writing files
    try {
      Files.createDirectories(Paths.get(savePath));
    } catch (IOException e) {
      System.out.println("Failed to create output directory '" + savePath + "': " + e);
    }
    Path dotFile = Paths.get(savePath + callerName + suffix + ".dot");
    try (BufferedWriter writer = Files.newBufferedWriter(dotFile, StandardCharsets.UTF_8)) {
      writer.write(dot);
      System.out.println("Saved dot to " + dotFile);
    } catch (IOException e) {
      System.out.println("Failed to save dot to " + dotFile + ": " + e.getMessage());
    }

    Path pngFile = Paths.get(savePath + callerName + suffix + ".png");
    try {
      Graphviz.fromString(dot).engine(Engine.DOT).render(Format.PNG).toFile(pngFile.toFile());
      System.out.println("Saved image to " + pngFile);
    } catch (IOException e) {
      System.out.println("Failed to save image to " + pngFile + ": " + e.getMessage());
    }
  }

  public static String compareSerializedOperations(
      ObjectMapper mapper, Operation op1, String op2Json) {
    return compareSerializedOperations(mapper, op1, mapper.readValue(op2Json, Operation.class));
  }

  /**
   * Save the DOT CFG of the given operation hierarchy alongside its rendered
   * PNG image, using the given file name suffix. Does nothing if {@code
   * rootOp} is {@code null}.
   */
  public static void saveDotCfg(String suffix, Operation rootOp) {
    if (rootOp == null) {
      System.out.println("Skipping cfg export for '" + suffix + "': no root operation found");
      return;
    }
    saveDotAndPng(suffix, DotCFG.buildCfgCluster(rootOp).toString());
  }

  /**
   * Find the root of a reconstructed operation tree: prefer the rebuilt
   * program op, otherwise the first operation without a parent.
   *
   * @return the root operation, or {@code null} if none could be determined.
   */
  public static Operation findRebuiltRoot(List<Operation> ops) {
    return ops.stream()
        .filter(op -> op.asOp() instanceof ProgramOp)
        .findFirst()
        .orElseGet(() -> ops.stream()
            .filter(op -> op.getParentOperation().isEmpty())
            .findFirst()
            .orElse(null));
  }

  /**
   * Export the operation-side artifacts of a type inference run: the original
   * operation tree that preceded the inference ({@code .input.cfg}) and the
   * reconstructed operation tree that postcedes it ({@code .output.cfg}).
   *
   * @param stagePrefix prefix for the file name suffixes (e.g. {@code
   *                    "algoW"}), may be empty.
   */
  public static <E extends Expression<E, T>, T extends Type> void saveInferenceCfg(
      String stagePrefix, Operation inputOp, E solvedExpr) {
    saveDotCfg(stagePrefix + ".input.cfg", inputOp);

    List<Operation> ops = new ArrayList<>();
    new Expression.ExpressionVisitor<E, T>(
        Expression.ExpressionVisitor.VisitOrder.POST_ORDER,
        Expression.ExpressionVisitor.VisitGetChildrenOption.ALL_CHILDREN)
        .visit(solvedExpr, e -> e.getUnderlyingOperation().ifPresent(ops::add));
    saveDotCfg(stagePrefix + ".output.cfg", findRebuiltRoot(ops));
  }

  public static String compareSerializedOperations(
      ObjectMapper mapper, Operation op1, Operation op2) {
    try {
      String json1 = mapper.writeValueAsString(op1);
      String json2 = mapper.writeValueAsString(op2);

      String normalizedJson1 = normalizeJson(json1);
      String normalizedJson2 = normalizeJson(json2);

      if (normalizedJson1.equals(normalizedJson2)) {
        return "";
      }
      return diffStrings(normalizedJson1, normalizedJson2);
    } catch (Exception e) {
      return "Error during serialization comparison: " + e.getMessage();
    }
  }

  private static String normalizeJson(String json) {
    AtomicInteger uuidCounter = new AtomicInteger();
    Map<UUID, String> uuidMap = new HashMap<>();

    Matcher matcher = UUID_PATTERN.matcher(json);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      UUID uuid = UUID.fromString(matcher.group());
      String replacement = uuidMap.computeIfAbsent(uuid, u -> "UUID_" + uuidCounter.getAndIncrement());
      matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  /**
   * Create a new string based on the initial string. The string is printed in
   * whole and line by
   * line the difference is printed next to the original line if there is a
   * difference. The
   * difference is separated by a " | " symbol.
   *
   * @param base     The original string
   * @param modified The modified string
   * @return the diff string
   */
  public static String diffStrings(String base, String modified) {
    base = base.replaceAll("\r", "");
    modified = modified.replaceAll("\r", "");
    StringBuilder diff = new StringBuilder();
    String[] baseLines = base.split("\n", -1);
    String[] modifiedLines = modified.split("\n", -1);
    int maxLines = Math.max(baseLines.length, modifiedLines.length);
    int maxBaseLength = 0;
    for (String line : baseLines) {
      maxBaseLength = Math.max(maxBaseLength, line.length());
    }
    for (int i = 0; i < maxLines; i++) {
      String baseLine = i < baseLines.length ? baseLines[i] : "";
      String modifiedLine = i < modifiedLines.length ? modifiedLines[i] : "";
      // Pad the base line to the maximum length of the base lines for better
      // alignment.
      diff.append(String.format("%-" + maxBaseLength + "s", baseLine));
      diff.append(" | ");
      if (!baseLine.trim().equals(modifiedLine.trim())) {
        diff.append("\u001B[33m").append(modifiedLine).append("\u001B[0m");
      } else {
        diff.append("\u001B[32m").append(modifiedLine).append("\u001B[0m");
      }
      diff.append("\n");
    }
    return diff.toString();
  }

  /**
   * Create a new ProgramOp with a func.func op inside with the symbol_name "main"
   *
   * @return a pair of the created ProgramOp and the block contained in the
   *         func.func op
   */
  public static Pair<ProgramOp, FuncOp> createProgramOpWithEntryFunc() {
    ProgramOp programOp = new ProgramOp(Location.UNKNOWN);
    FuncOp funcOp = programOp.addOperation(new FuncOp(Location.UNKNOWN, "main"));
    return Pair.of(programOp, funcOp);
  }
}

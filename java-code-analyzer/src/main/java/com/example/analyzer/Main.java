package com.example.analyzer;

import com.example.analyzer.model.InjectionEdge;
import com.example.analyzer.model.MethodInfo;
import com.example.analyzer.model.ProjectModel;
import com.example.analyzer.model.SpringComponent;
import com.example.analyzer.model.SpringComponentGraph;
import com.example.analyzer.model.TypeInfo;
import com.example.analyzer.persistence.CouchbaseSpringGraphStore;
import com.example.analyzer.persistence.JsonSpringGraphStore;
import com.example.analyzer.persistence.Neo4jSpringGraphStore;
import com.example.analyzer.seq.PlantUmlSvgExporter;
import com.example.analyzer.seq.SequenceDiagramGenerator;
import com.example.analyzer.spring.SpringComponentAnalyzer;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.TreeMap;

/**
 * CLI for querying Java source and exporting cross-reference matrices.
 * <p>
 * Usage:
 *   java -jar java-code-analyzer.jar &lt;project-root&gt;
 *   Then enter commands: packages, types &lt;package&gt;, controllers, services, repositories, entities,
 *   export-csv &lt;dir&gt;, export-html &lt;file&gt;, help, quit
 */
public class Main {

    /** In-memory command history size for ↑ / ↓ recall. */
    private static final int COMMAND_HISTORY_SIZE = 5;

    public static void main(String[] args) throws Exception {
        Path projectRoot = args.length > 0
            ? Path.of(args[0])
            : Path.of(System.getProperty("user.dir"));

        if (!java.nio.file.Files.isDirectory(projectRoot)) {
            System.err.println("Not a directory: " + projectRoot);
            System.err.println("Usage: java -jar java-code-analyzer.jar [<project-root>]");
            return;
        }

        System.out.println("Scanning " + projectRoot + " ...");
        List<Path> javaFiles = ProjectScanner.findJavaFiles(projectRoot);
        System.out.println("Found " + javaFiles.size() + " Java files. Parsing ...");
        ProjectModel model = JavaAnalyzer.analyze(projectRoot, javaFiles);
        SpringComponentGraph springGraph = SpringComponentAnalyzer.analyze(model, javaFiles);
        QueryEngine q = new QueryEngine(model);
        MatrixExporter exporter = new MatrixExporter(model);

        System.out.println("Ready. Commands: packages | types <pkg> | methods <package|class> | controllers | services | ...");
        System.out.println("  spring-beans | spring-wiring | persist-spring-json <file>");
        System.out.println("  persist-spring-neo4j | persist-spring-couchbase");
        System.out.println("  sequence <method> [depth] [--svg <file>] | sequence <FQN> <method> ...");
        System.out.println("  export-csv <dir> | export-html <file> | help | quit");

        LineReader lineReader = tryCreateLineReader();
        Scanner scannerFallback = lineReader == null ? new Scanner(System.in) : null;
        if (lineReader != null) {
            System.out.println(
                "  Line editing: ↑/↓ recall last " + COMMAND_HISTORY_SIZE + " commands; ←/→ edit; Ctrl+D exit."
            );
        }
        try {
            while (true) {
                String raw = readCommandLine(lineReader, scannerFallback);
                if (raw == null) {
                    break;
                }
                String line = raw.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\\s+", 2);
                String cmd = parts[0].toLowerCase();
                String arg = parts.length > 1 ? parts[1].trim() : "";

                switch (cmd) {
                    case "quit":
                    case "exit":
                    case "q":
                        return;
                    case "help":
                        printHelp();
                        break;
                    case "packages":
                        for (String p : q.listPackages()) System.out.println("  " + p);
                        break;
                    case "types":
                        if (arg.isEmpty()) {
                            System.out.println("Usage: types <package-name>");
                            break;
                        }
                        for (TypeInfo t : q.listTypesInPackage(arg)) {
                            System.out.println("  " + t.getSourceLocation() + "  " + t.getKind() + " " + t.getSimpleName() + " " + t.getAnnotations());
                        }
                        break;
                    case "methods":
                        if (arg.isEmpty()) {
                            System.out.println("Usage: methods <package-name> | methods <fully.qualified.ClassName>");
                            break;
                        }
                        java.util.List<MethodInfo> methodList = q.getType(arg) != null
                            ? q.listPublicMethodsInClass(arg)
                            : q.listPublicMethodsInPackage(arg);
                        for (MethodInfo m : methodList) {
                            System.out.println("  " + m.getSourceLocation() + "  " + m.getDeclaringTypeSimpleName() + "." + m.getSignature());
                        }
                        break;
                    case "controllers":
                        printTypesGroupedByPackage(q.listControllers(), q.listRestControllers());
                        break;
                    case "services":
                        printTypesGroupedByPackage(q.listServices());
                        break;
                    case "repositories":
                        printTypesGroupedByPackage(q.listRepositories());
                        break;
                    case "entities":
                        printTypesGroupedByPackage(q.listEntities());
                        break;
                    case "interfaces":
                        printTypesGroupedByPackage(q.listInterfaces());
                        break;
                    case "package-deps":
                    case "pkg-deps":
                        printPackageDependencies(q.getPackageDependencies());
                        break;
                    case "export-csv":
                        if (arg.isEmpty()) {
                            System.out.println("Usage: export-csv <output-directory>");
                            break;
                        }
                        Path dir = Path.of(arg);
                        java.nio.file.Files.createDirectories(dir);
                        exporter.exportPackageTypesCsv(dir.resolve("package-types.csv"));
                        exporter.exportTypeAnnotationCsv(dir.resolve("type-annotation.csv"));
                        exporter.exportPackageStereotypeCsv(dir.resolve("package-stereotype.csv"));
                        exporter.exportControllerServiceCsv(dir.resolve("controller-service.csv"));
                        exporter.exportServiceRepositoryCsv(dir.resolve("service-repository.csv"));
                        System.out.println("CSV files written to " + dir);
                        break;
                    case "export-html":
                        if (arg.isEmpty()) {
                            System.out.println("Usage: export-html <output-file.html>");
                            break;
                        }
                        exporter.exportHtmlReport(Path.of(arg));
                        System.out.println("HTML report written to " + arg);
                        break;
                    case "spring-beans":
                        for (SpringComponent c : springGraph.getComponents()) {
                            System.out.println("  " + c.getStereotypes() + " " + c.getQualifiedName());
                        }
                        System.out.println("  (" + springGraph.getComponents().size() + " beans)");
                        break;
                    case "spring-wiring":
                        for (InjectionEdge e : springGraph.getInjectionEdges()) {
                            String to = e.getToQualifiedName() != null
                                ? e.getToQualifiedName()
                                : ("?" + e.getToTypeSimpleName());
                            System.out.println("  " + e.getFromQualifiedName() + " --" + e.getKind() + "--> " + to
                                + (e.getQualifier() != null ? " @Qualifier(" + e.getQualifier() + ")" : ""));
                        }
                        System.out.println("  (" + springGraph.getInjectionEdges().size() + " edges)");
                        break;
                    case "persist-spring-json":
                        if (arg.isEmpty()) {
                            System.out.println("Usage: persist-spring-json <file.json>");
                            break;
                        }
                        new JsonSpringGraphStore().save(springGraph, Path.of(arg));
                        System.out.println("Spring graph JSON written to " + arg);
                        break;
                    case "persist-spring-neo4j": {
                        String uri = firstNonBlank(System.getenv("NEO4J_URI"), "neo4j://localhost:7687");
                        String user = firstNonBlank(System.getenv("NEO4J_USER"), "neo4j");
                        String pass = System.getenv("NEO4J_PASSWORD");
                        if (pass == null || pass.isBlank()) {
                            System.out.println("Set NEO4J_PASSWORD (and optionally NEO4J_URI, NEO4J_USER, NEO4J_DATABASE).");
                            break;
                        }
                        String db = firstNonBlank(System.getenv("NEO4J_DATABASE"), "neo4j");
                        try (Neo4jSpringGraphStore store = new Neo4jSpringGraphStore(uri, user, pass, db)) {
                            store.upsertGraph(springGraph);
                        }
                        System.out.println("Spring graph upserted into Neo4j database \"" + db + "\".");
                        break;
                    }
                    case "sequence":
                    case "seq-diagram": {
                        if (arg.isBlank()) {
                            printSequenceUsage();
                            break;
                        }
                        SequenceCommandInput sci = parseSequenceCommandInput(model, arg);
                        if (sci == null) {
                            break;
                        }
                        SeqDiagramArgs sd = sci.resolvedArgs();
                        if (sci.pendingClass() != null) {
                            sd = completeSequenceMethodPick(
                                model, sci.pendingClass(), sci.pendingDepth(), sci.pendingSvg(),
                                lineReader, scannerFallback
                            );
                            if (sd == null) {
                                break;
                            }
                        }
                        try {
                            String puml = new SequenceDiagramGenerator(model).generate(
                                sd.classFqn, sd.methodName, sd.maxDepth, sd.entryParameterTypes
                            );
                            Path pumlFile = writeSequencePumlToCurrentDir(sd, puml);
                            System.out.println("PlantUML saved to " + pumlFile.toAbsolutePath());
                            System.out.println(puml);
                            Path svgOut = sd.svgOut != null ? sd.svgOut : defaultSvgOutputPath(pumlFile);
                            PlantUmlSvgExporter.exportSvg(puml, svgOut);
                            System.out.println("SVG written to " + svgOut.toAbsolutePath());
                        } catch (Exception e) {
                            System.err.println(e.getMessage() != null ? e.getMessage() : e.toString());
                        }
                        break;
                    }
                    case "persist-spring-couchbase": {
                        String conn = firstNonBlank(
                            System.getenv("COUCHBASE_CONNECTION_STRING"),
                            "couchbase://127.0.0.1"
                        );
                        String user = firstNonBlank(System.getenv("COUCHBASE_USER"), "Administrator");
                        String pass = System.getenv("COUCHBASE_PASSWORD");
                        String bucket = firstNonBlank(System.getenv("COUCHBASE_BUCKET"), "default");
                        if (pass == null || pass.isBlank()) {
                            System.out.println(
                                "Set COUCHBASE_PASSWORD (and optionally COUCHBASE_CONNECTION_STRING, COUCHBASE_USER, COUCHBASE_BUCKET)."
                            );
                            break;
                        }
                        try (CouchbaseSpringGraphStore store = new CouchbaseSpringGraphStore(conn, user, pass, bucket)) {
                            store.upsertGraph(springGraph);
                        }
                        System.out.println("Spring graph upserted into Couchbase bucket \"" + bucket + "\".");
                        break;
                    }
                    default:
                        System.out.println("Unknown command. Type 'help'.");
                }
            }
        } finally {
            if (scannerFallback != null) {
                scannerFallback.close();
            }
        }
    }

    private static LineReader tryCreateLineReader() {
        try {
            return LineReaderBuilder.builder()
                .terminal(TerminalBuilder.builder().system(true).build())
                .variable(LineReader.HISTORY_SIZE, COMMAND_HISTORY_SIZE)
                .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
                .build();
        } catch (IOException e) {
            System.err.println("Interactive line editing unavailable (" + e.getMessage() + "); using plain input.");
            return null;
        }
    }

    /**
     * @return line text, or {@code null} on EOF; never throws to caller
     */
    private static String readCommandLine(LineReader lineReader, Scanner scannerFallback) {
        if (lineReader != null) {
            try {
                String s = lineReader.readLine("> ");
                return s;
            } catch (UserInterruptException e) {
                System.out.println();
                return "";
            } catch (EndOfFileException e) {
                System.out.println();
                return null;
            }
        }
        System.out.print("> ");
        if (scannerFallback == null || !scannerFallback.hasNextLine()) {
            return null;
        }
        return scannerFallback.nextLine();
    }

    /** @return line, empty string on interrupt, {@code null} on EOF */
    private static String readLineWithPrompt(LineReader lineReader, Scanner scannerFallback, String prompt) {
        if (lineReader != null) {
            try {
                return lineReader.readLine(prompt);
            } catch (UserInterruptException e) {
                System.out.println();
                return "";
            } catch (EndOfFileException e) {
                System.out.println();
                return null;
            }
        }
        System.out.print(prompt);
        if (scannerFallback == null || !scannerFallback.hasNextLine()) {
            return null;
        }
        return scannerFallback.nextLine();
    }

    /** Prints package dependencies: each package (sorted) with the list of packages it uses. */
    private static void printPackageDependencies(java.util.Map<String, java.util.Set<String>> deps) {
        for (var e : deps.entrySet()) {
            String pkg = e.getKey();
            System.out.println("  Package: " + (pkg.isEmpty() ? "(default package)" : pkg));
            java.util.Set<String> used = e.getValue();
            if (used.isEmpty()) {
                System.out.println("    (none)");
            } else {
                for (String u : used) {
                    System.out.println("    " + (u.isEmpty() ? "(default package)" : u));
                }
            }
        }
    }

    /** Groups types by package (sorted by package name), prints "Package: <name>" then each type. */
    private static void printTypesGroupedByPackage(List<TypeInfo>... lists) {
        TreeMap<String, List<TypeInfo>> byPackage = new TreeMap<>();
        for (List<TypeInfo> list : lists) {
            for (TypeInfo t : list) {
                String pkg = t.getPackageName() != null ? t.getPackageName() : "";
                byPackage.computeIfAbsent(pkg, k -> new ArrayList<>()).add(t);
            }
        }
        for (var e : byPackage.entrySet()) {
            String pkg = e.getKey();
            System.out.println("  Package: " + (pkg.isEmpty() ? "(default package)" : pkg));
            for (TypeInfo t : e.getValue()) {
                System.out.println("    " + t.getSourceLocation() + "  " + t.getQualifiedName());
            }
        }
    }

    private static void printHelp() {
        System.out.println("  packages              - list all package names");
        System.out.println("  types <package>       - list classes/interfaces in package");
        System.out.println("  methods <pkg|class>   - list public methods for a package or a class (FQN)");
        System.out.println("  controllers           - list @Controller / @RestController");
        System.out.println("  services              - list @Service");
        System.out.println("  repositories          - list @Repository");
        System.out.println("  entities              - list @Entity");
        System.out.println("  interfaces            - list all interfaces");
        System.out.println("  package-deps          - list package dependencies (grouped and sorted by package)");
        System.out.println("  export-csv <dir>      - write CSV matrices to directory");
        System.out.println("  export-html <file>    - write single HTML cross-reference report");
        System.out.println("  spring-beans          - list @Component/@Service/@RestController/… beans");
        System.out.println("  spring-wiring         - list DI edges (constructor / field / setter)");
        System.out.println("  persist-spring-json <file> - save beans + wiring as JSON");
        System.out.println("  persist-spring-neo4j  - upsert graph (NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD, NEO4J_DATABASE)");
        System.out.println("  persist-spring-couchbase - upsert docs (COUCHBASE_* env vars)");
        System.out.println("  sequence <method|class> [depth] [--svg <file>] - sequence diagram; writes .puml in cwd; class → pick #");
        System.out.println("  seq-diagram           - alias of sequence");
        System.out.println("  ↑/↓                   - recall last " + COMMAND_HISTORY_SIZE + " commands (interactive terminal)");
        System.out.println("  quit                  - exit (Ctrl+D also ends input)");
    }

    private static void printSequenceUsage() {
        System.out.println("Usage: sequence <methodName> [maxDepth] [--svg <output.svg>]");
        System.out.println("   or: sequence <ClassName|FQN> [maxDepth] [--svg ...] — pick method by number");
        System.out.println("   or: sequence <fully.qualified.ClassName> <methodName> [maxDepth] [--svg <output.svg>]");
        System.out.println("   or: sequence <SimpleClassName> <methodName> [...] (when only one type has that name)");
        System.out.println("  Single token: resolves as a class (FQN or unique simple name) first, else as a unique method name.");
        System.out.println("  maxDepth defaults to 10. SVG is rendered via kroki.io.");
        System.out.println("  Generated .puml is always written to the current working directory; .svg too (or --svg path).");
    }

    private static final class SeqDiagramArgs {
        final String classFqn;
        final String methodName;
        final int maxDepth;
        final Path svgOut;
        /** Selects entry overload when non-null and non-empty. */
        final List<String> entryParameterTypes;

        SeqDiagramArgs(String classFqn, String methodName, int maxDepth, Path svgOut) {
            this(classFqn, methodName, maxDepth, svgOut, null);
        }

        SeqDiagramArgs(String classFqn, String methodName, int maxDepth, Path svgOut, List<String> entryParameterTypes) {
            this.classFqn = classFqn;
            this.methodName = methodName;
            this.maxDepth = maxDepth;
            this.svgOut = svgOut;
            this.entryParameterTypes = entryParameterTypes;
        }
    }

    /**
     * Either {@code resolvedArgs} is set (ready to run), or {@code pendingClass} is set (interactive method #).
     */
    private record SequenceCommandInput(
        SeqDiagramArgs resolvedArgs,
        TypeInfo pendingClass,
        int pendingDepth,
        Path pendingSvg
    ) {}

    private static TypeInfo resolveClassTokenForMenu(ProjectModel model, String token) {
        TypeInfo byFqn = model.getType(token);
        if (byFqn != null) {
            return byFqn;
        }
        List<TypeInfo> simple = model.getTypesBySimpleName(token);
        if (simple.size() == 1) {
            return simple.get(0);
        }
        return null;
    }

    private static List<MethodInfo> sequenceMethodsInSourceOrder(TypeInfo type) {
        List<MethodInfo> all = new ArrayList<>();
        all.addAll(type.getPublicMethods());
        all.addAll(type.getProtectedMethods());
        all.sort(Comparator.comparingInt(MethodInfo::getLineNumber));
        return all;
    }

    private static SeqDiagramArgs completeSequenceMethodPick(
        ProjectModel model,
        TypeInfo type,
        int depth,
        Path svg,
        LineReader lineReader,
        Scanner scannerFallback
    ) {
        List<MethodInfo> methods = sequenceMethodsInSourceOrder(type);
        if (methods.isEmpty()) {
            System.out.println("No public or protected methods on " + type.getQualifiedName() + ".");
            return null;
        }
        System.out.println("Methods in " + type.getQualifiedName() + ":");
        for (int i = 0; i < methods.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + methods.get(i).getSignature());
        }
        String prompt = "Enter method # (1-" + methods.size() + "), blank to cancel: ";
        String line = readLineWithPrompt(lineReader, scannerFallback, prompt);
        if (line == null) {
            System.out.println("Cancelled.");
            return null;
        }
        line = line.trim();
        if (line.isEmpty()) {
            System.out.println("Cancelled.");
            return null;
        }
        try {
            int n = Integer.parseInt(line);
            if (n < 1 || n > methods.size()) {
                System.out.println("Number out of range.");
                return null;
            }
            MethodInfo chosen = methods.get(n - 1);
            return new SeqDiagramArgs(
                type.getQualifiedName(),
                chosen.getName(),
                depth,
                svg,
                new ArrayList<>(chosen.getParameterTypes())
            );
        } catch (NumberFormatException e) {
            System.out.println("Not a valid number.");
            return null;
        }
    }

    /**
     * Parses sequence arguments after stripping optional {@code --svg <path>} and trailing {@code depth}.
     * <ul>
     *   <li>{@code <ClassName|FQN>} — if that type is known (FQN or unique simple name), lists methods and waits for #</li>
     *   <li>{@code <method>} — otherwise, must match exactly one public/protected method in the project</li>
     *   <li>{@code <FQN> <method>} or {@code <SimpleClass> <method>} — as before</li>
     * </ul>
     */
    private static SequenceCommandInput parseSequenceCommandInput(ProjectModel model, String arg) {
        if (arg == null || arg.isBlank()) {
            return null;
        }
        List<String> tokens = new ArrayList<>(Arrays.asList(arg.trim().split("\\s+")));
        if (tokens.isEmpty()) {
            return null;
        }
        Path svg = null;
        for (int i = 0; i < tokens.size() - 1; i++) {
            if ("--svg".equals(tokens.get(i))) {
                svg = Path.of(tokens.get(i + 1));
                tokens.remove(i + 1);
                tokens.remove(i);
                i--;
            }
        }
        int depth = 10;
        if (!tokens.isEmpty()) {
            String last = tokens.get(tokens.size() - 1);
            try {
                int d = Integer.parseInt(last);
                if (d >= 0) {
                    depth = d;
                    tokens.remove(tokens.size() - 1);
                }
            } catch (NumberFormatException ignored) {
                // last token is not a depth number
            }
        }
        if (tokens.isEmpty()) {
            return null;
        }
        if (tokens.size() == 1) {
            String token = tokens.get(0);
            TypeInfo asClass = resolveClassTokenForMenu(model, token);
            if (asClass != null) {
                List<MethodInfo> methods = sequenceMethodsInSourceOrder(asClass);
                if (methods.isEmpty()) {
                    System.out.println("No public or protected methods on " + asClass.getQualifiedName() + ".");
                    return null;
                }
                return new SequenceCommandInput(null, asClass, depth, svg);
            }
            List<MethodInfo> matches = findSequenceTraceableMethodsByName(model, token);
            if (matches.isEmpty()) {
                System.out.println(
                    "No type or unique method \"" + token + "\". Use a class (FQN) or a method name that matches once."
                );
                return null;
            }
            if (matches.size() > 1) {
                System.out.println("Ambiguous method name \"" + token + "\". Matches:");
                for (MethodInfo m : matches) {
                    System.out.println("  " + m.getDeclaringTypeQualifiedName() + "." + m.getName());
                }
                return null;
            }
            return new SequenceCommandInput(
                new SeqDiagramArgs(matches.get(0).getDeclaringTypeQualifiedName(), token, depth, svg),
                null,
                0,
                null
            );
        }
        if (tokens.size() == 2) {
            String a = tokens.get(0);
            String method = tokens.get(1);
            TypeInfo byFqn = model.getType(a);
            if (byFqn != null) {
                if (!byFqn.hasSequenceTraceableMethod(method)) {
                    System.out.println("No public or protected method \"" + method + "\" on " + a + ".");
                    return null;
                }
                return new SequenceCommandInput(new SeqDiagramArgs(a, method, depth, svg), null, 0, null);
            }
            List<TypeInfo> simpleTypes = model.getTypesBySimpleName(a);
            List<TypeInfo> withMethod = new ArrayList<>();
            for (TypeInfo t : simpleTypes) {
                if (t.hasSequenceTraceableMethod(method)) {
                    withMethod.add(t);
                }
            }
            if (withMethod.size() == 1) {
                return new SequenceCommandInput(
                    new SeqDiagramArgs(withMethod.get(0).getQualifiedName(), method, depth, svg),
                    null,
                    0,
                    null
                );
            }
            if (withMethod.isEmpty()) {
                System.out.println("No type \"" + a + "\" with public/protected method \"" + method + "\" (or unknown FQN).");
                return null;
            }
            System.out.println("Ambiguous type \"" + a + "\" for method \"" + method + "\". Candidates:");
            for (TypeInfo t : withMethod) {
                System.out.println("  " + t.getQualifiedName());
            }
            return null;
        }
        System.out.println("Too many arguments for sequence (expected one method name, or type + method).");
        return null;
    }

    private static List<MethodInfo> findSequenceTraceableMethodsByName(ProjectModel model, String methodName) {
        List<MethodInfo> out = new ArrayList<>();
        for (TypeInfo t : model.getTypesByQualifiedName().values()) {
            for (MethodInfo m : t.getPublicMethods()) {
                if (methodName.equals(m.getName())) {
                    out.add(m);
                }
            }
            for (MethodInfo m : t.getProtectedMethods()) {
                if (methodName.equals(m.getName())) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null ? b : "";
    }

    /** Writes {@code .puml} under {@link System#getProperty} {@code user.dir}. */
    private static Path writeSequencePumlToCurrentDir(SeqDiagramArgs sd, String puml) throws IOException {
        Path dir = Path.of(System.getProperty("user.dir"));
        Path path = dir.resolve(sequencePumlFileName(sd));
        Files.writeString(path, puml, StandardCharsets.UTF_8);
        return path;
    }

    private static String sequencePumlFileName(SeqDiagramArgs sd) {
        String qn = sd.classFqn.replace('.', '-');
        String base = "sequence-" + qn + "-" + sd.methodName;
        if (sd.entryParameterTypes != null && !sd.entryParameterTypes.isEmpty()) {
            base += "-" + Integer.toHexString(sd.entryParameterTypes.hashCode());
        }
        base = sanitizeFileName(base);
        return base + ".puml";
    }

    private static String sanitizeFileName(String name) {
        String t = name.replaceAll("[^a-zA-Z0-9._-]+", "_");
        if (t.isBlank()) {
            return "sequence-diagram";
        }
        if (t.length() > 180) {
            t = t.substring(0, 180);
        }
        return t;
    }

    private static Path defaultSvgOutputPath(Path pumlFile) {
        String pumlName = pumlFile.getFileName().toString();
        String svgName = pumlName.endsWith(".puml")
            ? pumlName.substring(0, pumlName.length() - ".puml".length()) + ".svg"
            : pumlName + ".svg";
        Path parent = pumlFile.toAbsolutePath().getParent();
        return parent != null ? parent.resolve(svgName) : Path.of(svgName);
    }
}

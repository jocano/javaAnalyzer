package com.example.analyzer.web;

import com.example.analyzer.ClassDiagramGenerator;
import com.example.analyzer.JavaAnalyzer;
import com.example.analyzer.MatrixExporter;
import com.example.analyzer.PackageDependencyDiagramGenerator;
import com.example.analyzer.ProjectScanner;
import com.example.analyzer.QueryEngine;
import com.example.analyzer.model.InjectionEdge;
import com.example.analyzer.model.MethodInfo;
import com.example.analyzer.model.ProjectModel;
import com.example.analyzer.model.SpringComponent;
import com.example.analyzer.model.SpringComponentGraph;
import com.example.analyzer.model.TypeInfo;
import com.example.analyzer.persistence.AnalyzerSnapshot;
import com.example.analyzer.persistence.ChromaAnalyzerSnapshotStore;
import com.example.analyzer.persistence.ChromaQueryResult;
import com.example.analyzer.persistence.SqlAnalyzerSnapshotStore;
import com.example.analyzer.persistence.JsonAnalyzerSnapshotStore;
import com.example.analyzer.persistence.JsonSpringGraphStore;
import com.example.analyzer.seq.PlantUmlSvgExporter;
import com.example.analyzer.seq.SequenceDiagramGenerator;
import com.example.analyzer.spring.SpringComponentAnalyzer;
import com.example.analyzer.spring.SpringControllerServiceDiagramGenerator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class AnalyzerRuntime {
    private SpringComponentGraph springGraph;
    private ProjectModel model;
    private QueryEngine query;
    private MatrixExporter exporter;
    private final Path workingDir;
    private final SqlAnalyzerSnapshotStore sqlStore;
    private final String chromaUrl;

    public AnalyzerRuntime(Environment env, SqlAnalyzerSnapshotStore sqlStore) throws Exception {
        this.sqlStore   = sqlStore;
        this.workingDir = Path.of(System.getProperty("user.dir"));
        this.chromaUrl  = firstNonBlank(
            env.getProperty("analyzer.chroma.url"),
            System.getProperty("analyzer.chroma.url"),
            System.getenv("CHROMA_URL"),
            "http://localhost:8001"
        );

        String startupSource = firstNonBlank(
            env.getProperty("analyzer.startup.source"),
            System.getProperty("analyzer.startup.source")
        );

        if ("db".equalsIgnoreCase(startupSource)) {
            // ── Load from database ───────────────────────────────────────────
            String dbRoot = firstNonBlank(
                env.getProperty("analyzer.startup.db-root"),
                System.getProperty("analyzer.startup.db-root")
            );
            if (dbRoot == null || dbRoot.isBlank()) {
                throw new IllegalStateException(
                    "analyzer.startup.source=db requires analyzer.startup.db-root to be set");
            }
            sqlStore.initSchema();
            AnalyzerSnapshot snap = sqlStore.load(dbRoot);
            this.model       = snap.toProjectModel();
            this.springGraph = snap.getSpringComponentGraph();
            if (springGraph.getProjectRoot() == null || springGraph.getProjectRoot().isBlank()) {
                springGraph.setProjectRoot(model.getProjectRoot());
            }

        } else {
            String modelPath = firstNonBlank(
                env.getProperty("analyzer.model.path"),
                System.getProperty("analyzer.model.path"),
                env.getProperty("JAVA_CODE_ANALYZER_MODEL"),
                System.getenv("JAVA_CODE_ANALYZER_MODEL")
            );

            if (modelPath != null && !modelPath.isBlank() && Files.exists(Path.of(modelPath))) {
                // ── Load from JSON snapshot ──────────────────────────────────
                AnalyzerSnapshot snap = new JsonAnalyzerSnapshotStore().load(Path.of(modelPath));
                this.model       = snap.toProjectModel();
                this.springGraph = snap.getSpringComponentGraph();
                if (springGraph.getProjectRoot() == null || springGraph.getProjectRoot().isBlank()) {
                    springGraph.setProjectRoot(model.getProjectRoot());
                }
            } else {
                // ── Scan source tree ─────────────────────────────────────────
                Path root = Path.of(firstNonBlank(
                    env.getProperty("analyzer.project.root"),
                    System.getProperty("analyzer.project.root"),
                    workingDir.toString()
                ));
                List<Path> javaFiles = ProjectScanner.findJavaFiles(root);
                this.model       = JavaAnalyzer.analyze(root, javaFiles);
                this.springGraph = SpringComponentAnalyzer.analyze(model, javaFiles);
            }
        }

        this.query    = new QueryEngine(model);
        this.exporter = new MatrixExporter(model);
    }

    public CommandResult execute(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) {
            return CommandResult.error("Command is empty.");
        }
        String[] parts = rawCommand.trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";
        try {
            return switch (cmd) {
                case "help" -> CommandResult.ok(helpText());
                case "packages" -> packagesResult();
                case "types" -> typesResult(arg);
                case "methods" -> methodsResult(arg);
                case "controllers" -> groupedTypesResult(query.listControllers(), query.listRestControllers());
                case "services" -> groupedTypesResult(query.listServices());
                case "repositories" -> groupedTypesResult(query.listRepositories());
                case "entities" -> groupedTypesResult(query.listEntities());
                case "interfaces" -> groupedTypesResult(query.listInterfaces());
                case "package-deps", "pkg-deps" -> packageDepsResult();
                case "package-dependencies-diagram", "package-diagram", "pkg-diagram" -> packageDiagramResult();
                case "class-diagram" -> classDiagramResult(arg);
                case "spring-beans" -> springBeansResult();
                case "spring-wiring" -> springWiringResult();
                case "spring-controller-service-diagram" -> springControllerServiceDiagramResult();
                case "sequence", "seq-diagram" -> sequenceResult(arg);
                case "export-csv" -> exportCsvResult(arg);
                case "export-html" -> exportHtmlResult(arg);
                case "persist-spring-json" -> persistSpringJsonResult(arg);
                case "persist-model" -> persistModelResult(arg);
                case "persist-model-db" -> persistModelDbResult();
                case "load-model-db" -> loadModelDbResult(arg);
                case "persist-spring-chroma" -> persistSpringChromaResult();
                case "query-chroma" -> queryChromaResult(arg, false);
                case "query-chroma-beans" -> queryChromaResult(arg, true);
                case "query-chroma-wiring" -> queryChromaWiringResult(arg);
                default -> CommandResult.error("Unknown command. Type 'help'.");
            };
        } catch (Exception e) {
            return CommandResult.error(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    public List<String> availableCommands() {
        return Arrays.asList(
            "help", "packages", "types <package>", "methods <package|class>",
            "controllers", "services", "repositories", "entities", "interfaces",
            "package-deps", "package-dependencies-diagram", "class-diagram <package>",
            "spring-beans", "spring-wiring", "spring-controller-service-diagram",
            "sequence <fqn> <method> [depth] [--svg <file>]",
            "export-csv <dir>", "export-html <file>",
            "persist-spring-json <file>", "persist-model <file>",
            "persist-model-db", "load-model-db <projectRoot>",
            "persist-spring-chroma",
            "query-chroma <text> [n]", "query-chroma-beans <text> [n]",
            "query-chroma-wiring <text> [n]"
        );
    }

    private CommandResult packagesResult() {
        List<String> lines = new ArrayList<>();
        for (String p : query.listPackages()) {
            lines.add("  " + p);
        }
        return CommandResult.ok(String.join("\n", lines));
    }

    private CommandResult typesResult(String arg) {
        if (arg.isEmpty()) return CommandResult.error("Usage: types <package-name>");
        List<String> lines = new ArrayList<>();
        for (TypeInfo t : query.listTypesInPackage(arg)) {
            lines.add("  " + t.getSourceLocation() + "  " + t.getKind() + " " + t.getSimpleName() + " " + t.getAnnotations());
        }
        return CommandResult.ok(String.join("\n", lines));
    }

    private CommandResult methodsResult(String arg) {
        if (arg.isEmpty()) return CommandResult.error("Usage: methods <package-name> | methods <fully.qualified.ClassName>");
        List<MethodInfo> methodList = query.getType(arg) != null ? query.listPublicMethodsInClass(arg) : query.listPublicMethodsInPackage(arg);
        List<String> lines = new ArrayList<>();
        for (MethodInfo m : methodList) {
            lines.add("  " + m.getSourceLocation() + "  " + m.getDeclaringTypeSimpleName() + "." + m.getSignature());
        }
        return CommandResult.ok(String.join("\n", lines));
    }

    private CommandResult groupedTypesResult(List<TypeInfo>... lists) {
        TreeMap<String, List<TypeInfo>> byPackage = new TreeMap<>();
        for (List<TypeInfo> list : lists) {
            for (TypeInfo t : list) {
                String pkg = t.getPackageName() != null ? t.getPackageName() : "";
                byPackage.computeIfAbsent(pkg, k -> new ArrayList<>()).add(t);
            }
        }
        List<String> lines = new ArrayList<>();
        for (var e : byPackage.entrySet()) {
            String pkg = e.getKey();
            lines.add("  Package: " + (pkg.isEmpty() ? "(default package)" : pkg));
            for (TypeInfo t : e.getValue()) {
                lines.add("    " + t.getSourceLocation() + "  " + t.getQualifiedName());
            }
        }
        return CommandResult.ok(String.join("\n", lines));
    }

    private CommandResult packageDepsResult() {
        List<String> lines = new ArrayList<>();
        for (var e : query.getPackageDependencies().entrySet()) {
            String pkg = e.getKey();
            lines.add("  Package: " + (pkg.isEmpty() ? "(default package)" : pkg));
            if (e.getValue().isEmpty()) lines.add("    (none)");
            for (String used : e.getValue()) lines.add("    " + (used.isEmpty() ? "(default package)" : used));
        }
        return CommandResult.ok(String.join("\n", lines));
    }

    private CommandResult packageDiagramResult() throws Exception {
        String puml = PackageDependencyDiagramGenerator.toPlantUml(query.getPackageDependencies());
        Path pumlOut = workingDir.resolve("package-dependencies-diagram.puml");
        Path svgOut = workingDir.resolve("package-dependencies-diagram.svg");
        Files.writeString(pumlOut, puml, StandardCharsets.UTF_8);
        PlantUmlSvgExporter.exportSvg(puml, svgOut);
        return withSvg("PlantUML written to " + pumlOut.toAbsolutePath() + "\nSVG written to " + svgOut.toAbsolutePath(), pumlOut, svgOut);
    }

    private CommandResult classDiagramResult(String arg) throws Exception {
        if (arg.isBlank()) return CommandResult.error("Usage: class-diagram <package.name>");
        String base = ClassDiagramGenerator.fileNameBaseForPackage(arg.trim());
        Path pumlOut = workingDir.resolve(base + ".puml");
        Path svgOut = workingDir.resolve(base + ".svg");
        String puml = ClassDiagramGenerator.toPlantUml(model, query, arg.trim());
        Files.writeString(pumlOut, puml, StandardCharsets.UTF_8);
        PlantUmlSvgExporter.exportSvg(puml, svgOut);
        return withSvg("PlantUML written to " + pumlOut.toAbsolutePath() + "\nSVG written to " + svgOut.toAbsolutePath(), pumlOut, svgOut);
    }

    private CommandResult springBeansResult() {
        List<String> lines = new ArrayList<>();
        for (SpringComponent c : springGraph.getComponents()) lines.add("  " + c.getStereotypes() + " " + c.getQualifiedName());
        lines.add("  (" + springGraph.getComponents().size() + " beans)");
        return CommandResult.ok(String.join("\n", lines));
    }

    private CommandResult springWiringResult() {
        List<String> lines = new ArrayList<>();
        for (InjectionEdge e : springGraph.getInjectionEdges()) {
            String to = e.getToQualifiedName() != null ? e.getToQualifiedName() : ("?" + e.getToTypeSimpleName());
            lines.add("  " + e.getFromQualifiedName() + " --" + e.getKind() + "--> " + to
                + (e.getQualifier() != null ? " @Qualifier(" + e.getQualifier() + ")" : ""));
        }
        lines.add("  (" + springGraph.getInjectionEdges().size() + " edges)");
        return CommandResult.ok(String.join("\n", lines));
    }

    private CommandResult springControllerServiceDiagramResult() throws Exception {
        String puml = SpringControllerServiceDiagramGenerator.toPlantUml(springGraph);
        Path pumlOut = workingDir.resolve("spring-controllers-services-diagram.puml");
        Path svgOut = workingDir.resolve("spring-controllers-services-diagram.svg");
        Files.writeString(pumlOut, puml, StandardCharsets.UTF_8);
        PlantUmlSvgExporter.exportSvg(puml, svgOut);
        return withSvg("PlantUML written to " + pumlOut.toAbsolutePath() + "\nSVG written to " + svgOut.toAbsolutePath(), pumlOut, svgOut);
    }

    private CommandResult sequenceResult(String arg) throws Exception {
        if (arg.isBlank()) return CommandResult.error("Usage: sequence <fully.qualified.ClassName> <methodName> [maxDepth] [--svg <output.svg>]");
        List<String> tokens = new ArrayList<>(Arrays.asList(arg.split("\\s+")));
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
        if (tokens.size() >= 3) {
            try {
                depth = Integer.parseInt(tokens.get(tokens.size() - 1));
                tokens.remove(tokens.size() - 1);
            } catch (NumberFormatException ignored) {
            }
        }
        if (tokens.size() != 2) {
            return CommandResult.error("Web mode supports: sequence <FQN> <method> [depth] [--svg <file>]");
        }
        String classFqn = tokens.get(0);
        String method = tokens.get(1);
        String puml = new SequenceDiagramGenerator(model).generate(classFqn, method, depth, null);
        String pumlName = "sequence-" + classFqn.replace('.', '-') + "-" + method + ".puml";
        Path pumlOut = workingDir.resolve(pumlName);
        Files.writeString(pumlOut, puml, StandardCharsets.UTF_8);
        Path svgOut = svg != null ? svg : workingDir.resolve(pumlName.replace(".puml", ".svg"));
        PlantUmlSvgExporter.exportSvg(puml, svgOut);
        return withSvg("PlantUML written to " + pumlOut.toAbsolutePath() + "\nSVG written to " + svgOut.toAbsolutePath(), pumlOut, svgOut);
    }

    private CommandResult exportCsvResult(String arg) throws IOException {
        if (arg.isBlank()) return CommandResult.error("Usage: export-csv <output-directory>");
        Path dir = Path.of(arg);
        Files.createDirectories(dir);
        exporter.exportPackageTypesCsv(dir.resolve("package-types.csv"));
        exporter.exportTypeAnnotationCsv(dir.resolve("type-annotation.csv"));
        exporter.exportPackageStereotypeCsv(dir.resolve("package-stereotype.csv"));
        exporter.exportControllerServiceCsv(dir.resolve("controller-service.csv"));
        exporter.exportServiceRepositoryCsv(dir.resolve("service-repository.csv"));
        return CommandResult.ok("CSV files written to " + dir.toAbsolutePath());
    }

    private CommandResult exportHtmlResult(String arg) throws IOException {
        if (arg.isBlank()) return CommandResult.error("Usage: export-html <output-file.html>");
        Path out = Path.of(arg);
        exporter.exportHtmlReport(out);
        return CommandResult.ok("HTML report written to " + out.toAbsolutePath());
    }

    private CommandResult persistSpringJsonResult(String arg) throws IOException {
        if (arg.isBlank()) return CommandResult.error("Usage: persist-spring-json <file.json>");
        Path out = Path.of(arg);
        new JsonSpringGraphStore().save(springGraph, out);
        return CommandResult.ok("Spring graph JSON written to " + out.toAbsolutePath());
    }

    private CommandResult persistModelResult(String arg) throws IOException {
        if (arg.isBlank()) return CommandResult.error("Usage: persist-model <snapshot.json>");
        Path out = Path.of(arg);
        new JsonAnalyzerSnapshotStore().save(AnalyzerSnapshot.capture(model, springGraph), out);
        return CommandResult.ok("Full analyzer snapshot written to " + out.toAbsolutePath());
    }

    private CommandResult withSvg(String text, Path pumlPath, Path svgPath) throws IOException {
        CommandResult r = CommandResult.ok(text);
        r.setPumlPath(pumlPath.toAbsolutePath().toString());
        r.setSvgPath(svgPath.toAbsolutePath().toString());
        if (Files.exists(svgPath)) {
            r.setSvgContent(Files.readString(svgPath, StandardCharsets.UTF_8));
        }
        return r;
    }

    private CommandResult persistModelDbResult() {
        AnalyzerSnapshot snap = AnalyzerSnapshot.capture(model, springGraph);
        sqlStore.save(snap);
        return CommandResult.ok(String.format(
            "Model persisted to database for project root: %s%n  %d packages, %d types, %d Spring beans",
            model.getProjectRoot(),
            model.getPackages().size(),
            model.getTypesByQualifiedName().size(),
            springGraph.getComponents().size()));
    }

    private CommandResult loadModelDbResult(String arg) {
        if (arg.isBlank()) {
            List<String> roots = sqlStore.listProjectRoots();
            if (roots.isEmpty()) {
                return CommandResult.error(
                    "Usage: load-model-db <projectRoot>\n  No snapshots found in the database.");
            }
            return CommandResult.error(
                "Usage: load-model-db <projectRoot>\n  Available roots:\n    "
                + String.join("\n    ", roots));
        }
        AnalyzerSnapshot snap = sqlStore.load(arg);
        this.model       = snap.toProjectModel();
        this.springGraph = snap.getSpringComponentGraph();
        if (springGraph.getProjectRoot() == null || springGraph.getProjectRoot().isBlank()) {
            springGraph.setProjectRoot(model.getProjectRoot());
        }
        this.query    = new QueryEngine(model);
        this.exporter = new MatrixExporter(model);
        return CommandResult.ok(String.format(
            "Model loaded from database for project root: %s%n  %d packages, %d types, %d Spring beans",
            model.getProjectRoot(),
            model.getPackages().size(),
            model.getTypesByQualifiedName().size(),
            springGraph.getComponents().size()));
    }

    private CommandResult persistSpringChromaResult() throws IOException {
        ChromaAnalyzerSnapshotStore store = new ChromaAnalyzerSnapshotStore(chromaUrl);
        String summary = store.upsertSnapshot(AnalyzerSnapshot.capture(model, springGraph));
        return CommandResult.ok(summary);
    }

    private CommandResult queryChromaResult(String arg, boolean beansMode) throws IOException {
        String label = beansMode ? "query-chroma-beans" : "query-chroma";
        if (arg.isBlank()) {
            return CommandResult.error("Usage: " + label + " <text> [n]");
        }
        String[] parts = arg.split("\\s+");
        int n = 5;
        String queryText = arg;
        try {
            n = Integer.parseInt(parts[parts.length - 1]);
            queryText = String.join(" ", Arrays.copyOf(parts, parts.length - 1));
        } catch (NumberFormatException ignored) {}

        ChromaAnalyzerSnapshotStore store = new ChromaAnalyzerSnapshotStore(chromaUrl);
        List<ChromaQueryResult> results = beansMode
            ? store.queryBeans(model.getProjectRoot(), queryText, n)
            : store.queryTypes(model.getProjectRoot(), queryText, n);

        if (results.isEmpty()) {
            return CommandResult.error("No results. Run persist-spring-chroma first.");
        }
        List<String> lines = new ArrayList<>();
        for (ChromaQueryResult r : results) {
            lines.add(String.format("  [%.3f] %s", r.similarity(), r.qualifiedName()));
            if (!r.sourcePath().isBlank()) {
                lines.add(String.format("         %s:%d", r.sourcePath(), r.lineNumber()));
            }
        }
        return CommandResult.ok(String.join("\n", lines));
    }

    private CommandResult queryChromaWiringResult(String arg) throws IOException {
        if (arg.isBlank()) {
            return CommandResult.error(
                "Usage: query-chroma-wiring <text> [n]\n"
                + "  e.g.: query-chroma-wiring services used by BeerController\n"
                + "        query-chroma-wiring what repositories does BeerService access\n"
                + "        query-chroma-wiring who injects BrewingService");
        }
        String[] parts = arg.split("\\s+");
        int n = 5;
        String queryText = arg;
        try {
            n = Integer.parseInt(parts[parts.length - 1]);
            queryText = String.join(" ", Arrays.copyOf(parts, parts.length - 1));
        } catch (NumberFormatException ignored) {}

        ChromaAnalyzerSnapshotStore store = new ChromaAnalyzerSnapshotStore(chromaUrl);
        List<ChromaQueryResult> results =
            store.queryWiring(model.getProjectRoot(), queryText, n);

        if (results.isEmpty()) {
            return CommandResult.error("No results. Run persist-spring-chroma first.");
        }
        List<String> lines = new ArrayList<>();
        for (ChromaQueryResult r : results) {
            lines.add(String.format("  [%.3f] %s", r.similarity(), r.document().split("\n")[0]));
            Map<String, Object> m = r.metadata();
            lines.add(String.format("         relation=%-14s  from=%-30s  to=%s",
                m.getOrDefault("relation", ""),
                m.getOrDefault("from_simple", ""),
                m.getOrDefault("to_simple", "")));
        }
        return CommandResult.ok(String.join("\n", lines));
    }

    private String helpText() {
        return String.join("\n", availableCommands());
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}

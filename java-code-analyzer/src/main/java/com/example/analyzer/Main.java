package com.example.analyzer;

import com.example.analyzer.model.MethodInfo;
import com.example.analyzer.model.ProjectModel;
import com.example.analyzer.model.TypeInfo;

import java.nio.file.Path;
import java.util.ArrayList;
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
        QueryEngine q = new QueryEngine(model);
        MatrixExporter exporter = new MatrixExporter(model);

        System.out.println("Ready. Commands: packages | types <pkg> | methods <package|class> | controllers | services | ...");
        System.out.println("  export-csv <dir> | export-html <file> | help | quit");

        try (Scanner in = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                if (!in.hasNextLine()) break;
                String line = in.nextLine().trim();
                if (line.isEmpty()) continue;
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
                    default:
                        System.out.println("Unknown command. Type 'help'.");
                }
            }
        }
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
        System.out.println("  quit                  - exit");
    }
}

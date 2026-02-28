package com.example.analyzer;

import com.example.analyzer.model.ProjectModel;
import com.example.analyzer.model.TypeInfo;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports cross-reference matrices (CSV and HTML) for packages, types, controllers, services, etc.
 */
public class MatrixExporter {

    private final ProjectModel model;
    private final QueryEngine query;

    public MatrixExporter(ProjectModel model) {
        this.model = model;
        this.query = new QueryEngine(model);
    }

    /** Package x Types matrix: each row = package, columns = type count and type names. */
    public void exportPackageTypesCsv(Path outFile) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outFile))) {
            w.println("Package,TypeCount,Types");
            for (String pkg : query.listPackages()) {
                List<TypeInfo> types = query.listTypesInPackage(pkg);
                String typesList = types.stream().map(TypeInfo::getSimpleName).reduce((a, b) -> a + "; " + b).orElse("");
                w.println(escapeCsv(pkg) + "," + types.size() + "," + escapeCsv(typesList));
            }
        }
    }

    /** Controller x Service dependency matrix (which controller uses which service). */
    public void exportControllerServiceCsv(Path outFile) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outFile))) {
            w.println("Controller,Service,Used");
            for (TypeInfo c : query.listControllers()) {
                for (String dep : c.getFieldTypes()) {
                    TypeInfo service = model.getType(resolveQualified(dep, c.getPackageName()));
                    if (service != null && service.hasAnnotation("Service")) {
                        w.println(escapeCsv(c.getQualifiedName()) + "," + escapeCsv(service.getQualifiedName()) + ",field");
                    }
                }
            }
            for (TypeInfo c : query.listRestControllers()) {
                for (String dep : c.getFieldTypes()) {
                    TypeInfo service = model.getType(resolveQualified(dep, c.getPackageName()));
                    if (service != null && service.hasAnnotation("Service")) {
                        w.println(escapeCsv(c.getQualifiedName()) + "," + escapeCsv(service.getQualifiedName()) + ",field");
                    }
                }
            }
        }
    }

    /** Service x Repository dependency matrix. */
    public void exportServiceRepositoryCsv(Path outFile) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outFile))) {
            w.println("Service,Repository,Used");
            for (TypeInfo s : query.listServices()) {
                for (String dep : s.getFieldTypes()) {
                    TypeInfo repo = model.getType(resolveQualified(dep, s.getPackageName()));
                    if (repo != null && repo.hasAnnotation("Repository")) {
                        w.println(escapeCsv(s.getQualifiedName()) + "," + escapeCsv(repo.getQualifiedName()) + ",field");
                    }
                }
            }
        }
    }

    /** Full type x annotation matrix: rows = types, columns = Controller, Service, Repository, Entity, etc. */
    public void exportTypeAnnotationCsv(Path outFile) throws IOException {
        String[] anns = { "Controller", "RestController", "Service", "Repository", "Entity", "Component", "Configuration" };
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outFile))) {
            w.println("Type," + String.join(",", anns));
            for (TypeInfo t : model.getTypesByQualifiedName().values()) {
                List<String> row = new ArrayList<>();
                row.add(escapeCsv(t.getQualifiedName()));
                for (String a : anns) {
                    row.add(t.hasAnnotation(a) ? "1" : "0");
                }
                w.println(String.join(",", row));
            }
        }
    }

    /** Package x stereotype count matrix. */
    public void exportPackageStereotypeCsv(Path outFile) throws IOException {
        String[] anns = { "Controller", "RestController", "Service", "Repository", "Entity" };
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outFile))) {
            w.println("Package," + String.join(",", anns));
            for (String pkg : query.listPackages()) {
                List<String> row = new ArrayList<>();
                row.add(escapeCsv(pkg));
                for (String a : anns) {
                    long count = query.listTypesInPackage(pkg).stream().filter(t -> t.hasAnnotation(a)).count();
                    row.add(String.valueOf(count));
                }
                w.println(String.join(",", row));
            }
        }
    }

    /** Single HTML report with tables for packages, controllers, services, and dependency matrices. */
    public void exportHtmlReport(Path outFile) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outFile))) {
            w.println("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Java Code Cross-Reference</title>");
            w.println("<style>table{border-collapse:collapse}th,td{border:1px solid #ccc;padding:6px 10px;text-align:left}th{background:#eee}</style></head><body>");
            w.println("<h1>Project: " + model.getProjectRoot() + "</h1>");

            w.println("<h2>Packages &amp; types</h2><table><tr><th>Package</th><th>Count</th><th>Types</th></tr>");
            for (String pkg : query.listPackages()) {
                List<TypeInfo> types = query.listTypesInPackage(pkg);
                String typesList = types.stream().map(TypeInfo::getSimpleName).reduce((a, b) -> a + ", " + b).orElse("");
                w.println("<tr><td>" + escapeHtml(pkg) + "</td><td>" + types.size() + "</td><td>" + escapeHtml(typesList) + "</td></tr>");
            }
            w.println("</table>");

            w.println("<h2>Controllers / RestControllers</h2><table><tr><th>Type</th><th>Package</th></tr>");
            for (TypeInfo t : query.listControllers()) w.println("<tr><td>" + escapeHtml(t.getSimpleName()) + "</td><td>" + escapeHtml(t.getPackageName()) + "</td></tr>");
            for (TypeInfo t : query.listRestControllers()) w.println("<tr><td>" + escapeHtml(t.getSimpleName()) + "</td><td>" + escapeHtml(t.getPackageName()) + "</td></tr>");
            w.println("</table>");

            w.println("<h2>Services</h2><table><tr><th>Type</th><th>Package</th></tr>");
            for (TypeInfo t : query.listServices()) w.println("<tr><td>" + escapeHtml(t.getSimpleName()) + "</td><td>" + escapeHtml(t.getPackageName()) + "</td></tr>");
            w.println("</table>");

            w.println("<h2>Repositories</h2><table><tr><th>Type</th><th>Package</th></tr>");
            for (TypeInfo t : query.listRepositories()) w.println("<tr><td>" + escapeHtml(t.getSimpleName()) + "</td><td>" + escapeHtml(t.getPackageName()) + "</td></tr>");
            w.println("</table>");

            w.println("<h2>Controller → Service</h2><table><tr><th>Controller</th><th>Service</th></tr>");
            for (TypeInfo c : query.listControllers()) {
                for (String dep : c.getFieldTypes()) {
                    TypeInfo s = model.getType(resolveQualified(dep, c.getPackageName()));
                    if (s != null && s.hasAnnotation("Service")) w.println("<tr><td>" + escapeHtml(c.getSimpleName()) + "</td><td>" + escapeHtml(s.getSimpleName()) + "</td></tr>");
                }
            }
            for (TypeInfo c : query.listRestControllers()) {
                for (String dep : c.getFieldTypes()) {
                    TypeInfo s = model.getType(resolveQualified(dep, c.getPackageName()));
                    if (s != null && s.hasAnnotation("Service")) w.println("<tr><td>" + escapeHtml(c.getSimpleName()) + "</td><td>" + escapeHtml(s.getSimpleName()) + "</td></tr>");
                }
            }
            w.println("</table>");

            w.println("<h2>Service → Repository</h2><table><tr><th>Service</th><th>Repository</th></tr>");
            for (TypeInfo s : query.listServices()) {
                for (String dep : s.getFieldTypes()) {
                    TypeInfo r = model.getType(resolveQualified(dep, s.getPackageName()));
                    if (r != null && r.hasAnnotation("Repository")) w.println("<tr><td>" + escapeHtml(s.getSimpleName()) + "</td><td>" + escapeHtml(r.getSimpleName()) + "</td></tr>");
                }
            }
            w.println("</table>");

            w.println("</body></html>");
        }
    }

    private static String resolveQualified(String simpleOrQualified, String currentPackage) {
        if (simpleOrQualified.contains(".")) return simpleOrQualified;
        return currentPackage.isEmpty() ? simpleOrQualified : currentPackage + "." + simpleOrQualified;
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

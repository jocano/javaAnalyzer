package com.example.analyzer;

import com.example.analyzer.model.MethodInfo;
import com.example.analyzer.model.ProjectModel;
import com.example.analyzer.model.TypeInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * PlantUML class diagram for types in one package, including related types (superclasses, interfaces,
 * field and method-signature references) that exist in the {@link ProjectModel}.
 */
public final class ClassDiagramGenerator {

    private ClassDiagramGenerator() {}

    public static String toPlantUml(ProjectModel model, QueryEngine query, String packageName) {
        if (packageName == null || packageName.isBlank()) {
            throw new IllegalArgumentException("package name required");
        }
        String pkg = packageName.trim();
        List<TypeInfo> focal = model.getTypesInPackage(pkg);
        if (focal.isEmpty()) {
            return "@startuml\nskinparam shadowing false\ntitle Class diagram\n"
                + "note right\n  No types in package \"" + escapePlant(pkg) + "\".\nend note\n@enduml\n";
        }

        Set<TypeInfo> visible = new LinkedHashSet<>(focal);
        for (TypeInfo t : focal) {
            collectRelated(t, query, visible);
        }

        List<TypeInfo> ordered = visible.stream()
            .sorted(Comparator.comparing(TypeInfo::getQualifiedName))
            .collect(Collectors.toList());

        Map<String, String> idByFqn = new LinkedHashMap<>();
        int n = 0;
        for (TypeInfo t : ordered) {
            idByFqn.put(t.getQualifiedName(), "T" + (n++));
        }

        StringBuilder sb = new StringBuilder(1024);
        sb.append("@startuml\n");
        sb.append("skinparam shadowing false\n");
        sb.append("left to right direction\n");
        sb.append("title Class diagram: ").append(escapePlant(pkg)).append('\n');
        sb.append("legend right\n");
        sb.append("  <|-- extends    <|.. implements\n");
        sb.append("  --> field       ..> method signature (return/params)\n");
        sb.append("end legend\n\n");

        sb.append("package \"").append(escapePlant(pkg)).append("\" {\n");
        for (TypeInfo t : ordered) {
            if (pkg.equals(nz(t.getPackageName()))) {
                emitTypeDeclaration(sb, t, idByFqn, "  ");
            }
        }
        sb.append("}\n\n");

        TreeSet<String> outsidePkgs = ordered.stream()
            .map(TypeInfo::getPackageName)
            .filter(p -> !pkg.equals(nz(p)))
            .collect(Collectors.toCollection(TreeSet::new));

        for (String op : outsidePkgs) {
            sb.append("package \"").append(escapePlant(nz(op))).append("\" {\n");
            for (TypeInfo t : ordered) {
                if (nz(t.getPackageName()).equals(op)) {
                    emitTypeDeclaration(sb, t, idByFqn, "  ");
                }
            }
            sb.append("}\n");
        }
        sb.append('\n');

        Set<String> seenEdges = new HashSet<>();
        for (TypeInfo t : focal) {
            emitEdgesFromFocal(sb, t, query, idByFqn, seenEdges);
        }

        sb.append("@enduml\n");
        return sb.toString();
    }

    public static String fileNameBaseForPackage(String packageName) {
        String p = packageName == null ? "package" : packageName.trim();
        if (p.isEmpty()) {
            p = "default-package";
        }
        String s = p.replaceAll("[^a-zA-Z0-9._-]+", "_");
        if (s.length() > 120) {
            s = s.substring(0, 120);
        }
        return "class-diagram-" + s;
    }

    private static void collectRelated(TypeInfo t, QueryEngine query, Set<TypeInfo> visible) {
        String p = nz(t.getPackageName());
        if (t.getExtendsType() != null) {
            TypeInfo x = query.resolveTypeReference(t.getExtendsType(), p);
            if (x != null) {
                visible.add(x);
            }
        }
        for (String impl : t.getImplementsTypes()) {
            TypeInfo x = query.resolveTypeReference(impl, p);
            if (x != null) {
                visible.add(x);
            }
        }
        for (String ft : t.getFieldTypes()) {
            TypeInfo x = query.resolveTypeReference(ft, p);
            if (x != null) {
                visible.add(x);
            }
        }
        for (MethodInfo m : t.getPublicMethods()) {
            addMethodRefTypes(t, m, query, visible);
        }
        for (MethodInfo m : t.getProtectedMethods()) {
            addMethodRefTypes(t, m, query, visible);
        }
    }

    private static void addMethodRefTypes(TypeInfo t, MethodInfo m, QueryEngine query, Set<TypeInfo> visible) {
        String p = nz(t.getPackageName());
        String ret = stripRough(m.getReturnType());
        if (!shouldSkipRefString(ret)) {
            TypeInfo r = query.resolveTypeReference(ret, p);
            if (r != null) {
                visible.add(r);
            }
        }
        for (String par : m.getParameterTypes()) {
            String ps = stripRough(par);
            if (shouldSkipRefString(ps)) {
                continue;
            }
            TypeInfo pt = query.resolveTypeReference(ps, p);
            if (pt != null) {
                visible.add(pt);
            }
        }
    }

    private static void emitTypeDeclaration(StringBuilder sb, TypeInfo t, Map<String, String> idByFqn, String indent) {
        String id = idByFqn.get(t.getQualifiedName());
        String name = escapePlant(t.getSimpleName());
        TypeInfo.Kind k = t.getKind();
        if (k == TypeInfo.Kind.INTERFACE) {
            sb.append(indent).append("interface \"").append(name).append("\" as ").append(id).append('\n');
        } else if (k == TypeInfo.Kind.ENUM) {
            sb.append(indent).append("enum \"").append(name).append("\" as ").append(id).append('\n');
        } else {
            String st = stereotypeFor(t);
            sb.append(indent).append("class \"").append(name).append("\" as ").append(id);
            if (st != null) {
                sb.append(" <<").append(st).append(">>");
            }
            sb.append('\n');
        }
    }

    /** First stereotype annotation short name, if any. */
    private static String stereotypeFor(TypeInfo t) {
        for (String a : t.getAnnotations()) {
            String shortName = a.contains(".") ? a.substring(a.lastIndexOf('.') + 1) : a;
            if (shortName.equals("Controller") || shortName.equals("RestController") || shortName.equals("Service")
                || shortName.equals("Repository") || shortName.equals("Component") || shortName.equals("Configuration")
                || shortName.equals("Entity")) {
                return shortName;
            }
        }
        return null;
    }

    private static void emitEdgesFromFocal(
        StringBuilder sb,
        TypeInfo from,
        QueryEngine query,
        Map<String, String> idByFqn,
        Set<String> seenEdges
    ) {
        String fpkg = nz(from.getPackageName());
        String fid = idByFqn.get(from.getQualifiedName());
        if (fid == null) {
            return;
        }

        if (from.getExtendsType() != null) {
            TypeInfo sup = query.resolveTypeReference(from.getExtendsType(), fpkg);
            if (sup != null) {
                String sid = idByFqn.get(sup.getQualifiedName());
                if (sid != null) {
                    String key = "ext|" + sid + "|" + fid;
                    if (seenEdges.add(key)) {
                        sb.append(sid).append(" <|-- ").append(fid).append('\n');
                    }
                }
            }
        }
        for (String impl : from.getImplementsTypes()) {
            TypeInfo it = query.resolveTypeReference(impl, fpkg);
            if (it == null) {
                continue;
            }
            String iid = idByFqn.get(it.getQualifiedName());
            if (iid != null) {
                String key = "impl|" + iid + "|" + fid;
                if (seenEdges.add(key)) {
                    sb.append(iid).append(" <|.. ").append(fid).append('\n');
                }
            }
        }

        Map<String, List<String>> targetToFields = new TreeMap<>();
        for (var e : from.getFieldsByName().entrySet()) {
            TypeInfo ft = query.resolveTypeReference(e.getValue(), fpkg);
            if (ft == null) {
                continue;
            }
            String tid = idByFqn.get(ft.getQualifiedName());
            if (tid == null) {
                continue;
            }
            targetToFields.computeIfAbsent(ft.getQualifiedName(), k -> new ArrayList<>()).add(e.getKey());
        }
        Set<String> fieldTargetFqns = new HashSet<>();
        for (var e : targetToFields.entrySet()) {
            String tid = idByFqn.get(e.getKey());
            String label = String.join(", ", e.getValue());
            String key = "fld|" + fid + "|" + tid + "|" + label;
            if (!seenEdges.add(key)) {
                continue;
            }
            fieldTargetFqns.add(e.getKey());
            sb.append(fid).append(" --> ").append(tid).append(" : ").append(escapePlant(label)).append('\n');
        }

        Set<String> methTargets = new TreeSet<>();
        for (MethodInfo m : from.getPublicMethods()) {
            collectMethodTargets(from, m, query, idByFqn, methTargets);
        }
        for (MethodInfo m : from.getProtectedMethods()) {
            collectMethodTargets(from, m, query, idByFqn, methTargets);
        }
        for (String tgtFqn : methTargets) {
            if (fieldTargetFqns.contains(tgtFqn)) {
                continue;
            }
            String tid = idByFqn.get(tgtFqn);
            if (tid == null) {
                continue;
            }
            String key = "use|" + fid + "|" + tid;
            if (seenEdges.add(key)) {
                sb.append(fid).append(" ..> ").append(tid).append(" : «uses»\n");
            }
        }
    }

    private static void collectMethodTargets(
        TypeInfo from,
        MethodInfo m,
        QueryEngine query,
        Map<String, String> idByFqn,
        Set<String> outFqns
    ) {
        String fpkg = nz(from.getPackageName());
        String ret = stripRough(m.getReturnType());
        if (!shouldSkipRefString(ret)) {
            TypeInfo r = query.resolveTypeReference(ret, fpkg);
            if (r != null && idByFqn.containsKey(r.getQualifiedName())) {
                outFqns.add(r.getQualifiedName());
            }
        }
        for (String par : m.getParameterTypes()) {
            String ps = stripRough(par);
            if (shouldSkipRefString(ps)) {
                continue;
            }
            TypeInfo pt = query.resolveTypeReference(ps, fpkg);
            if (pt != null && idByFqn.containsKey(pt.getQualifiedName())) {
                outFqns.add(pt.getQualifiedName());
            }
        }
    }

    private static String stripRough(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replaceAll("<[^>]*>", "").trim();
        int dots = s.lastIndexOf('.');
        if (dots >= 0 && dots < s.length() - 1) {
            String last = s.substring(dots + 1);
            if (last.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                return last;
            }
        }
        return s;
    }

    private static boolean shouldSkipRefString(String s) {
        if (s == null || s.isBlank()) {
            return true;
        }
        return switch (s) {
            case "void", "int", "long", "short", "byte", "char", "boolean", "float", "double",
                "Integer", "Long", "Short", "Byte", "Character", "Boolean", "Float", "Double",
                "String", "Object", "Class", "List", "Map", "Set", "Collection", "Iterable",
                "Optional" -> true;
            default -> false;
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String escapePlant(String s) {
        return s.replace("\"", "'");
    }
}

package com.example.analyzer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * PlantUML diagram of inter-package relationships using the same rules as {@link QueryEngine#getPackageDependencies()}:
 * edges come from extends, implements, field types, and {@code import} of types in the model.
 */
public final class PackageDependencyDiagramGenerator {

    private PackageDependencyDiagramGenerator() {}

    public static String toPlantUml(Map<String, Set<String>> packageDependencies) {
        TreeSet<String> all = new TreeSet<>();
        for (var e : packageDependencies.entrySet()) {
            if (e.getKey() != null) {
                all.add(e.getKey());
            }
            for (String to : e.getValue()) {
                if (to != null) {
                    all.add(to);
                }
            }
        }
        if (all.isEmpty()) {
            return "@startuml\nskinparam shadowing false\ntitle Package dependencies\n"
                + "note right\n  No packages in model.\nend note\n@enduml\n";
        }

        Map<String, String> aliasByPackage = new LinkedHashMap<>();
        int i = 0;
        for (String pkg : all) {
            aliasByPackage.put(pkg, "pkg" + (i++));
        }

        StringBuilder sb = new StringBuilder(256 + all.size() * 48);
        sb.append("@startuml\n");
        sb.append("skinparam shadowing false\n");
        sb.append("skinparam linetype ortho\n");
        sb.append("left to right direction\n");
        sb.append("title Package dependencies (from project model)\n");
        sb.append("legend right\n");
        sb.append("  Arrows: extends, implements, field types, and imports\n");
        sb.append("  of in-project types (not method parameters or body-only references).\n");
        sb.append("end legend\n\n");

        for (String pkg : all) {
            sb.append("rectangle \"")
                .append(escapeLabel(displayPackage(pkg)))
                .append("\" as ")
                .append(aliasByPackage.get(pkg))
                .append('\n');
        }
        sb.append('\n');

        Set<String> seenEdge = new LinkedHashSet<>();
        for (var e : new TreeMap<>(packageDependencies).entrySet()) {
            String from = e.getKey();
            if (from == null) {
                continue;
            }
            String aFrom = aliasByPackage.get(from);
            if (aFrom == null) {
                continue;
            }
            for (String to : new TreeSet<>(e.getValue())) {
                if (to == null) {
                    continue;
                }
                String aTo = aliasByPackage.get(to);
                if (aTo == null) {
                    continue;
                }
                String key = from + "\0" + to;
                if (!seenEdge.add(key)) {
                    continue;
                }
                sb.append(aFrom).append(" --> ").append(aTo).append('\n');
            }
        }

        sb.append("@enduml\n");
        return sb.toString();
    }

    private static String displayPackage(String pkg) {
        return (pkg == null || pkg.isEmpty()) ? "(default package)" : pkg;
    }

    private static String escapeLabel(String s) {
        return s.replace("\"", "'");
    }
}

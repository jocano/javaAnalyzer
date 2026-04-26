package com.example.analyzer.spring;

import com.example.analyzer.model.InjectionEdge;
import com.example.analyzer.model.SpringComponent;
import com.example.analyzer.model.SpringComponentGraph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds a PlantUML component diagram of Spring {@code @Controller}/{@code @RestController}
 * beans and {@code @Service} beans, including DI edges between them.
 */
public final class SpringControllerServiceDiagramGenerator {

    private SpringControllerServiceDiagramGenerator() {}

    public static String toPlantUml(SpringComponentGraph graph) {
        Map<String, SpringComponent> byFqn = new LinkedHashMap<>();
        for (SpringComponent c : graph.getComponents()) {
            byFqn.put(c.getQualifiedName(), c);
        }

        Set<String> csFqns = new TreeSet<>();
        for (SpringComponent c : graph.getComponents()) {
            if (isController(c) || isService(c)) {
                csFqns.add(c.getQualifiedName());
            }
        }

        if (csFqns.isEmpty()) {
            return "@startuml\nskinparam shadowing false\ntitle Spring controllers and services\n"
                + "note right\n  No @Controller, @RestController, or @Service types found.\nend note\n@enduml\n";
        }

        Map<String, String> aliasByFqn = new LinkedHashMap<>();
        int i = 0;
        for (String fqn : csFqns) {
            aliasByFqn.put(fqn, "cs" + (i++));
        }

        List<String> edgeLines = new ArrayList<>();
        Set<String> seenEdge = new LinkedHashSet<>();
        for (InjectionEdge e : graph.getInjectionEdges()) {
            String from = e.getFromQualifiedName();
            if (from == null || !csFqns.contains(from)) {
                continue;
            }
            String to = resolveTargetFqn(e, csFqns);
            if (to == null || !csFqns.contains(to) || from.equals(to)) {
                continue;
            }
            String key = from + "\0" + to + "\0" + edgeLabel(e);
            if (!seenEdge.add(key)) {
                continue;
            }
            String aFrom = aliasByFqn.get(from);
            String aTo = aliasByFqn.get(to);
            edgeLines.add("  " + aFrom + " --> " + aTo + " : " + sanitizeEdgeLabel(edgeLabel(e)));
        }

        StringBuilder sb = new StringBuilder(512);
        sb.append("@startuml\n");
        sb.append("skinparam shadowing false\n");
        sb.append("skinparam linetype ortho\n");
        sb.append("left to right direction\n");
        sb.append("title Spring controllers and services (dependency injection)\n");

        sb.append("package Controllers {\n");
        for (String fqn : csFqns) {
            SpringComponent sc = byFqn.get(fqn);
            if (sc == null || !isController(sc)) {
                continue;
            }
            sb.append("  component \"")
                .append(simpleName(fqn))
                .append("\" as ")
                .append(aliasByFqn.get(fqn))
                .append("\n");
        }
        sb.append("}\n");

        sb.append("package Services {\n");
        for (String fqn : csFqns) {
            SpringComponent sc = byFqn.get(fqn);
            if (sc == null || !isService(sc) || isController(sc)) {
                continue;
            }
            sb.append("  component \"")
                .append(simpleName(fqn))
                .append("\" as ")
                .append(aliasByFqn.get(fqn))
                .append("\n");
        }
        sb.append("}\n");

        if (!edgeLines.isEmpty()) {
            sb.append("\n");
            for (String line : edgeLines) {
                sb.append(line).append('\n');
            }
        }

        sb.append("note bottom\n");
        sb.append("  Full qualified names (scan order).\n");
        for (String fqn : csFqns) {
            sb.append("  * ").append(fqn).append('\n');
        }
        sb.append("end note\n");
        sb.append("@enduml\n");
        return sb.toString();
    }

    private static boolean isController(SpringComponent c) {
        Set<String> s = c.getStereotypes();
        return s.contains("Controller") || s.contains("RestController");
    }

    private static boolean isService(SpringComponent c) {
        return c.getStereotypes().contains("Service");
    }

    private static String simpleName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? fqn : fqn.substring(dot + 1);
    }

    /** Resolves injection target to an FQN that is in {@code csFqns}, if possible. */
    private static String resolveTargetFqn(InjectionEdge e, Set<String> csFqns) {
        if (e.getToQualifiedName() != null && csFqns.contains(e.getToQualifiedName())) {
            return e.getToQualifiedName();
        }
        String simple = e.getToTypeSimpleName();
        if (simple == null || simple.isBlank()) {
            return null;
        }
        List<String> hits = new ArrayList<>();
        for (String fqn : csFqns) {
            if (simpleName(fqn).equals(simple)) {
                hits.add(fqn);
            }
        }
        if (hits.size() == 1) {
            return hits.get(0);
        }
        return null;
    }

    private static String edgeLabel(InjectionEdge e) {
        String k = e.getKind() != null ? e.getKind().name().toLowerCase() : "inject";
        if (e.getQualifier() != null && !e.getQualifier().isBlank()) {
            return k + " @Qualifier(" + e.getQualifier() + ")";
        }
        return k;
    }

    private static String sanitizeEdgeLabel(String label) {
        String t = label.replace('\n', ' ').replace('\r', ' ');
        if (t.contains("\"") || t.contains("]")) {
            t = t.replace("\"", "'");
        }
        return t;
    }
}

package com.example.analyzer.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * In-memory model of a Java project: packages and types with relationships.
 */
public class ProjectModel {

    private final Map<String, PackageInfo> packages = new LinkedHashMap<>();
    private final Map<String, TypeInfo> typesByQualifiedName = new LinkedHashMap<>();
    /**
     * Source package → packages of types referenced by {@code import} / {@code import static} in that package's
     * compilation units (in-model types only).
     */
    private final Map<String, Set<String>> packageImportDependencies = new LinkedHashMap<>();
    private String projectRoot;

    public Map<String, PackageInfo> getPackages() { return packages; }

    public Map<String, TypeInfo> getTypesByQualifiedName() { return typesByQualifiedName; }

    public Map<String, Set<String>> getPackageImportDependencies() {
        return packageImportDependencies;
    }

    /** Declares that types in {@code fromPackage} import a type living in {@code toPackage}. */
    public void addPackageImportDependency(String fromPackage, String toPackage) {
        if (toPackage == null) {
            return;
        }
        String from = fromPackage != null ? fromPackage : "";
        if (from.equals(toPackage)) {
            return;
        }
        packageImportDependencies.computeIfAbsent(from, k -> new TreeSet<>()).add(toPackage);
    }

    public String getProjectRoot() { return projectRoot; }
    public void setProjectRoot(String projectRoot) { this.projectRoot = projectRoot; }

    public TypeInfo getType(String qualifiedName) {
        return typesByQualifiedName.get(qualifiedName);
    }

    public List<TypeInfo> getTypesInPackage(String packageName) {
        List<TypeInfo> out = new ArrayList<>();
        for (TypeInfo t : typesByQualifiedName.values()) {
            if (packageName.equals(t.getPackageName())) out.add(t);
        }
        return out;
    }

    public List<TypeInfo> getTypesWithAnnotation(String annotationSimpleName) {
        List<TypeInfo> out = new ArrayList<>();
        for (TypeInfo t : typesByQualifiedName.values()) {
            if (t.hasAnnotation(annotationSimpleName)) out.add(t);
        }
        return out;
    }

    /** All types whose simple name matches (for import / field resolution). */
    public List<TypeInfo> getTypesBySimpleName(String simpleName) {
        List<TypeInfo> out = new ArrayList<>();
        if (simpleName == null || simpleName.isEmpty()) {
            return out;
        }
        for (TypeInfo t : typesByQualifiedName.values()) {
            if (simpleName.equals(t.getSimpleName())) {
                out.add(t);
            }
        }
        return out;
    }
}

package com.example.analyzer.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory model of a Java project: packages and types with relationships.
 */
public class ProjectModel {

    private final Map<String, PackageInfo> packages = new LinkedHashMap<>();
    private final Map<String, TypeInfo> typesByQualifiedName = new LinkedHashMap<>();
    private String projectRoot;

    public Map<String, PackageInfo> getPackages() { return packages; }

    public Map<String, TypeInfo> getTypesByQualifiedName() { return typesByQualifiedName; }

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

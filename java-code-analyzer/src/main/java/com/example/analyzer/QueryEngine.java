package com.example.analyzer;

import com.example.analyzer.model.MethodInfo;
import com.example.analyzer.model.ProjectModel;
import com.example.analyzer.model.TypeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Answers questions about the project model: packages, types, controllers, services, etc.
 */
public class QueryEngine {

    private final ProjectModel model;

    public QueryEngine(ProjectModel model) {
        this.model = model;
    }

    /** All package names. */
    public List<String> listPackages() {
        return model.getPackages().keySet().stream()
            .filter(p -> !p.isEmpty())
            .sorted()
            .collect(Collectors.toList());
    }

    /** All types (classes/interfaces) in a package. */
    public List<TypeInfo> listTypesInPackage(String packageName) {
        return model.getTypesInPackage(packageName);
    }

    /** All types with a given stereotype annotation (e.g. Controller, Service, Entity). */
    public List<TypeInfo> listByAnnotation(String annotationSimpleName) {
        return model.getTypesWithAnnotation(annotationSimpleName);
    }

    public List<TypeInfo> listControllers() { return listByAnnotation("Controller"); }
    public List<TypeInfo> listRestControllers() { return listByAnnotation("RestController"); }
    public List<TypeInfo> listServices() { return listByAnnotation("Service"); }
    public List<TypeInfo> listRepositories() { return listByAnnotation("Repository"); }
    public List<TypeInfo> listEntities() { return listByAnnotation("Entity"); }
    public List<TypeInfo> listComponents() { return listByAnnotation("Component"); }
    public List<TypeInfo> listConfigurations() { return listByAnnotation("Configuration"); }

    /** All interfaces. */
    public List<TypeInfo> listInterfaces() {
        return model.getTypesByQualifiedName().values().stream()
            .filter(t -> t.getKind() == TypeInfo.Kind.INTERFACE)
            .collect(Collectors.toList());
    }

    /** All classes (excluding interfaces and enums). */
    public List<TypeInfo> listClasses() {
        return model.getTypesByQualifiedName().values().stream()
            .filter(t -> t.getKind() == TypeInfo.Kind.CLASS)
            .collect(Collectors.toList());
    }

    public TypeInfo getType(String qualifiedName) {
        return model.getType(qualifiedName);
    }

    /** All public methods of types in the given package. */
    public List<MethodInfo> listPublicMethodsInPackage(String packageName) {
        List<MethodInfo> out = new ArrayList<>();
        for (TypeInfo t : model.getTypesInPackage(packageName)) {
            out.addAll(t.getPublicMethods());
        }
        return out;
    }

    /** All public methods of the given class or interface (by fully qualified name). */
    public List<MethodInfo> listPublicMethodsInClass(String qualifiedTypeName) {
        TypeInfo t = model.getType(qualifiedTypeName);
        return t == null ? List.of() : new ArrayList<>(t.getPublicMethods());
    }

    /**
     * Package dependencies: for each package, the set of other packages it uses (from field types, extends, implements).
     * Only includes types that exist in the project. Sorted by package name.
     */
    public Map<String, Set<String>> getPackageDependencies() {
        TreeMap<String, Set<String>> deps = new TreeMap<>();
        for (TypeInfo from : model.getTypesByQualifiedName().values()) {
            String fromPkg = from.getPackageName() != null ? from.getPackageName() : "";
            Set<String> used = deps.computeIfAbsent(fromPkg, k -> new TreeSet<>());
            addUsedPackages(from, fromPkg, used);
        }
        return deps;
    }

    private void addUsedPackages(TypeInfo from, String fromPkg, Set<String> used) {
        String pkg = from.getPackageName() != null ? from.getPackageName() : "";
        if (from.getExtendsType() != null) {
            TypeInfo t = model.getType(resolveQualified(from.getExtendsType(), pkg));
            if (t != null) addPackageIfDifferent(t.getPackageName(), fromPkg, used);
        }
        for (String impl : from.getImplementsTypes()) {
            TypeInfo t = model.getType(resolveQualified(impl, pkg));
            if (t != null) addPackageIfDifferent(t.getPackageName(), fromPkg, used);
        }
        for (String fieldType : from.getFieldTypes()) {
            TypeInfo t = model.getType(resolveQualified(fieldType, pkg));
            if (t != null) addPackageIfDifferent(t.getPackageName(), fromPkg, used);
        }
    }

    private static void addPackageIfDifferent(String usedPkg, String fromPkg, Set<String> used) {
        if (usedPkg == null) return;
        String p = usedPkg.isEmpty() ? "(default package)" : usedPkg;
        String f = fromPkg.isEmpty() ? "(default package)" : fromPkg;
        if (!p.equals(f)) used.add(usedPkg);
    }

    private static String resolveQualified(String simpleOrQualified, String currentPackage) {
        if (simpleOrQualified == null || simpleOrQualified.isEmpty()) return simpleOrQualified;
        if (simpleOrQualified.contains(".")) return simpleOrQualified;
        return currentPackage.isEmpty() ? simpleOrQualified : currentPackage + "." + simpleOrQualified;
    }
}

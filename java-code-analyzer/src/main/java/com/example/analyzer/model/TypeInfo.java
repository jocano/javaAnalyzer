package com.example.analyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Metadata for a Java type (class, interface, enum) extracted from source.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TypeInfo {

    public enum Kind { CLASS, INTERFACE, ENUM }

    private String packageName;
    private String simpleName;
    private String qualifiedName;
    private Kind kind;
    private final List<String> annotations = new ArrayList<>();
    private String extendsType;
    private final List<String> implementsTypes = new ArrayList<>();
    private final List<String> fieldTypes = new ArrayList<>();
    /** Field / component name → dependency simple type name (generics stripped). */
    private final Map<String, String> fieldsByName = new LinkedHashMap<>();
    private final List<MethodInfo> publicMethods = new ArrayList<>();
    private final List<MethodInfo> protectedMethods = new ArrayList<>();
    private String sourcePath;
    private int lineNumber;

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getSimpleName() { return simpleName; }
    public void setSimpleName(String simpleName) { this.simpleName = simpleName; }

    public String getQualifiedName() { return qualifiedName; }
    public void setQualifiedName(String qualifiedName) { this.qualifiedName = qualifiedName; }

    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }

    public List<String> getAnnotations() { return annotations; }

    public String getExtendsType() { return extendsType; }
    public void setExtendsType(String extendsType) { this.extendsType = extendsType; }

    public List<String> getImplementsTypes() { return implementsTypes; }

    public List<String> getFieldTypes() { return fieldTypes; }

    public Map<String, String> getFieldsByName() { return fieldsByName; }

    public List<MethodInfo> getPublicMethods() { return publicMethods; }

    public List<MethodInfo> getProtectedMethods() { return protectedMethods; }

    /** True if a public or protected method with this name exists (sequence tracing; private excluded). */
    public boolean hasSequenceTraceableMethod(String methodName) {
        for (MethodInfo m : publicMethods) {
            if (methodName.equals(m.getName())) {
                return true;
            }
        }
        for (MethodInfo m : protectedMethods) {
            if (methodName.equals(m.getName())) {
                return true;
            }
        }
        return false;
    }

    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }

    /** 1-based line in source file (for IDE links). */
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

    /** Format for IntelliJ Terminal: file:///path/to/file.java:line (clickable on macOS and Windows). */
    @JsonIgnore
    public String getSourceLocation() {
        if (sourcePath == null || lineNumber < 1) return sourcePath != null ? sourcePath : "";
        try {
            String uri = Paths.get(sourcePath).toUri().toString();
            return uri + ":" + lineNumber;
        } catch (Exception e) {
            return sourcePath + ":" + lineNumber;
        }
    }

    public boolean hasAnnotation(String name) {
        String simple = name.startsWith("@") ? name.substring(1) : name;
        for (String a : annotations) {
            if (a.endsWith("." + simple) || a.equals(simple)) return true;
        }
        return false;
    }
}

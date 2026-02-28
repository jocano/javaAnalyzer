package com.example.analyzer.model;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadata for a Java type (class, interface, enum) extracted from source.
 */
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
    private final List<MethodInfo> publicMethods = new ArrayList<>();
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

    public List<MethodInfo> getPublicMethods() { return publicMethods; }

    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }

    /** 1-based line in source file (for IDE links). */
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

    /** Format for IntelliJ Terminal: file:///path/to/file.java:line (clickable on macOS and Windows). */
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

package com.example.analyzer.model;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadata for a public method: declaring type, name, return type, parameter types.
 */
public class MethodInfo {

    private String declaringTypeQualifiedName;
    private String name;
    private String returnType;
    private final List<String> parameterTypes = new ArrayList<>();
    private String sourcePath;
    private int lineNumber;

    public String getDeclaringTypeQualifiedName() { return declaringTypeQualifiedName; }
    public void setDeclaringTypeQualifiedName(String declaringTypeQualifiedName) { this.declaringTypeQualifiedName = declaringTypeQualifiedName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }

    public List<String> getParameterTypes() { return parameterTypes; }

    /** e.g. "UserService" or "com.example.app.UserService" */
    public String getDeclaringTypeSimpleName() {
        if (declaringTypeQualifiedName == null) return "";
        int dot = declaringTypeQualifiedName.lastIndexOf('.');
        return dot < 0 ? declaringTypeQualifiedName : declaringTypeQualifiedName.substring(dot + 1);
    }

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

    /** Human-readable signature: name(paramTypes) : returnType */
    public String getSignature() {
        String params = String.join(", ", parameterTypes);
        return name + "(" + params + ") : " + (returnType != null ? returnType : "void");
    }
}

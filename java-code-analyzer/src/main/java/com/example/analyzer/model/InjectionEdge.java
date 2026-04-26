package com.example.analyzer.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A wiring edge: a Spring component depends on another type via DI.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InjectionEdge {

    public enum Kind {
        CONSTRUCTOR,
        FIELD,
        SETTER
    }

    public InjectionEdge() {
    }

    private String fromQualifiedName;
    /** Resolved dependency FQN when unique in the project index; may be null. */
    private String toQualifiedName;
    /** Raw simple name when {@link #toQualifiedName} could not be resolved. */
    private String toTypeSimpleName;
    private Kind kind;
    /** Optional {@code @Qualifier} value. */
    private String qualifier;

    public String getFromQualifiedName() {
        return fromQualifiedName;
    }

    public void setFromQualifiedName(String fromQualifiedName) {
        this.fromQualifiedName = fromQualifiedName;
    }

    public String getToQualifiedName() {
        return toQualifiedName;
    }

    public void setToQualifiedName(String toQualifiedName) {
        this.toQualifiedName = toQualifiedName;
    }

    public String getToTypeSimpleName() {
        return toTypeSimpleName;
    }

    public void setToTypeSimpleName(String toTypeSimpleName) {
        this.toTypeSimpleName = toTypeSimpleName;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public String getQualifier() {
        return qualifier;
    }

    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }
}

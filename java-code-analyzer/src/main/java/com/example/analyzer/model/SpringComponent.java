package com.example.analyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A Spring-managed type discovered from stereotype annotations on the declaration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpringComponent {

    public SpringComponent() {
    }

    private String qualifiedName;
    /** Short stereotype names, e.g. RestController, Service, Configuration. */
    private Set<String> stereotypes = new LinkedHashSet<>();
    private String sourcePath;
    private int lineNumber;

    public String getQualifiedName() {
        return qualifiedName;
    }

    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public Set<String> getStereotypes() {
        return stereotypes;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public void setStereotypes(Set<String> stereotypes) {
        this.stereotypes = stereotypes != null ? stereotypes : new LinkedHashSet<>();
    }
}

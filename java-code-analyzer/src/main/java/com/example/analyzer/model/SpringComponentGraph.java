package com.example.analyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring stereotype components and injection wiring for a scanned project.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpringComponentGraph {

    private String projectRoot;
    private long analyzedAtMillis = System.currentTimeMillis();
    private List<SpringComponent> components = new ArrayList<>();
    private List<InjectionEdge> injectionEdges = new ArrayList<>();

    public SpringComponentGraph() {
    }

    public String getProjectRoot() {
        return projectRoot;
    }

    public void setProjectRoot(String projectRoot) {
        this.projectRoot = projectRoot;
    }

    public long getAnalyzedAtMillis() {
        return analyzedAtMillis;
    }

    public void setAnalyzedAtMillis(long analyzedAtMillis) {
        this.analyzedAtMillis = analyzedAtMillis;
    }

    public List<SpringComponent> getComponents() {
        return components;
    }

    public void setComponents(List<SpringComponent> components) {
        this.components = components != null ? components : new ArrayList<>();
    }

    public List<InjectionEdge> getInjectionEdges() {
        return injectionEdges;
    }

    public void setInjectionEdges(List<InjectionEdge> injectionEdges) {
        this.injectionEdges = injectionEdges != null ? injectionEdges : new ArrayList<>();
    }
}

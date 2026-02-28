package com.example.analyzer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata for a Java package: name and types it contains.
 */
public class PackageInfo {

    private String name;
    private final List<String> typeQualifiedNames = new ArrayList<>();

    public PackageInfo(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getTypeQualifiedNames() { return typeQualifiedNames; }
}

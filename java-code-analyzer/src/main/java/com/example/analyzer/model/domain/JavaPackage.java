package com.example.analyzer.model.domain;

import java.util.List;

/**
 * A Java package and the types it contains (by qualified name).
 *
 * @param qualifiedName      Dot-separated package name (e.g. {@code com.example.service}).
 * @param typeQualifiedNames Qualified names of all types declared in this package.
 */
public record JavaPackage(
        String       qualifiedName,
        List<String> typeQualifiedNames
) {}

package com.example.analyzer.model.domain;

/**
 * A field declared on a {@link JavaType}.
 *
 * <p>{@code typeName} may be a simple name (generics stripped) when the full qualified name
 * could not be resolved during analysis.
 *
 * @param name     Field identifier.
 * @param typeName Declared type (may be simple or fully qualified).
 */
public record JavaField(
        String name,
        String typeName
) {}

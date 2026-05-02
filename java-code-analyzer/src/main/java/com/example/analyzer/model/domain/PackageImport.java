package com.example.analyzer.model.domain;

/**
 * A compile-time import dependency between two packages.
 *
 * <p>Derived from {@code import} statements: types in {@code fromPackage} reference
 * at least one type from {@code toPackage}.
 *
 * @param fromPackage Qualified name of the depending package.
 * @param toPackage   Qualified name of the depended-upon package.
 */
public record PackageImport(
        String fromPackage,
        String toPackage
) {}

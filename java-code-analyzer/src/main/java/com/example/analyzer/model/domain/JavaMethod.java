package com.example.analyzer.model.domain;

import java.util.List;

/**
 * A method declared on a {@link JavaType}.
 *
 * @param name               Method identifier.
 * @param visibility         Access modifier.
 * @param returnTypeName     Return type (simple or fully qualified; {@code "void"} for void methods).
 * @param parameterTypeNames Ordered list of parameter types (simple or fully qualified).
 * @param sourcePath         Absolute path to the source file, or {@code null} if unavailable.
 * @param lineNumber         1-based line of the method declaration, or {@code 0} if unknown.
 */
public record JavaMethod(
        String       name,
        Visibility   visibility,
        String       returnTypeName,
        List<String> parameterTypeNames,
        String       sourcePath,
        int          lineNumber
) {}

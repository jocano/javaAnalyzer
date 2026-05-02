package com.example.analyzer.model.domain;

import java.util.Set;

/**
 * A Spring-managed component discovered through stereotype annotations.
 *
 * <p>{@code typeQualifiedName} is a foreign key into {@link CodeModel#types()}; the full
 * structural metadata lives there. This record carries only the Spring-specific overlay.
 *
 * @param typeQualifiedName Qualified name of the underlying {@link JavaType}.
 * @param stereotypes       Short stereotype names, e.g. {@code "Service"}, {@code "RestController"}.
 * @param sourcePath        Absolute source path (denormalised for convenience), or {@code null}.
 * @param lineNumber        1-based declaration line, or {@code 0} if unknown.
 */
public record SpringBean(
        String      typeQualifiedName,
        Set<String> stereotypes,
        String      sourcePath,
        int         lineNumber
) {}

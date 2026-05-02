package com.example.analyzer.model.domain;

import java.util.List;

/**
 * Root aggregate of a Java/Spring project analysis snapshot.
 *
 * <p>All cross-references between records (type names, package names) are by
 * {@code String} key so that the model is self-contained and serialisable to any
 * format (JSON, SQL rows, graph nodes) without circular object references.
 *
 * <p>The two layers are independent: {@code springContext} may be empty (or null)
 * for pure structural analysis; {@code types} and {@code packages} are always present.
 *
 * @param project        Provenance of this snapshot (who, when, format).
 * @param packages       All Java packages discovered in the project.
 * @param types          All Java types discovered in the project.
 * @param packageImports Compile-time import dependencies between packages.
 * @param springContext  Spring DI graph overlay; {@code null} when not analysed.
 */
public record CodeModel(
        JavaProject         project,
        List<JavaPackage>   packages,
        List<JavaType>      types,
        List<PackageImport> packageImports,
        SpringContext       springContext
) {}

package com.example.analyzer.model.domain;

/**
 * A wiring edge: one Spring bean depends on another type via dependency injection.
 *
 * <p>When the dependency could be resolved to a type present in the project model,
 * {@code toQualifiedName} is set. Otherwise {@code toTypeSimpleName} holds the raw
 * declared type name (e.g. a framework type such as {@code ObservationRegistry}).
 * Exactly one of the two is expected to be non-null.
 *
 * @param fromQualifiedName  Qualified name of the injecting {@link SpringBean}.
 * @param toQualifiedName    Resolved qualified name of the injected dependency, or {@code null}.
 * @param toTypeSimpleName   Simple/raw type name when {@code toQualifiedName} is unresolved.
 * @param kind               Injection mechanism.
 * @param qualifier          Value of {@code @Qualifier}, or {@code null} if absent.
 */
public record BeanInjection(
        String        fromQualifiedName,
        String        toQualifiedName,
        String        toTypeSimpleName,
        InjectionKind kind,
        String        qualifier
) {}

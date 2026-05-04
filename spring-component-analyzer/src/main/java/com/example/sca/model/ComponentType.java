package com.example.sca.model;

/**
 * Stereotype/role of a Java type inside a Spring application.
 * Derived from annotations present on the type declaration.
 */
public enum ComponentType {
    /** @RestController or @Controller */
    CONTROLLER,
    /** @Service */
    SERVICE,
    /** @Repository, or extends JpaRepository / CrudRepository */
    REPOSITORY,
    /** @Entity */
    ENTITY,
    /** @Component (not mapped to a more specific stereotype) */
    COMPONENT,
    /** @Configuration or @SpringBootApplication */
    CONFIGURATION,
    /** @FeignClient or similar declarative HTTP client */
    REST_CLIENT,
    /** @Aspect */
    ASPECT,
    /** Plain Java interface (no Spring annotation) */
    INTERFACE,
    /** Enum declaration */
    ENUM,
    /** Any other class not matching the above */
    OTHER
}

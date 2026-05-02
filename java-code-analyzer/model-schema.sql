-- =============================================================================
-- Java/Spring Code Model — Relational Schema
--
-- Implements the abstract model defined in:
--   model-abstract.puml
--   src/main/java/com/example/analyzer/model/domain/
--
-- Dialect: ANSI SQL (compatible with PostgreSQL, H2, SQLite with minor tweaks).
-- Primary key strategy: natural keys where stable (qualified names);
--   surrogate BIGSERIAL for ordered/positional child rows.
-- All cross-type references that may point to *external* types (not present
--   in the project) are stored as VARCHAR rather than FK to avoid orphan rows.
-- =============================================================================


-- ─────────────────────────────────────────────────────────────────────────────
-- PROJECT
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE project (
    project_root    VARCHAR(2000) NOT NULL,
    saved_at_millis BIGINT        NOT NULL,
    format_version  INT           NOT NULL DEFAULT 1,
    PRIMARY KEY (project_root)
);


-- ─────────────────────────────────────────────────────────────────────────────
-- STRUCTURAL LAYER
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE package (
    qualified_name VARCHAR(500)  NOT NULL,
    project_root   VARCHAR(2000) NOT NULL,
    PRIMARY KEY (qualified_name, project_root),
    FOREIGN KEY (project_root) REFERENCES project (project_root) ON DELETE CASCADE
);

CREATE TABLE type (
    qualified_name  VARCHAR(500)  NOT NULL,
    simple_name     VARCHAR(200)  NOT NULL,
    package_name    VARCHAR(500)  NOT NULL DEFAULT '',
    project_root    VARCHAR(2000) NOT NULL,
    -- TypeKind enum
    kind            VARCHAR(20)   NOT NULL
        CHECK (kind IN ('CLASS','INTERFACE','ENUM','ANNOTATION','RECORD')),
    -- nullable: raw name (simple or qualified) of the supertype; may be external
    extends_type    VARCHAR(500),
    source_path     VARCHAR(2000),
    line_number     INT,
    PRIMARY KEY (qualified_name, project_root),
    FOREIGN KEY (project_root)                REFERENCES project (project_root) ON DELETE CASCADE,
    FOREIGN KEY (package_name, project_root)  REFERENCES package (qualified_name, project_root)
);

-- One row per annotation applied to a type declaration (e.g. 'Service', 'RestController')
CREATE TABLE type_annotation (
    type_qualified_name VARCHAR(500)  NOT NULL,
    project_root        VARCHAR(2000) NOT NULL,
    annotation_name     VARCHAR(200)  NOT NULL,
    PRIMARY KEY (type_qualified_name, project_root, annotation_name),
    FOREIGN KEY (type_qualified_name, project_root) REFERENCES type (qualified_name, project_root) ON DELETE CASCADE
);

-- One row per interface a type declares it implements
CREATE TABLE type_implements (
    type_qualified_name  VARCHAR(500)  NOT NULL,
    project_root         VARCHAR(2000) NOT NULL,
    interface_name       VARCHAR(500)  NOT NULL,   -- may be simple name if unresolved
    PRIMARY KEY (type_qualified_name, project_root, interface_name),
    FOREIGN KEY (type_qualified_name, project_root) REFERENCES type (qualified_name, project_root) ON DELETE CASCADE
);

CREATE TABLE field (
    id             BIGSERIAL     PRIMARY KEY,
    declaring_type VARCHAR(500)  NOT NULL,
    project_root   VARCHAR(2000) NOT NULL,
    name           VARCHAR(200)  NOT NULL,
    type_name      VARCHAR(500)  NOT NULL,   -- simple or qualified; may be external
    FOREIGN KEY (declaring_type, project_root) REFERENCES type (qualified_name, project_root) ON DELETE CASCADE
);

CREATE TABLE method (
    id               BIGSERIAL     PRIMARY KEY,
    declaring_type   VARCHAR(500)  NOT NULL,
    project_root     VARCHAR(2000) NOT NULL,
    name             VARCHAR(200)  NOT NULL,
    -- Visibility enum
    visibility       VARCHAR(20)   NOT NULL
        CHECK (visibility IN ('PUBLIC','PROTECTED','PACKAGE_PRIVATE','PRIVATE')),
    return_type_name VARCHAR(500),
    source_path      VARCHAR(2000),
    line_number      INT,
    FOREIGN KEY (declaring_type, project_root) REFERENCES type (qualified_name, project_root) ON DELETE CASCADE
);

CREATE TABLE method_parameter (
    method_id BIGINT       NOT NULL,
    position  INT          NOT NULL,   -- 0-based order
    type_name VARCHAR(500) NOT NULL,
    PRIMARY KEY (method_id, position),
    FOREIGN KEY (method_id) REFERENCES method (id) ON DELETE CASCADE
);

-- Compile-time import dependency between two packages within the same project
CREATE TABLE package_import (
    from_package VARCHAR(500)  NOT NULL,
    to_package   VARCHAR(500)  NOT NULL,
    project_root VARCHAR(2000) NOT NULL,
    PRIMARY KEY (from_package, to_package, project_root),
    FOREIGN KEY (from_package, project_root) REFERENCES package (qualified_name, project_root) ON DELETE CASCADE,
    FOREIGN KEY (to_package,   project_root) REFERENCES package (qualified_name, project_root) ON DELETE CASCADE
);


-- ─────────────────────────────────────────────────────────────────────────────
-- SPRING LAYER
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE spring_bean (
    type_qualified_name VARCHAR(500)  NOT NULL,
    project_root        VARCHAR(2000) NOT NULL,
    analyzed_at_millis  BIGINT,
    source_path         VARCHAR(2000),
    line_number         INT,
    PRIMARY KEY (type_qualified_name, project_root),
    -- A bean must correspond to a known type in the structural layer
    FOREIGN KEY (type_qualified_name, project_root) REFERENCES type (qualified_name, project_root) ON DELETE CASCADE
);

-- One row per stereotype on a bean (e.g. 'Service', 'RestController', 'Configuration')
CREATE TABLE spring_bean_stereotype (
    type_qualified_name VARCHAR(500)  NOT NULL,
    project_root        VARCHAR(2000) NOT NULL,
    stereotype          VARCHAR(100)  NOT NULL,
    PRIMARY KEY (type_qualified_name, project_root, stereotype),
    FOREIGN KEY (type_qualified_name, project_root) REFERENCES spring_bean (type_qualified_name, project_root) ON DELETE CASCADE
);

-- Wiring edge: one bean injects a dependency
CREATE TABLE bean_injection (
    id                    BIGSERIAL     PRIMARY KEY,
    from_qualified_name   VARCHAR(500)  NOT NULL,
    project_root          VARCHAR(2000) NOT NULL,
    -- Resolved FQN of the injected type (null when external / unresolved)
    to_qualified_name     VARCHAR(500),
    -- Raw simple name when to_qualified_name is null
    to_type_simple_name   VARCHAR(200),
    -- InjectionKind enum
    kind                  VARCHAR(20)   NOT NULL
        CHECK (kind IN ('CONSTRUCTOR','FIELD','SETTER')),
    -- @Qualifier value, null when absent
    qualifier             VARCHAR(200),
    FOREIGN KEY (from_qualified_name, project_root) REFERENCES spring_bean (type_qualified_name, project_root) ON DELETE CASCADE,
    -- to_qualified_name may be external; soft reference only
    CHECK (to_qualified_name IS NOT NULL OR to_type_simple_name IS NOT NULL)
);


-- ─────────────────────────────────────────────────────────────────────────────
-- USEFUL INDEXES
-- ─────────────────────────────────────────────────────────────────────────────

CREATE INDEX idx_type_package          ON type           (package_name, project_root);
CREATE INDEX idx_type_kind             ON type           (kind, project_root);
CREATE INDEX idx_type_annotation_name  ON type_annotation(annotation_name, project_root);
CREATE INDEX idx_field_type_name       ON field          (type_name);
CREATE INDEX idx_method_name           ON method         (name, project_root);
CREATE INDEX idx_bean_stereotype       ON spring_bean_stereotype(stereotype, project_root);
CREATE INDEX idx_injection_to_fqn      ON bean_injection (to_qualified_name, project_root);
CREATE INDEX idx_injection_from        ON bean_injection (from_qualified_name, project_root);

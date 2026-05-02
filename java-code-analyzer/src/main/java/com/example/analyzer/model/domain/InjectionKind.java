package com.example.analyzer.model.domain;

/** Mechanism by which one Spring bean receives another as a dependency. */
public enum InjectionKind {
    CONSTRUCTOR,
    FIELD,
    SETTER
}

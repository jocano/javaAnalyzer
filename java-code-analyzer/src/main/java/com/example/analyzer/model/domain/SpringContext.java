package com.example.analyzer.model.domain;

import java.util.List;

/**
 * The Spring application context graph: all beans and their injection relationships.
 *
 * @param projectRoot      Absolute root path of the analysed project.
 * @param analyzedAtMillis Epoch-millis when the Spring graph was built.
 * @param beans            All Spring-managed components found in the project.
 * @param injections       All dependency-injection edges between beans.
 */
public record SpringContext(
        String             projectRoot,
        long               analyzedAtMillis,
        List<SpringBean>   beans,
        List<BeanInjection> injections
) {}

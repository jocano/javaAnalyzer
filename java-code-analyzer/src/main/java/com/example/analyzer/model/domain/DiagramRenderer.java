package com.example.analyzer.model.domain;

/**
 * Port: diagram rendering back-end for a {@link CodeModel} snapshot.
 *
 * <p>Concrete implementations may produce PlantUML source, Mermaid markup,
 * DOT/Graphviz notation, or any other textual diagram format.
 */
public interface DiagramRenderer {

    /**
     * Render the entire model as a diagram.
     *
     * @param model The code model to visualise.
     * @return Diagram source text (format depends on the implementation).
     */
    String render(CodeModel model);
}

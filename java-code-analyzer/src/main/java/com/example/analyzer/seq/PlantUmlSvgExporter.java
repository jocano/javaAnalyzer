package com.example.analyzer.seq;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs the {@code plantuml} CLI to render SVG. Requires {@code plantuml} on {@code PATH}.
 */
public final class PlantUmlSvgExporter {

    private PlantUmlSvgExporter() {}

    /**
     * Writes PlantUML source to a temp file, runs {@code plantuml -tsvg}, moves the SVG to {@code targetSvg}.
     *
     * @return the path to the written SVG
     */
    public static Path exportSvg(String plantUml, Path targetSvg) throws IOException, InterruptedException {
        Path parent = targetSvg.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempDir = Files.createTempDirectory("java-analyzer-puml");
        Path tempPuml = tempDir.resolve("diagram.puml");
        try {
            Files.writeString(tempPuml, plantUml);
            List<String> cmd = new ArrayList<>();
            cmd.add("plantuml");
            cmd.add("-tsvg");
            cmd.add(tempPuml.toAbsolutePath().toString());
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new IOException("plantuml timed out");
            }
            if (p.exitValue() != 0) {
                String out = new String(p.getInputStream().readAllBytes());
                throw new IOException("plantuml exited with " + p.exitValue() + (out.isBlank() ? "" : (": " + out)));
            }
            Path generated = tempDir.resolve("diagram.svg");
            if (!Files.isRegularFile(generated)) {
                throw new IOException("Expected SVG at " + generated);
            }
            Files.move(generated, targetSvg, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return targetSvg;
        } finally {
            try {
                Files.deleteIfExists(tempPuml);
                Files.deleteIfExists(tempDir.resolve("diagram.svg"));
                Files.deleteIfExists(tempDir);
            } catch (IOException ignored) {
                // best effort cleanup
            }
        }
    }
}

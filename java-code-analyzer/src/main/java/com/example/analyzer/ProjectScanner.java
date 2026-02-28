package com.example.analyzer;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds all .java files under a project root (optionally excluding build dirs).
 */
public class ProjectScanner {

    private static final String[] DEFAULT_EXCLUDES = {
        "target", "build", "out", ".git", "node_modules"
    };

    public static List<Path> findJavaFiles(Path projectRoot, String... excludeDirs) throws IOException {
        List<Path> result = new ArrayList<>();
        String[] excludes = excludeDirs.length > 0 ? excludeDirs : DEFAULT_EXCLUDES;

        Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName().toString();
                for (String ex : excludes) {
                    if (ex.equals(name)) return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().endsWith(".java")) {
                    result.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }
}

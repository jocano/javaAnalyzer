package com.example.springmvccontroller.util;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Simple Java program that writes error messages in the Java compiler error format.
 * 
 * Standard format: filename.java:line: error: message
 * 
 * For IDE clickable errors, use full paths or relative paths from project root.
 */
public class CompilerErrorWriter {
    
    private PrintStream output;
    private Path projectRoot;
    
    public CompilerErrorWriter() {
        this(System.err); // Default to stderr like real compiler
        this.projectRoot = Paths.get(System.getProperty("user.dir"));
    }
    
    public CompilerErrorWriter(PrintStream output) {
        this.output = output;
        this.projectRoot = Paths.get(System.getProperty("user.dir"));
    }
    
    public CompilerErrorWriter(PrintStream output, Path projectRoot) {
        this.output = output;
        this.projectRoot = projectRoot != null ? projectRoot : Paths.get(System.getProperty("user.dir"));
    }
    
    /**
     * Converts a file path to a relative path from project root for IDE compatibility.
     * If the path is already relative or can't be resolved, returns it as-is.
     */
    private String normalizePath(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (path.isAbsolute()) {
                // Try to make it relative to project root
                if (path.startsWith(projectRoot)) {
                    return projectRoot.relativize(path).toString().replace('\\', '/');
                }
                return filePath;
            }
            // Already relative, return as-is
            return filePath.replace('\\', '/');
        } catch (Exception e) {
            // If path resolution fails, return original
            return filePath.replace('\\', '/');
        }
    }
    
    /**
     * Writes an error message in compiler format.
     * 
     * @param fileName The source file name (use full path or relative path from project root for IDE clickability)
     * @param line The line number
     * @param message The error message
     */
    public void writeError(String fileName, int line, String message) {
        String normalizedPath = normalizePath(fileName);
        output.println(String.format("%s:%d: error: %s", normalizedPath, line, message));
    }
    
    /**
     * Writes an error message using a Path object (automatically converts to relative path).
     * 
     * @param filePath The source file path
     * @param line The line number
     * @param message The error message
     */
    public void writeError(Path filePath, int line, String message) {
        writeError(filePath.toString(), line, message);
    }
    
    /**
     * Writes a warning message in compiler format.
     * 
     * @param fileName The source file name (use full path or relative path from project root for IDE clickability)
     * @param line The line number
     * @param message The warning message
     */
    public void writeWarning(String fileName, int line, String message) {
        String normalizedPath = normalizePath(fileName);
        output.println(String.format("%s:%d: warning: %s", normalizedPath, line, message));
    }
    
    /**
     * Writes a warning message using a Path object.
     */
    public void writeWarning(Path filePath, int line, String message) {
        writeWarning(filePath.toString(), line, message);
    }
    
    /**
     * Writes an error with column position.
     * 
     * @param fileName The source file name (use full path or relative path from project root for IDE clickability)
     * @param line The line number
     * @param column The column number
     * @param message The error message
     */
    public void writeError(String fileName, int line, int column, String message) {
        String normalizedPath = normalizePath(fileName);
        output.println(String.format("%s:%d:%d: error: %s", normalizedPath, line, column, message));
    }
    
    /**
     * Writes an error with column position using a Path object.
     */
    public void writeError(Path filePath, int line, int column, String message) {
        writeError(filePath.toString(), line, column, message);
    }
    
    /**
     * Writes a note (additional information about an error).
     * 
     * @param fileName The source file name (use full path or relative path from project root for IDE clickability)
     * @param line The line number
     * @param message The note message
     */
    public void writeNote(String fileName, int line, String message) {
        String normalizedPath = normalizePath(fileName);
        output.println(String.format("%s:%d: note: %s", normalizedPath, line, message));
    }
    
    /**
     * Writes a note using a Path object.
     */
    public void writeNote(Path filePath, int line, String message) {
        writeNote(filePath.toString(), line, message);
    }
    
    /**
     * Example usage
     */
    public static void main(String[] args) {
        CompilerErrorWriter writer = new CompilerErrorWriter();
        
        // Example errors
        writer.writeError("Test.java", 5, "cannot find symbol");
        writer.writeNote("Test.java", 5, "symbol:   class String");
        writer.writeNote("Test.java", 5, "location: class Test");
        
        writer.writeError("Test.java", 10, 15, "';' expected");
        
        writer.writeWarning("Test.java", 20, "unchecked or unsafe operations");
        writer.writeNote("Test.java", 20, "Recompile with -Xlint:unchecked for details");
        
        // Example with multiple errors
        writer.writeError("MyClass.java", 3, "class MyClass is public, should be declared in a file named MyClass.java");
        writer.writeError("MyClass.java", 7, "incompatible types: int cannot be converted to String");
        writer.writeError("MyClass.java", 12, 8, "method does not override or implement a method from a supertype");
    
        // Example using Path object (automatically converts to relative path)
        Path currentFile = Paths.get("src/main/java/com/example/springmvccontroller/util/CompilerErrorWriter.java");
        writer.writeError(currentFile, 12, 8, "method does not override or implement a method from a supertype");
        
        // Example using absolute path (will be converted to relative if under project root)
        Path absolutePath = Paths.get(System.getProperty("user.dir"))
            .resolve("src/main/java/com/example/springmvccontroller/util/CompilerErrorWriter.java");
        writer.writeError(absolutePath, 15, "test error with absolute path");
    }
}

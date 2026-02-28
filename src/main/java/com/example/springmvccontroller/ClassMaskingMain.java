package com.example.springmvccontroller;

import com.example.springmvccontroller.config.MaskingConfigProperties;
import com.example.springmvccontroller.entity.User;
//import com.example.springmvccontroller.interceptor.UsersAllInterceptor;
//import com.example.springmvccontroller.util.ClassMaskingExample;
import com.example.springmvccontroller.util.ClassMaskingUtility;

import io.jsonwebtoken.lang.Assert;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Standalone Java Main program that uses the same four components:
 * 1. ClassMaskingUtility - for analyzing classes and generating annotated source code
 * 2. UsersAllInterceptor - for mask functions and maskedFields configuration
 * 3. User entity - the class to analyze
 * 4. ClassMaskingExample - for demonstrating the masking functionality
 * 
 * This program can be run independently without Spring Boot:
 *   java -cp "target/classes:target/dependency/*" com.example.springmvccontroller.ClassMaskingMain
 * 
 *  mvn exec:java
 */
public class ClassMaskingMain {
    static MaskingConfigProperties maskingConfigProperties;
    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("Class Masking Main Program");
        System.out.println("");
        // mvn exec:java -Dexec.args="classes.txt"
        // mvn exec:java -Dexec.args="classes.txt target/classes"   
        System.out.println("==========================================");
        System.out.println();

        // load the masking config properties from the application.yml file
        MaskingConfigProperties config = new MaskingConfigProperties();
      

        try {
            // Create instances of the four components
            System.out.println("Initializing components...");
            
            // Component 1: ClassMaskingUtility
            ClassMaskingUtility classMaskingUtility = new ClassMaskingUtility();
            System.out.println("✓ ClassMaskingUtility initialized");
            
            // Component 2: UsersAllInterceptor (for mask functions)
            //UsersAllInterceptor usersAllInterceptor = new UsersAllInterceptor();
            // Manually initialize the interceptor since @PostConstruct won't run without Spring
            //initializeInterceptor(usersAllInterceptor);
            System.out.println("✓ UsersAllInterceptor initialized and configured");
            
            // Component 3: User entity (the class to analyze)
            Class<?> userClass = User.class;
            System.out.println("✓ User class loaded: " + userClass.getName());
            
        

            processClassesFromFile("classes.txt", "target/classes", config);
            
            System.out.println();
            System.out.println();
            
            
            System.out.println();
            System.out.println("==========================================");
            System.out.println("Program completed successfully!");
            System.out.println("==========================================");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Reads class names from a text file and processes each class found in the root folder.
     * 
     * @param classListFile Path to text file containing class names (one per line)
     * @param rootFolder Root folder to search for compiled classes
     */
    private static void processClassesFromFile(String classListFile, String rootFolder, MaskingConfigProperties config) {
        try {
            // Read class names from file
            System.out.println("Reading class names from file: " + classListFile);
            List<String> classNames = readClassNamesFromFile(classListFile);
            
            if (classNames.isEmpty()) {
                System.err.println("No class names found in file: " + classListFile);
                return;
            }
            
            System.out.println("Found " + classNames.size() + " class names to process");
            System.out.println();
            
            // Initialize components
            ClassMaskingUtility classMaskingUtility = new ClassMaskingUtility();
            
            // Create accumulator list for annotated fields across all classes
            List<Map<String, String>> accumulatedAnnotatedFields = new ArrayList<>();
            
            Path rootPath = Paths.get(rootFolder);
            if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
                System.err.println("Error: Root folder does not exist or is not a directory: " + rootFolder);
                return;
            }
            
            int processedCount = 0;
            int notFoundCount = 0;
            
            // Process each class name
            for (String className : classNames) {
                className = className.trim();
                if (className.isEmpty() || className.startsWith("#")) {
                    // Skip empty lines and comments
                    continue;
                }
                
                System.out.println("==========================================");
                System.out.println("Processing: " + className);
                System.out.println("==========================================");
                
                try {
                    // Try to find and load the class
                    Class<?> clazz = findAndLoadClass(className, rootPath);
                    
                    if (clazz != null) {
                        System.out.println("✓ Class found and loaded: " + clazz.getName());
                        System.out.println();
                        
                        // Process using demonstrateWithUtility with accumulator list
                        demonstrateWithUtility(clazz, classMaskingUtility, className, config, accumulatedAnnotatedFields);
                        
                        processedCount++;
                        System.out.println();
                    } else {
                        System.err.println("✗ Class not found: " + className);
                        notFoundCount++;
                        System.out.println();
                    }
                    
                } catch (Exception e) {
                    System.err.println("✗ Error processing class '" + className + "': " + e.getMessage());
                    e.printStackTrace();
                    notFoundCount++;
                    System.out.println();
                }
            }
            
            // Save accumulated annotated fields to a file
            saveAccumulatedAnnotatedFields(accumulatedAnnotatedFields);
            
            // Summary
            System.out.println("==========================================");
            System.out.println("Processing Summary");
            System.out.println("==========================================");
            System.out.println("Total classes to process: " + classNames.size());
            System.out.println("Successfully processed: " + processedCount);
            System.out.println("Not found or errors: " + notFoundCount);
            System.out.println("Total unique annotated fields: " + accumulatedAnnotatedFields.size());
            System.out.println("==========================================");
            
        } catch (Exception e) {
            System.err.println("Error processing classes from file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Reads class names from a text file (one per line).
     * 
     * @param filePath Path to the text file
     * @return List of class names
     * @throws IOException If file cannot be read
     */
    private static List<String> readClassNamesFromFile(String filePath) throws IOException {
        List<String> classNames = new ArrayList<>();
        Path path = Paths.get(filePath);
        
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + filePath);
        }
        
        try (Stream<String> lines = Files.lines(path)) {
            lines.forEach(line -> {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    classNames.add(trimmed);
                }
            });
        }
        
        return classNames;
    }
    
    /**
     * Finds and loads a class by name, searching in the root folder and its subdirectories.
     * 
     * @param className The class name (can be simple name or fully qualified)
     * @param rootPath Root path to search for compiled classes
     * @return The loaded Class object, or null if not found
     */
    private static Class<?> findAndLoadClass(String className, Path rootPath) {
        try {
            // First, try to load directly using Class.forName (if fully qualified)
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                // Not found via Class.forName, try searching in file system
            }
            
            // Convert class name to file path
            String classFilePath = className.replace('.', '/') + ".class";
            Path classFile = rootPath.resolve(classFilePath);
            
            // Check if file exists
            if (Files.exists(classFile) && Files.isRegularFile(classFile)) {
                // Extract package name and class name
                String packageName = extractPackageName(rootPath, classFile);
                String fullClassName = packageName.isEmpty() 
                    ? className 
                    : packageName + "." + className.substring(className.lastIndexOf('.') + 1);
                
                // Try to load the class
                try {
                    return Class.forName(fullClassName);
                } catch (ClassNotFoundException e) {
                    // Try with just the class name from file
                    String simpleClassName = classFile.getFileName().toString().replace(".class", "");
                    if (!packageName.isEmpty()) {
                        return Class.forName(packageName + "." + simpleClassName);
                    }
                }
            }
            
            // Search recursively in subdirectories
            return findClassRecursively(className, rootPath, rootPath);
            
        } catch (Exception e) {
            System.err.println("Error loading class '" + className + "': " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Recursively searches for a class file in the directory structure.
     * 
     * @param className The class name to search for
     * @param rootPath The root path (for calculating package name)
     * @param currentPath The current directory being searched
     * @return The loaded Class object, or null if not found
     */
    private static Class<?> findClassRecursively(String className, Path rootPath, Path currentPath) {
        try {
            if (!Files.isDirectory(currentPath)) {
                return null;
            }
            
            try (Stream<Path> paths = Files.list(currentPath)) {
                for (Path path : paths.collect(java.util.stream.Collectors.toList())) {
                    if (Files.isDirectory(path)) {
                        // Recursively search subdirectories
                        Class<?> found = findClassRecursively(className, rootPath, path);
                        if (found != null) {
                            return found;
                        }
                    } else if (path.getFileName().toString().equals(className + ".class") ||
                               path.getFileName().toString().equals(extractSimpleClassName(className) + ".class")) {
                        // Found a matching class file
                        String packageName = extractPackageName(rootPath, path);
                        String simpleName = path.getFileName().toString().replace(".class", "");
                        String fullClassName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
                        
                        try {
                            return Class.forName(fullClassName);
                        } catch (ClassNotFoundException e) {
                            // Continue searching
                        }
                    }
                }
            }
        } catch (IOException e) {
            // Ignore and continue
        }
        
        return null;
    }
    
    /**
     * Extracts the package name from the file path relative to root.
     * 
     * @param rootPath The root path
     * @param classFile The class file path
     * @return The package name (empty string if in root)
     */
    private static String extractPackageName(Path rootPath, Path classFile) {
        try {
            Path relativePath = rootPath.relativize(classFile.getParent());
            if (relativePath.toString().isEmpty() || relativePath.toString().equals(".")) {
                return "";
            }
            return relativePath.toString().replace('/', '.').replace('\\', '.');
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
    
    /**
     * Extracts the simple class name from a fully qualified class name.
     * 
     * @param className The class name (can be fully qualified or simple)
     * @return The simple class name
     */
    private static String extractSimpleClassName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }
    
    /**
     * Method 1: Demonstrate using ClassMaskingUtility directly
     */
    private static void demonstrateWithUtility(Class<?> clazz, ClassMaskingUtility utility, MaskingConfigProperties config) {
        demonstrateWithUtility(clazz, utility, null, config, null);
    }
    
    private static void demonstrateWithUtility(Class<?> clazz, ClassMaskingUtility utility, MaskingConfigProperties config, List<Map<String, String>> accumulatorList) {
        demonstrateWithUtility(clazz, utility, null, config, accumulatorList);
    }
    
    /**
     * Method 1: Demonstrate using ClassMaskingUtility directly (with custom output file name)
     */
    private static void demonstrateWithUtility(Class<?> clazz, 
                                               ClassMaskingUtility utility, 
                                               String customFileName,
                                               MaskingConfigProperties config,
                                               List<Map<String, String>> accumulatorList) {
        Assert.notNull(config, "MaskingConfigProperties cannot be null");
        System.out.println("Analyzing class: " + clazz.getName());
        
        // Generate annotated class source code
        String simpleClassName = clazz.getSimpleName();
        String newClassName = "Annotated" + simpleClassName;
        String annotatedClassSource = utility.generateAnnotatedClass(clazz, newClassName, config, accumulatorList);
        
        System.out.println("\n=== Generated Annotated Class Source Code ===");
        System.out.println(annotatedClassSource);
        
        // Save to file (use custom name if provided, otherwise generate from class name)
        String fileName = customFileName != null 
            ? "Annotated" + customFileName + ".java"
            : "Annotated" + simpleClassName + ".java";
        
        saveToFile(annotatedClassSource, fileName);
    }
    
   
    
    /**
     * Saves the annotated class source code to a text file.
     * 
     * @param sourceCode The source code to save
     * @param fileName The name of the file to save to
     */
    private static void saveToFile(String sourceCode, String fileName) {
        try {
            Path filePath = Paths.get(fileName);
            Files.writeString(filePath, sourceCode, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            String absolutePath = filePath.toAbsolutePath().toString();
            System.out.println("\n=== File Saved ===");
            System.out.println("Path: " + absolutePath);
            System.out.println("Size: " + sourceCode.length() + " characters");
            
        } catch (IOException e) {
            System.err.println("Error saving annotated class to file: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Saves the accumulated annotated fields list to a text file.
     * 
     * @param accumulatedAnnotatedFields List of annotated fields accumulated from all classes
     */
    private static void saveAccumulatedAnnotatedFields(List<Map<String, String>> accumulatedAnnotatedFields) {
        try {
            String fileName = "all_annotatedFields.txt";
            Path filePath = Paths.get(fileName);
            StringBuilder fileContent = new StringBuilder();
            
            fileContent.append("All Annotated Fields (Accumulated from All Classes)\n");
            for (int i = 0; i < 70; i++) {
                fileContent.append("=");
            }
            fileContent.append("\n\n");
            fileContent.append("Total unique fields: ").append(accumulatedAnnotatedFields.size()).append("\n\n");
            
            for (Map<String, String> annotatedField : accumulatedAnnotatedFields) {
                fileContent.append("\t").append(annotatedField.get("fieldName")).append("\n");
            }
            
            Files.writeString(filePath, fileContent.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            String absolutePath = filePath.toAbsolutePath().toString();
            System.out.println("\n=== Accumulated Annotated Fields File Saved ===");
            System.out.println("Path: " + absolutePath);
            System.out.println("Total fields: " + accumulatedAnnotatedFields.size());
            
        } catch (IOException e) {
            System.err.println("Error saving accumulated annotated fields to file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}


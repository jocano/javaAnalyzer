// package com.example.springmvccontroller.util;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Component;

// import java.io.IOException;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.nio.file.Paths;
// import java.nio.file.StandardOpenOption;
// import java.util.Map;

// /**
//  * Example class demonstrating how to use ClassMaskingUtility
//  * to analyze a class and generate an annotated version.
//  */
// @Component
// public class ClassMaskingExample {
    
//     private static final Logger logger = LoggerFactory.getLogger(ClassMaskingExample.class);
    
//     @Autowired
//     private ClassMaskingUtility classMaskingUtility;
    
//     /**
//      * Example: Analyze User class and generate annotated class source code.
//      */
//     public void demonstrateClassMasking(Class<?> inputClassType) {
//         // Analyze the User class
//         Map<String, String> fieldMappings = classMaskingUtility.analyzeClass(inputClassType);
        
//         System.out.println("=== Field Mappings ===");
//         fieldMappings.forEach((field, maskType) -> 
//             System.out.println("  " + field + " -> " + maskType)
//         );
        
//         // Generate annotated class source code
//         // String annotatedClassSource = classMaskingUtility.generateAnnotatedClass(
//         //     inputClassType, 
//         //     "AnnotatedUser"
//         // );
        
//         System.out.println("\n=== Generated Annotated Class ===");
//         //System.out.println(annotatedClassSource);
        
//         // Save to text file
//         //saveToFile(annotatedClassSource, "AnnotatedUser.java");
    
//     }
    
//     /**
//      * Saves the annotated class source code to a text file.
//      * 
//      * @param sourceCode The source code to save
//      * @param fileName The name of the file to save to
//      */
//     private void saveToFile(String sourceCode, String fileName) {
//         try {
//             // Save to project root directory
//             Path filePath = Paths.get(fileName);
//             Files.writeString(filePath, sourceCode, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
//             String absolutePath = filePath.toAbsolutePath().toString();
//             logger.info("Annotated class source code saved to: {}", absolutePath);
//             System.out.println("\n=== File Saved ===");
//             System.out.println("Path: " + absolutePath);
            
//         } catch (IOException e) {
//             logger.error("Error saving annotated class to file: {}", e.getMessage(), e);
//             System.err.println("Error saving file: " + e.getMessage());
//         }
//     }
// }


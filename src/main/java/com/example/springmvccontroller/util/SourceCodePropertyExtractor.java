package com.example.springmvccontroller.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.jsonwebtoken.lang.Assert;

/**
 * Alternative implementations to extract properties from Java source code.
 * 
 * This class provides multiple methods:
 * 1. Simple regex-based extraction (no dependencies)
 * 2. Line-by-line parser (no dependencies)
 * 3. JavaParser-based extraction (requires JavaParser dependency)
 */
public class SourceCodePropertyExtractor {
    
    /**
     * Method 1: Simple Regex Extraction (No Dependencies)
     * 
     * Extracts property names using regular expressions.
     * Note: This is a basic implementation and may have limitations.
     */
    public static List<PropertyInfo> extractPropertiesWithRegex(Path sourceFile) throws IOException {
        List<PropertyInfo> properties = new ArrayList<>();
        String sourceCode = Files.readString(sourceFile);
        
        // Remove comments
        sourceCode = removeComments(sourceCode);
        
        // Pattern to match field declarations
        // Matches: [modifiers] type fieldName [= value];
        Pattern fieldPattern = Pattern.compile(
            "(?:private|public|protected|static|final|transient|volatile\\s+)*" +
            "(\\w+(?:<[^>]+>)?(?:\\[\\])?(?:\\.\\w+)*)\\s+" +  // type
            "(\\w+)\\s*" +  // field name
            "(?:=\\s*[^;]+)?;"  // optional initialization
        );
        
        // Extract class body
        String classBody = extractClassBody(sourceCode);
        if (classBody == null || classBody.isEmpty()) {
            return properties;
        }
        
        Matcher matcher = fieldPattern.matcher(classBody);
        while (matcher.find()) {
            String type = matcher.group(1).trim();
            String name = matcher.group(2).trim();
            
            // Filter out method parameters and local variables
            if (!isLikelyLocalVariable(classBody, matcher.start(), name)) {
                PropertyInfo prop = new PropertyInfo();
                prop.name = name;
                prop.type = type;
                properties.add(prop);
            }
        }
        
        return properties;
    }
    
    /**
     * Method 2: Line-by-Line Parser (No Dependencies)
     * 
     * Parses source code line by line looking for field declarations.
     */
    public static List<PropertyInfo> extractPropertiesLineByLine(Path sourceFile, 
                            Map<String, Function<String, String>> maskedFields) throws IOException {
        Map<Function<String, String>, String> functionToEnumMap = getFunctionToEnumMap();
        List<PropertyInfo> properties = new ArrayList<>();
        Path outputDir = Paths.get("annotated_files");
        List<String> lines = Files.readAllLines(sourceFile);
        // create a new file using the source file name and the extension .properties
        String fileName = sourceFile.getFileName().toString().replace(".java", ".copy");
        
        // Get the parent folder name from the source file
        Path parentFolder = sourceFile.getParent();
        String parentFolderName = (parentFolder != null && parentFolder.getFileName() != null) 
            ? parentFolder.getFileName().toString() 
            : "default";
        
        // Create subfolder inside annotated_files with the same name as the original folder
        Path subFolder = outputDir.resolve(parentFolderName);
        if (!Files.exists(subFolder)) {
            Files.createDirectories(subFolder);
        }
        
        Path propertiesFile = subFolder.resolve(fileName);
        Files.writeString(propertiesFile, "");

        boolean inClassBody = false;
        int braceDepth = 0;
        int classStartLine = -1;
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            
            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("//") || 
                trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                continue;
            }
            
            // Detect class/interface/enum/record declaration
            if (trimmed.matches(".*(?:class|interface|enum|record)\\s+\\w+.*")) {
                inClassBody = true;
                classStartLine = i;
                braceDepth = 0;
            }
            
            // Track brace depth
            braceDepth += countOccurrences(line, '{');
            braceDepth -= countOccurrences(line, '}');
            
            // Check if we're in class bod
            if (inClassBody && braceDepth > 0) {
                // Look for field declarations
                PropertyInfo prop = parseFieldDeclaration(trimmed);
                if (prop != null && !isMethodDeclaration(trimmed)) {
                    String maskFunctionType = findMatchingMaskFunctionType(prop.name, maskedFields, 
                        functionToEnumMap);
                    if (maskFunctionType != null) {
                        System.out.println("Annotated name: " + prop.type + " name: " + prop.name);
                        StringBuilder source = new StringBuilder();
                        source.append("    @MaskedField(maskFunctionType = \"").append(maskFunctionType).append("\")\n");
                        Files.writeString(propertiesFile, source.toString(), StandardOpenOption.APPEND);
                    }
                    Files.writeString(propertiesFile, line + "\n", StandardOpenOption.APPEND);
                    properties.add(prop);
                } else {
                    System.out.println("No property found on line: " + line);
                    // write the line to the properties file
                    Files.writeString(propertiesFile, line + "\n", StandardOpenOption.APPEND);
                }
            } else {
                System.out.println("Not in class body on line: " + line);
                // write the line to the properties file
                Files.writeString(propertiesFile, line + "\n", StandardOpenOption.APPEND);
            }
            
            // Check if class body ended
            if (inClassBody && braceDepth <= 0 && classStartLine != i) {
                break;
            }
        }
        
        return properties;
    }
    
    
    // Helper methods
    
    private static String removeComments(String code) {
        // Remove single-line comments
        code = code.replaceAll("//.*", "");
        
        // Remove multi-line comments
        code = code.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "");
        
        return code;
    }
    
    private static String extractClassBody(String sourceCode) {
        int classStart = -1;
        int braceCount = 0;
        int bodyStart = -1;
        
        // Find class declaration
        Pattern classPattern = Pattern.compile("(?:class|interface|enum|record)\\s+\\w+");
        Matcher classMatcher = classPattern.matcher(sourceCode);
        if (!classMatcher.find()) {
            return "";
        }
        
        classStart = classMatcher.start();
        
        // Find class body (first { after class declaration)
        for (int i = classStart; i < sourceCode.length(); i++) {
            char c = sourceCode.charAt(i);
            if (c == '{') {
                if (bodyStart == -1) {
                    bodyStart = i + 1;
                }
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && bodyStart != -1) {
                    return sourceCode.substring(bodyStart, i);
                }
            }
        }
        
        return "";
    }
    
    private static boolean isLikelyLocalVariable(String classBody, int position, String varName) {
        // Simple heuristic: if we're inside a method, it's likely a local variable
        String before = classBody.substring(0, position);
        
        // Count method starts vs field ends
        int methodStarts = countOccurrences(before, '(');
        int methodEnds = countOccurrences(before, ')');
        
        // If we're inside parentheses, we're likely in a method
        return methodStarts > methodEnds;
    }
    
    private static PropertyInfo parseFieldDeclaration(String line) {
        // Pattern: [modifiers] type name [= value];
        Pattern pattern = Pattern.compile(
            "(?:private|public|protected|static|final|transient|volatile)\\s+" +
            "(\\w+(?:<[^>]+>)?(?:\\[\\])?(?:\\.\\w+)*)\\s+" +
            "(\\w+)\\s*[;=]"
        );
        
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            System.out.println("Found property line: " + line);
            PropertyInfo prop = new PropertyInfo();
            prop.type = matcher.group(1).trim();
            prop.name = matcher.group(2).trim();
            return prop;
        }
        
        return null;
    }
    
    private static boolean isMethodDeclaration(String line) {
        // Methods have parentheses and are not assignments
        return line.contains("(") && line.contains(")") && 
               !line.trim().matches(".*=\\s*new\\s+\\w+\\s*\\(.*");
    }
    
    private static int countOccurrences(String str, char c) {
        return (int) str.chars().filter(ch -> ch == c).count();
    }
    
    /**
     * Simple data class to hold property information.
     */
    public static class PropertyInfo {
        public String name;
        public String type;
        public String modifiers;
        public List<String> annotations = new ArrayList<>();
        
        @Override
        public String toString() {
            return String.format("PropertyInfo{name='%s', type='%s', modifiers='%s'}", 
                name, type, modifiers);
        }
    }
    
    /**
     * Reads class names from classes.txt and searches for Java files in the root folder
     * and all subfolders. For each matching Java file found, creates a copy with .copy
     * extension in the annotated_files folder.
     * 
     * @param classesFilePath Path to the classes.txt file
     * @param rootFolder Root folder to search for Java files
     * @throws IOException If there's an error reading files or writing copies
     */
    public static void processClassesFromFile(String classesFilePath, String rootFolder, 
                                Map<String, Function<String, String>> maskedFields) throws IOException {
        Assert.notNull(maskedFields, "maskedFields cannot be null");
        // Read class names from file
        Path classesFile = Paths.get(classesFilePath);
        if (!Files.exists(classesFile)) {
            throw new IOException("Classes file not found: " + classesFilePath);
        }
        
        List<String> classNames = readClassNamesFromFile(classesFilePath);
        if (classNames.isEmpty()) {
            System.out.println("No class names found in file: " + classesFilePath);
            return;
        }
        
        System.out.println("Found " + classNames.size() + " class names to process: " + classNames);
        
        // Create a set of expected file names (e.g., "User.java" for class "User")
        Set<String> expectedFileNames = new HashSet<>();
        for (String className : classNames) {
            // Remove package name if present, keep only simple class name
            String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
            expectedFileNames.add(simpleClassName + ".java");
        }
        
        System.out.println("Looking for files: " + expectedFileNames);
        
        // Create output directory (in current working directory, not inside root folder)
        Path rootPath = Paths.get(rootFolder);
        Path outputDir = Paths.get("annotated_files");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
            System.out.println("Created output directory: " + outputDir.toAbsolutePath());
        }
        
        // Search for Java files recursively
        List<Path> foundFiles = findJavaFilesRecursively(rootPath, expectedFileNames);
        System.out.println("Found " + foundFiles.size() + " matching Java files");
        
        // Copy each file to annotated_files folder with .copy extension
        for (Path sourceFile : foundFiles) {
            String fileName = sourceFile.getFileName().toString();
            String copyFileName = fileName.replace(".java", ".copy");
            extractPropertiesLineByLine(sourceFile, maskedFields);
            Path targetFile = outputDir.resolve(copyFileName);
            
            // Copy file content
            // String content = Files.readString(sourceFile);
            // Files.writeString(targetFile, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            //System.out.println("Copied: " + sourceFile.toAbsolutePath() + " -> " + targetFile.toAbsolutePath());
        }
        
        System.out.println("Processing complete. Copied " + foundFiles.size() + " files to " + outputDir.toAbsolutePath());
    }
    
    /**
     * Reads class names from a text file (one per line, skipping comments).
     * 
     * @param filePath Path to the classes.txt file
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
     * Recursively searches for Java files matching the expected file names.
     * 
     * @param rootPath Root directory to search
     * @param expectedFileNames Set of expected Java file names (e.g., "User.java")
     * @return List of matching Java file paths
     * @throws IOException If there's an error walking the file tree
     */
    private static List<Path> findJavaFilesRecursively(Path rootPath, Set<String> expectedFileNames) throws IOException {
        List<Path> foundFiles = new ArrayList<>();
        
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            System.err.println("Warning: Root path does not exist or is not a directory: " + rootPath);
            return foundFiles;
        }
        
        try (Stream<Path> paths = Files.walk(rootPath)) {
            foundFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String fileName = path.getFileName().toString();
                    return fileName.endsWith(".java") && expectedFileNames.contains(fileName);
                })
                .collect(Collectors.toList());
        }
        
        return foundFiles;
    }
    
    /**
     * Example usage method
     */
    public static void main(String[] args) {
        try {
            Map<String, Function<String, String>> maskedFields = initDefaultMaskingFields();
            Path sourceFile = Paths.get("src/main/java/com/example/springmvccontroller/entity/User.java");
            SourceCodePropertyExtractor.processClassesFromFile("classes.txt", "src/main/java", maskedFields);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Map<String, Function<String, String>> initDefaultMaskingFields() {
        Map<String, Function<String, String>> maskedFields = new HashMap<>();
        
        try {
            // Load properties from masking-fields.properties file
            Properties properties = new Properties();
            InputStream inputStream = SourceCodePropertyExtractor.class
                .getClassLoader()
                .getResourceAsStream("masking-fields.properties");
            
            if (inputStream == null) {
                System.err.println("Warning: masking-fields.properties not found in classpath");
                return maskedFields;
            }
            
            properties.load(inputStream);
            inputStream.close();
            
            // Iterate through properties and map to MaskFunctions enum
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                String fieldName = (String) entry.getKey();
                String enumValue = (String) entry.getValue();
                
                try {
                    // Map the enum value string to the MaskFunctions enum
                    MaskFunctions maskFunction = MaskFunctions.valueOf(enumValue);
                    maskedFields.put(fieldName, maskFunction.getMaskFunction());
                    System.out.println("Loaded masking function for field: " + fieldName + " -> " + enumValue);
                } catch (IllegalArgumentException e) {
                    System.err.println("Warning: Invalid enum value '" + enumValue + "' for field '" + fieldName + 
                                     "'. Valid values are: " + getValidEnumValues());
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading masking-fields.properties: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("maskedFields: " + maskedFields);
        return maskedFields;
    }
    
    private static String getValidEnumValues() {
        StringBuilder sb = new StringBuilder();
        for (MaskFunctions mf : MaskFunctions.values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(mf.name());
        }
        return sb.toString();
    }


    private enum MaskFunctions {
        PASSWORD_TYPE("password", fieldValue -> fieldValue.replaceAll(".", "-")),
        EMAIL_TYPE("email", fieldValue -> "-----em***-----"),
        ROLE_TYPE("role", fieldValue -> "-----rol***-----"),
        ADDRESS_TYPE("address", fieldValue -> "-----adr***-----"),
        STREET_TYPE("street", fieldValue -> "-----str***-----"),
        CITY_TYPE("city", fieldValue -> "-----cit***-----"),
        STATE_TYPE("state", fieldValue -> "-----sta***-----");

        
        private final String fieldName;
        private final Function<String, String> maskFunction;
        
        MaskFunctions(String fieldName, Function<String, String> maskFunction) {
            this.fieldName = fieldName;
            this.maskFunction = maskFunction;
        }
        
        public String getFieldName() {
            return fieldName;
        }
        
        public Function<String, String> getMaskFunction() {
            return maskFunction;
        }
    }

      /**
     * Returns a map of mask function to MaskFunctions enum type name.
     * Useful for utilities that need to map functions to enum values.
     * 
     * @return Map of Function -> enum type name (e.g., function -> "PASSWORD_TYPE")
     */
      private static Map<Function<String, String>, String> getFunctionToEnumMap() {
        Map<Function<String, String>, String> map = new HashMap<>();
        for (MaskFunctions mf : MaskFunctions.values()) {
            map.put(mf.getMaskFunction(), mf.name());
        }
        return map;
    }

     /**
     * Finds the matching MaskFunctions enum type name for a field name.
     * Uses the same matching logic as UsersAllInterceptor (exact, prefix, Soundex).
     * 
     * @param fieldName The field name to match
     * @param maskedFields The map of patterns to mask functions
     * @param functionToEnumMap Map of mask function -> enum type name
     * @return The MaskFunctions enum type name, or null if no match
     */
    private static String findMatchingMaskFunctionType(String fieldName, 
                                                       Map<String, Function<String, String>> maskedFields,
                                                       Map<Function<String, String>, String> functionToEnumMap) {
        // Use the same matching logic as UsersAllInterceptor
        Function<String, String> maskFunction = findMatchingMaskFunction(fieldName, maskedFields);
        
        if (maskFunction != null && functionToEnumMap != null) {
            return functionToEnumMap.get(maskFunction);
        }
        
        return null;
    }

     /**
     * Finds a matching mask function using the same logic as UsersAllInterceptor.
     */
     private static Function<String, String> findMatchingMaskFunction(String fieldName, Map<String, Function<String, String>> maskedFields) {
        // 1. Exact match
        if (maskedFields.containsKey(fieldName)) {
            return maskedFields.get(fieldName);
        }
        
        // 2. Prefix match: fieldName starts with key
        String longestMatch = null;
        int longestLength = 0;
        
        for (String key : maskedFields.keySet()) {
            if (fieldName.startsWith(key) && key.length() > longestLength) {
                longestMatch = key;
                longestLength = key.length();
            }
        }
        
        if (longestMatch != null) {
            return maskedFields.get(longestMatch);
        }
        
        // 3. Reverse prefix match: key starts with fieldName
        for (String key : maskedFields.keySet()) {
            if (key.startsWith(fieldName)) {
                return maskedFields.get(key);
            }
        }
        
        // 4. Soundex match
        String fieldNameSoundex = soundex(fieldName);
        for (String key : maskedFields.keySet()) {
            String keySoundex = soundex(key);
            if (fieldNameSoundex.equals(keySoundex)) {
                System.out.println("Soundex match: '" + fieldName + "' matches '" + key + "'");
                return maskedFields.get(key);
            }
        }
        
        return null;
    }
    
    /**
     * Soundex encoding algorithm (same as in UsersAllInterceptor).
     */
    private static String soundex(String word) {
        if (word == null || word.isEmpty()) {
            return "0000";
        }
        
        word = word.toUpperCase().replaceAll("[^A-Z]", "");
        if (word.isEmpty()) {
            return "0000";
        }
        
        char[] chars = word.toCharArray();
        StringBuilder soundex = new StringBuilder();
        soundex.append(chars[0]);
        
        char previousDigit = getSoundexDigit(chars[0]);
        
        for (int i = 1; i < chars.length && soundex.length() < 4; i++) {
            char c = chars[i];
            char digit = getSoundexDigit(c);
            
            if (digit != '0' && digit != previousDigit) {
                soundex.append(digit);
                previousDigit = digit;
            }
        }
        
        while (soundex.length() < 4) {
            soundex.append('0');
        }
        
        return soundex.toString();
    }
    
    private static char getSoundexDigit(char c) {
        switch (c) {
            case 'B': case 'F': case 'P': case 'V':
                return '1';
            case 'C': case 'G': case 'J': case 'K': case 'Q': case 'S': case 'X': case 'Z':
                return '2';
            case 'D': case 'T':
                return '3';
            case 'L':
                return '4';
            case 'M': case 'N':
                return '5';
            case 'R':
                return '6';
            default:
                return '0';
        }
    }
    


}



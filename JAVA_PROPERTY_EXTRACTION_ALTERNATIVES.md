# Alternatives to Identify Properties in Java Class Source Code

This document presents multiple approaches to extract properties (fields) from Java source code.

## Current Approach: Java Reflection (Compiled Classes)

**Pros:**
- ✅ Accurate and reliable
- ✅ Type information available
- ✅ Works with existing code
- ✅ Handles inheritance correctly

**Cons:**
- ❌ Requires compiled classes (`.class` files)
- ❌ Cannot analyze source code directly
- ❌ Requires classes to be on classpath

**Usage:**
```java
Class<?> clazz = Class.forName("com.example.User");
Field[] fields = clazz.getDeclaredFields();
```

---

## Alternative 1: JavaParser Library (Recommended for Source Code)

JavaParser is the most popular Java AST parser library. It can parse Java source code directly.

### Setup

Add to `pom.xml`:
```xml
<dependency>
    <groupId>com.github.javaparser</groupId>
    <artifactId>javaparser-core</artifactId>
    <version>3.25.1</version>
</dependency>
```

### Code Example

```java
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

public class JavaParserPropertyExtractor {
    
    public static List<PropertyInfo> extractProperties(String sourceCode) {
        List<PropertyInfo> properties = new ArrayList<>();
        
        try {
            JavaParser parser = new JavaParser();
            ParseResult<CompilationUnit> result = parser.parse(sourceCode);
            
            if (result.isSuccessful() && result.getResult().isPresent()) {
                CompilationUnit cu = result.getResult().get();
                
                // Find all field declarations
                cu.findAll(FieldDeclaration.class).forEach(fieldDecl -> {
                    String modifiers = fieldDecl.getModifiers().toString();
                    String type = fieldDecl.getElementType().asString();
                    
                    // Handle multiple variables in one declaration (e.g., int x, y;)
                    for (VariableDeclarator var : fieldDecl.getVariables()) {
                        String name = var.getNameAsString();
                        
                        PropertyInfo prop = new PropertyInfo();
                        prop.name = name;
                        prop.type = type;
                        prop.modifiers = modifiers;
                        prop.hasGetter = hasGetter(cu, name);
                        prop.hasSetter = hasSetter(cu, name);
                        
                        properties.add(prop);
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return properties;
    }
    
    private static boolean hasGetter(CompilationUnit cu, String fieldName) {
        String getterName = "get" + capitalize(fieldName);
        String booleanGetterName = "is" + capitalize(fieldName);
        
        return cu.findAny(node -> {
            if (node instanceof MethodDeclaration) {
                MethodDeclaration method = (MethodDeclaration) node;
                String methodName = method.getNameAsString();
                return methodName.equals(getterName) || methodName.equals(booleanGetterName);
            }
            return false;
        }).isPresent();
    }
    
    private static boolean hasSetter(CompilationUnit cu, String fieldName) {
        String setterName = "set" + capitalize(fieldName);
        
        return cu.findAny(node -> {
            if (node instanceof MethodDeclaration) {
                MethodDeclaration method = (MethodDeclaration) node;
                return method.getNameAsString().equals(setterName);
            }
            return false;
        }).isPresent();
    }
    
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
```

**Pros:**
- ✅ Works with source code directly (no compilation needed)
- ✅ Very accurate parsing
- ✅ Provides full AST access
- ✅ Can detect annotations, modifiers, types
- ✅ Active project with good documentation

**Cons:**
- ❌ Additional dependency
- ❌ Slightly more complex API

---

## Alternative 2: Regular Expressions (Simple but Limited)

### Code Example

```java
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.List;

public class RegexPropertyExtractor {
    
    // Pattern to match field declarations
    // Matches: [modifiers] type fieldName [= value];
    private static final Pattern FIELD_PATTERN = Pattern.compile(
        "(?:private|public|protected|static|final|transient|volatile\\s+)*" +  // modifiers
        "(\\w+(?:<[^>]+>)?(?:\\[\\])?)\\s+" +  // type (handles generics and arrays)
        "(\\w+)\\s*" +  // field name
        "(?:=\\s*[^;]+)?;"  // optional initialization
    );
    
    public static List<String> extractPropertyNames(String sourceCode) {
        List<String> properties = new ArrayList<>();
        
        // Remove comments first
        String cleanedCode = removeComments(sourceCode);
        
        // Find class body
        int classStart = cleanedCode.indexOf('{');
        if (classStart == -1) return properties;
        
        String classBody = extractClassBody(cleanedCode, classStart);
        
        Matcher matcher = FIELD_PATTERN.matcher(classBody);
        while (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            
            // Filter out method parameters and local variables
            if (!isLocalVariable(classBody, matcher.start(), name)) {
                properties.add(name);
            }
        }
        
        return properties;
    }
    
    private static String removeComments(String code) {
        // Remove single-line comments
        code = code.replaceAll("//.*", "");
        
        // Remove multi-line comments
        code = code.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "");
        
        return code;
    }
    
    private static String extractClassBody(String code, int start) {
        int braceCount = 0;
        int bodyStart = -1;
        
        for (int i = start; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                if (bodyStart == -1) bodyStart = i;
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && bodyStart != -1) {
                    return code.substring(bodyStart + 1, i);
                }
            }
        }
        
        return "";
    }
    
    private static boolean isLocalVariable(String classBody, int position, String varName) {
        // Simple heuristic: check if it's inside a method
        String before = classBody.substring(0, position);
        int lastMethodStart = before.lastIndexOf('{');
        int lastMethodEnd = before.lastIndexOf('}');
        
        return lastMethodStart > lastMethodEnd;
    }
}
```

**Pros:**
- ✅ No external dependencies
- ✅ Simple to implement
- ✅ Fast for basic cases

**Cons:**
- ❌ Error-prone (can match wrong things)
- ❌ Doesn't handle complex Java syntax well
- ❌ Hard to distinguish fields from local variables
- ❌ Doesn't handle generics well
- ❌ Can break with nested classes, lambdas, etc.

---

## Alternative 3: Java Compiler API (javac programmatic)

Uses Java's built-in compiler API to parse and compile source code.

### Code Example

```java
import javax.tools.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;

public class CompilerAPIPropertyExtractor {
    
    public static void extractFromSourceFile(String sourceFilePath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(
            null, null, null
        );
        
        Iterable<? extends JavaFileObject> compilationUnits = 
            fileManager.getJavaFileObjectsFromFiles(
                Arrays.asList(new File(sourceFilePath))
            );
        
        // Custom compilation task that extracts info
        JavaCompiler.CompilationTask task = compiler.getTask(
            null, fileManager, null, null, null, compilationUnits
        );
        
        // Compile and extract during compilation
        boolean success = task.call();
        
        // Note: This approach requires custom implementation of 
        // JavaFileManager or AST visitor to extract properties
    }
}
```

**Pros:**
- ✅ Uses standard Java API
- ✅ Accurate (same parser as javac)
- ✅ Can compile and analyze

**Cons:**
- ❌ Complex API
- ❌ Requires custom implementation
- ❌ More involved setup

---

## Alternative 4: Eclipse JDT Core (Powerful but Heavy)

Eclipse's Java Development Tools Core provides a comprehensive AST parser.

### Setup

```xml
<dependency>
    <groupId>org.eclipse.jdt</groupId>
    <artifactId>org.eclipse.jdt.core</artifactId>
    <version>3.31.0</version>
</dependency>
```

### Code Example

```java
import org.eclipse.jdt.core.dom.*;

public class JDTPropertyExtractor {
    
    public static List<String> extractProperties(String sourceCode) {
        ASTParser parser = ASTParser.newParser(AST.JLS17);
        parser.setSource(sourceCode.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        
        List<String> properties = new ArrayList<>();
        
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(FieldDeclaration node) {
                @SuppressWarnings("unchecked")
                List<VariableDeclarationFragment> fragments = 
                    node.fragments();
                
                for (VariableDeclarationFragment fragment : fragments) {
                    String name = fragment.getName().getIdentifier();
                    properties.add(name);
                }
                
                return false;
            }
        });
        
        return properties;
    }
}
```

**Pros:**
- ✅ Very powerful and accurate
- ✅ Full AST support
- ✅ Used by Eclipse IDE

**Cons:**
- ❌ Large dependency
- ❌ More complex API
- ❌ Overkill for simple use cases

---

## Alternative 5: Simple Line-by-Line Parser (Custom Implementation)

For simple cases, you can parse line by line looking for field patterns.

### Code Example

```java
public class SimpleLineParser {
    
    public static List<String> extractProperties(Path sourceFile) throws IOException {
        List<String> properties = new ArrayList<>();
        boolean inClassBody = false;
        int braceDepth = 0;
        
        for (String line : Files.readAllLines(sourceFile)) {
            String trimmed = line.trim();
            
            // Detect class start
            if (trimmed.contains("class ") || trimmed.contains("interface ")) {
                inClassBody = true;
                braceDepth = 0;
            }
            
            // Track brace depth
            braceDepth += countOccurrences(trimmed, '{');
            braceDepth -= countOccurrences(trimmed, '}');
            
            // Check if we're in class body
            if (inClassBody && braceDepth > 0) {
                // Look for field patterns
                if (isFieldDeclaration(trimmed)) {
                    String fieldName = extractFieldName(trimmed);
                    if (fieldName != null) {
                        properties.add(fieldName);
                    }
                }
            }
            
            // Check if class body ended
            if (braceDepth == 0 && inClassBody) {
                break;
            }
        }
        
        return properties;
    }
    
    private static boolean isFieldDeclaration(String line) {
        // Simple heuristic: contains type keywords and semicolon
        return (line.contains("private") || line.contains("public") || 
                line.contains("protected")) &&
               (line.contains("String") || line.contains("int") || 
                line.contains("long") || line.contains("boolean") ||
                line.contains("double") || line.contains("float") ||
                line.matches(".*[A-Z]\\w*\\s+\\w+;")) &&  // Custom types
               line.contains(";") &&
               !line.contains("(");  // Not a method
    }
    
    private static String extractFieldName(String line) {
        // Extract field name (word before semicolon)
        Pattern pattern = Pattern.compile("\\b(\\w+)\\s*;");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    private static int countOccurrences(String str, char c) {
        return (int) str.chars().filter(ch -> ch == c).count();
    }
}
```

**Pros:**
- ✅ No dependencies
- ✅ Simple to understand
- ✅ Good for basic cases

**Cons:**
- ❌ Very limited accuracy
- ❌ Fails with multi-line declarations
- ❌ Doesn't handle complex syntax

---

## Recommendation

For your use case (analyzing Java source code), I recommend **JavaParser** because:

1. ✅ It's designed specifically for parsing Java source code
2. ✅ Works without compilation
3. ✅ Provides accurate AST parsing
4. ✅ Active project with good community support
5. ✅ Can handle all Java language features
6. ✅ Easy to integrate with Maven

## Next Steps

Would you like me to:
1. Add JavaParser dependency to your pom.xml?
2. Create a SourceCodePropertyExtractor class using JavaParser?
3. Update ClassMaskingMain to support both compiled classes and source code?
4. Show how to integrate JavaParser with your existing ClassMaskingUtility?


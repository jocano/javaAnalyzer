# Standalone Java Main Program - ClassMaskingMain

This document describes how to run the `ClassMaskingMain` program, which uses the same four components as the Spring Boot application but runs as a standalone Java program.

## Components Used

The program uses these four components:

1. **ClassMaskingUtility** - Analyzes classes and generates annotated source code
2. **UsersAllInterceptor** - Provides mask functions and maskedFields configuration  
3. **User Entity** - The class to analyze (com.example.springmvccontroller.entity.User)
4. **ClassMaskingExample** - Demonstrates the masking functionality (conceptually, using utility directly)

## Building and Running

### Prerequisites
- Java 17 or higher
- Maven installed
- All project dependencies built

### Step 1: Build the Project
```bash
mvn clean compile
```

### Step 2: Run the Standalone Program

#### Option A: Using Maven Exec Plugin (Recommended)

Add this to your `pom.xml` in the `<plugins>` section:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.1.0</version>
    <configuration>
        <mainClass>com.example.springmvccontroller.ClassMaskingMain</mainClass>
    </configuration>
</plugin>
```

Then run:
```bash
mvn exec:java
```

#### Option B: Using Java Command

First, collect all dependencies:
```bash
mvn dependency:copy-dependencies -DoutputDirectory=target/dependency
```

Then run:
```bash
java -cp "target/classes:target/dependency/*" \
    com.example.springmvccontroller.ClassMaskingMain
```

#### Option C: Create a JAR with Dependencies

Add Maven Assembly Plugin to `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-assembly-plugin</artifactId>
    <version>3.4.2</version>
    <configuration>
        <archive>
            <manifest>
                <mainClass>com.example.springmvccontroller.ClassMaskingMain</mainClass>
            </manifest>
        </archive>
        <descriptorRefs>
            <descriptorRef>jar-with-dependencies</descriptorRef>
        </descriptorRefs>
    </configuration>
    <executions>
        <execution>
            <id>make-assembly</id>
            <phase>package</phase>
            <goals>
                <goal>single</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Build and run:
```bash
mvn clean package
java -jar target/spring-mvc-controller-0.0.1-SNAPSHOT-jar-with-dependencies.jar
```

## Program Output

The program demonstrates three methods of using the components:

### Method 1: Using ClassMaskingUtility Directly
- Analyzes the User class
- Generates field mappings
- Creates annotated class source code
- Saves to `AnnotatedUserFromUtility.java`

### Method 2: Using ClassMaskingExample Approach
- Uses ClassMaskingUtility (since Example requires Spring)
- Generates annotated class source code
- Saves to `AnnotatedUserFromExample.java`

### Method 3: Using UsersAllInterceptor's Configuration
- Gets maskedFields from the interceptor
- Uses the interceptor's mask function configuration
- Generates annotated class source code
- Saves to `AnnotatedUserFromInterceptor.java`

## Expected Output

```
==========================================
Class Masking Main Program
==========================================

Initializing components...
✓ ClassMaskingUtility initialized
✓ UsersAllInterceptor initialized and configured
✓ User class loaded: com.example.springmvccontroller.entity.User
✓ ClassMaskingExample initialized

==========================================
All components initialized successfully!
==========================================

Method 1: Using ClassMaskingUtility directly
--------------------------------------------
Analyzing class: com.example.springmvccontroller.entity.User

=== Field Mappings ===
  paswrd -> PASSWORD_TYPE
  emal -> EMAIL_TYPE
  role -> ROLE_TYPE

=== Generated Annotated Class Source Code ===
[Generated Java source code...]

=== File Saved ===
Path: /path/to/AnnotatedUserFromUtility.java
Size: 1234 characters
```

## Generated Files

The program generates three Java source files:
- `AnnotatedUserFromUtility.java`
- `AnnotatedUserFromExample.java`
- `AnnotatedUserFromInterceptor.java`

These files contain the same structure as the User class but with `@MaskedField` annotations added to fields that match the masking configuration.

## Notes

- The program uses reflection to manually initialize the `UsersAllInterceptor` since `@PostConstruct` won't run without Spring
- If YAML configuration is needed, the interceptor will fall back to default values
- The program works completely standalone without requiring Spring Boot to be running

## Troubleshooting

### Error: ClassNotFoundException
Make sure all dependencies are in the classpath. Use `mvn dependency:copy-dependencies` first.

### Error: NoClassDefFoundError
Ensure you've compiled the project with `mvn clean compile` before running.

### Warning: Could not initialize interceptor
This is usually non-fatal - the interceptor will use default masking fields. Check that the `initializeMaskedFields()` method exists and is accessible.


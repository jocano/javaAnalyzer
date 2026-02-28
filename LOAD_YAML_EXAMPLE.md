# Loading MaskingConfigProperties from YAML Without Spring Boot

This document shows how to load `MaskingConfigProperties` from `application.yml` without using Spring Boot.

## Implementation

The `MaskingConfigProperties` class now includes a `load()` method that can parse YAML files without Spring Boot context.

### Features

1. **Uses SnakeYAML** (included in Spring Boot dependencies)
2. **Fallback to manual parsing** if SnakeYAML is not available
3. **Supports both File and Path** inputs
4. **Handles kebab-case to camelCase** conversion (`mask-functions` → `maskFunctions`)

## Usage Examples

### Example 1: Load from File

```java
import com.example.springmvccontroller.config.MaskingConfigProperties;
import java.io.File;
import java.io.IOException;

public class Example {
    public static void main(String[] args) {
        try {
            MaskingConfigProperties config = new MaskingConfigProperties();
            config.load(new File("src/main/resources/application.yml"));
            
            System.out.println("Fields: " + config.getFields());
            System.out.println("Mask Functions: " + config.getMaskFunctions());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

### Example 2: Load using Static Factory Method

```java
import com.example.springmvccontroller.config.MaskingConfigProperties;
import java.io.IOException;

public class Example {
    public static void main(String[] args) {
        try {
            // Load from file path string
            MaskingConfigProperties config = 
                MaskingConfigProperties.loadFromFile("src/main/resources/application.yml");
            
            // Load from File object
            // MaskingConfigProperties config = 
            //     MaskingConfigProperties.loadFromFile(new File("application.yml"));
            
            System.out.println("Fields: " + config.getFields());
            System.out.println("Mask Functions: " + config.getMaskFunctions());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

### Example 3: Load from Path

```java
import com.example.springmvccontroller.config.MaskingConfigProperties;
import java.nio.file.Paths;
import java.io.IOException;

public class Example {
    public static void main(String[] args) {
        try {
            MaskingConfigProperties config = new MaskingConfigProperties();
            config.load(Paths.get("src/main/resources/application.yml"));
            
            System.out.println("Fields: " + config.getFields());
            System.out.println("Mask Functions: " + config.getMaskFunctions());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

### Example 4: Integration with ClassMaskingMain

```java
// In ClassMaskingMain.java
MaskingConfigProperties maskingConfigProperties = 
    MaskingConfigProperties.loadFromFile("src/main/resources/application.yml");

// Now use it to configure the interceptor
UsersAllInterceptor interceptor = new UsersAllInterceptor();
// ... set the configuration
```

## YAML File Structure

The method expects the following structure in `application.yml`:

```yaml
masking:
  fields:
    pass: PASSWORD_TYPE
    passwrd: PASSWORD_TYPE
    email: EMAIL_TYPE
    role: ROLE_TYPE
  mask-functions:  # or maskFunctions
    PASSWORD_TYPE: "-----pass***-----"
    EMAIL_TYPE: "-----em***-----"
    ROLE_TYPE: "-----rol***-----"
```

## How It Works

1. **First, tries SnakeYAML**: Uses the SnakeYAML library (included in Spring Boot) to parse the YAML file
2. **Fallback to manual parsing**: If SnakeYAML is not available, uses a simple line-by-line parser
3. **Extracts masking section**: Looks for the `masking:` section in the YAML
4. **Populates maps**: Extracts `fields` and `mask-functions` (or `maskFunctions`) maps

## Error Handling

- Throws `IOException` if the file cannot be read
- Throws `IOException` if the YAML is invalid
- Prints warning if `masking` section is not found (continues with empty maps)

## Notes

- SnakeYAML is included in `spring-boot-starter-web`, so it should be available
- The manual parser is a basic implementation and may not handle all YAML features
- Comments (lines starting with `#`) are ignored
- Both `mask-functions` (kebab-case) and `maskFunctions` (camelCase) are supported


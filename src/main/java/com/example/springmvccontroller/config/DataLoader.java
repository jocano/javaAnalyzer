package com.example.springmvccontroller.config;

import com.example.springmvccontroller.entity.User;
import com.example.springmvccontroller.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== Starting User Data Loader ===");
        
        // Create admin user if it doesn't exist
        if (!userService.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPaswrd(passwordEncoder.encode("admin123"));
            admin.setEmal("admin@example.com");
            admin.setRole("ADMIN");
            admin.setEnabled(true);
            userService.save(admin);
            System.out.println("✓ Created admin user: admin/admin123");
        } else {
            System.out.println("○ Admin user already exists");
        }
        
        // Create regular user if it doesn't exist
        if (!userService.existsByUsername("user")) {
            User user = new User();
            user.setUsername("user");
            user.setPaswrd(passwordEncoder.encode("password"));
            user.setEmal("user@example.com");
            user.setRole("USER");
            user.setEnabled(true);
            userService.save(user);
            System.out.println("✓ Created regular user: user/password");
        } else {
            System.out.println("○ Regular user already exists");
        }
        
        // Create demo user if it doesn't exist
        if (!userService.existsByUsername("demo")) {
            User demo = new User();
            demo.setUsername("demo");
            demo.setPaswrd(passwordEncoder.encode("demo123"));
            demo.setEmal("demo@example.com");
            demo.setRole("USER");
            demo.setEnabled(true);
            userService.save(demo);
            System.out.println("✓ Created demo user: demo/demo123");
        } else {
            System.out.println("○ Demo user already exists");
        }
        
        // Create test user if it doesn't exist
        if (!userService.existsByUsername("test")) {
            User test = new User();
            test.setUsername("test");
            test.setPaswrd(passwordEncoder.encode("test123"));
            test.setEmal("test@example.com");
            test.setRole("USER");
            test.setEnabled(true);
            userService.save(test);
            System.out.println("✓ Created test user: test/test123");
        } else {
            System.out.println("○ Test user already exists");
        }
        
        System.out.println("=== User Data Loader Completed ===\n");
    }
}

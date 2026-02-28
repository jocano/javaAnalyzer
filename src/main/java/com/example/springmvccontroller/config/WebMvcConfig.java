package com.example.springmvccontroller.config;

import com.example.springmvccontroller.interceptor.UsersAllInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Autowired
    private UsersAllInterceptor usersAllInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // registry.addInterceptor(usersAllInterceptor)
        //         .addPathPatterns("/api/users/all") // Intercept this specific endpoint
        //         .order(1); // Set the order of execution (lower numbers have higher priority)
    }
}


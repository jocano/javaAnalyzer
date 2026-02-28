package com.example.springmvccontroller.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

@Component
@Order(1)
public class ResponseCachingFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        // Only wrap response for /api/users/all endpoint
        if (request.getRequestURI().equals("/api/users/all")) {
            ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
            
            // Store wrapper in request attribute for interceptor to access
            request.setAttribute("contentCachingResponseWrapper", wrappedResponse);
            
            try {
                filterChain.doFilter(request, wrappedResponse);
            } finally {
                // Check if interceptor has created a masked response body
                String maskedResponseBody = (String) request.getAttribute("maskedResponseBody");
                
                if (maskedResponseBody != null && !maskedResponseBody.isEmpty()) {
                    // Use the masked response body instead of the original
                    byte[] maskedBytes = maskedResponseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    response.setContentLength(maskedBytes.length);
                    response.getOutputStream().write(maskedBytes);
                    response.getOutputStream().flush();
                } else {
                    // Copy the cached content to the actual response (original behavior)
                    wrappedResponse.copyBodyToResponse();
                }
                
                // Extract response body and store in request attribute
                byte[] contentAsBytes = wrappedResponse.getContentAsByteArray();
                if (contentAsBytes.length > 0) {
                    String responseBody = maskedResponseBody != null ? maskedResponseBody : 
                        new String(contentAsBytes, java.nio.charset.StandardCharsets.UTF_8);
                    request.setAttribute("responseBody", responseBody);
                }
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }
}


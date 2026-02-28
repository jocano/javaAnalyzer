package com.example.springmvccontroller.interceptor;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class CachingResponseWrapper extends HttpServletResponseWrapper {
    
    private final ByteArrayOutputStream content = new ByteArrayOutputStream();
    private ServletOutputStream outputStream;
    private PrintWriter writer;
    
    public CachingResponseWrapper(HttpServletResponse response) {
        super(response);
    }
    
    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (outputStream == null) {
            outputStream = new CachingServletOutputStream(content);
        }
        return outputStream;
    }
    
    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            writer = new PrintWriter(new java.io.OutputStreamWriter(content, StandardCharsets.UTF_8), true);
        }
        return writer;
    }
    
    public String getContentAsString() {
        return content.toString(StandardCharsets.UTF_8);
    }
    
    public byte[] getContentAsBytes() {
        return content.toByteArray();
    }
    
    private static class CachingServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream outputStream;
        
        public CachingServletOutputStream(ByteArrayOutputStream outputStream) {
            this.outputStream = outputStream;
        }
        
        @Override
        public void write(int b) throws IOException {
            outputStream.write(b);
        }
        
        @Override
        public void write(byte[] b) throws IOException {
            outputStream.write(b);
        }
        
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            outputStream.write(b, off, len);
        }
        
        @Override
        public boolean isReady() {
            return true;
        }
        
        @Override
        public void setWriteListener(WriteListener listener) {
            // Not needed for blocking I/O
        }
    }
}


package com.example.springmvccontroller.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String SECRET = "mySuperStrongSecretKeyThatIsAtLeastThirtyTwoBytesLong!!"; // >= 32 bytes
    private static final int EXPIRATION_TIME = 86400000; // 24 hours
    private static final String CLAIM_PASSWORD_HASH = "pwd";

    /** Generates a token with username only (no password binding). */
    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generates a token bound to the user's current password hash.
     * When the user changes password in the DB, existing tokens will no longer validate.
     */
    public String generateToken(String username, String encodedPasswordHash) {
        return Jwts.builder()
                .setSubject(username)
                .claim(CLAIM_PASSWORD_HASH, encodedPasswordHash)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    /** Extracts the stored password hash claim, or null if not present (older tokens). */
    public String extractPasswordHash(String token) {
        return extractClaims(token).get(CLAIM_PASSWORD_HASH, String.class);
    }

    public Date extractExpiration(String token) {
        return extractClaims(token).getExpiration();
    }

    public Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Boolean validateToken(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }

    private Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}

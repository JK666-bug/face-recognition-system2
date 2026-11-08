package com.face.recognition.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.ExpiredJwtException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue and check JWTs without starting Spring or a database.
 */
class JwtServiceTest {

    private final JwtService jwtService = new JwtService();

    @BeforeEach
    void setUp() {
        // HS256 needs a long secret. This is Base64 of at least 32 bytes of key material.
        ReflectionTestUtils.setField(
                jwtService,
                "secretKey",
                "dGhpc2lzYXZlcnlsb25nYmFzZTY0c2VjcmV0a2V5Zm9yand0dGVzdGluZzEyMzQ1");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);
    }

    @Test
    void generateToken_containsSubjectAndRole() {
        String token = jwtService.generateToken("alice", "SUPER_ADMIN");
        assertEquals("alice", jwtService.extractUsername(token));
        assertEquals("SUPER_ADMIN", jwtService.extractClaim(token, c -> c.get("role", String.class)));
    }

    @Test
    void isTokenValid_trueWhenFresh() {
        String token = jwtService.generateToken("bob", "ADMIN");
        assertTrue(jwtService.isTokenValid(token, "bob"));
    }

    @Test
    void isTokenValid_falseWhenUsernameMismatch() {
        String token = jwtService.generateToken("bob", "ADMIN");
        assertFalse(jwtService.isTokenValid(token, "other"));
    }

    @Test
    void isTokenValid_expiredToken_throwsExpiredJwt() {
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -86_400_000L);
        String token = jwtService.generateToken("exp", "ADMIN");
        // parseClaims throws on expiry instead of returning false.
        assertThrows(ExpiredJwtException.class, () -> jwtService.isTokenValid(token, "exp"));
    }

    @Test
    void malformedToken_throws() {
        assertThrows(Exception.class, () -> jwtService.extractUsername("not-a-jwt"));
    }
}

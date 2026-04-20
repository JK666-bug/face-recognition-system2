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
 * JWT 签发与校验（不启动 Spring 容器，不连数据库）。
 */
class JwtServiceTest {

    private final JwtService jwtService = new JwtService();

    @BeforeEach
    void setUp() {
        // HS256 需要足够长的密钥；此处为 Base64(任意长度>=32字节的密钥材料)
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
        // 当前实现先 parseClaims，过期会直接抛异常而非返回 false
        assertThrows(ExpiredJwtException.class, () -> jwtService.isTokenValid(token, "exp"));
    }

    @Test
    void malformedToken_throws() {
        assertThrows(Exception.class, () -> jwtService.extractUsername("not-a-jwt"));
    }
}

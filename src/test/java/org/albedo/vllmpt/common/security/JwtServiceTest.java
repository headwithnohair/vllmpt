package org.albedo.vllmpt.common.security;

import io.jsonwebtoken.JwtException;
import org.albedo.vllmpt.config.JwtProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    @Test
    void issuesAndParsesUserId() {
        JwtService service = new JwtService(properties());

        String token = service.issue(5L);

        assertEquals(5L, service.parseUserId(token));
    }

    @Test
    void rejectsTamperedToken() {
        JwtService service = new JwtService(properties());

        assertThrows(JwtException.class, () -> service.parseUserId("not-a-token"));
    }

    @Test
    void rejectsExpiredToken() {
        JwtProperties properties = properties();
        properties.setExpirationMinutes(-1);
        JwtService service = new JwtService(properties);

        String token = service.issue(5L);

        assertThrows(JwtException.class, () -> service.parseUserId(token));
    }

    private JwtProperties properties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-test-secret-test-secret-123456");
        properties.setExpirationMinutes(30);
        return properties;
    }
}

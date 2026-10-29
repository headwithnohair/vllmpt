package org.albedo.vllmpt.common.security;

import org.albedo.vllmpt.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserContextFilterTest {

    @Test
    void bearerTokenSetsUserAndClearsAfterRequest() throws Exception {
        UserContextFilter filter = new UserContextFilter(jwtService());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService().issue(5L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seen.set(UserContextHolder.getUserId()));

        assertEquals(5L, seen.get());
        assertEquals(null, UserContextHolder.getUserId());
    }

    @Test
    void devHeaderFallbackSetsUser() throws Exception {
        UserContextFilter filter = new UserContextFilter(jwtService());
        ReflectionTestUtils.setField(filter, "defaultUserEnabled", true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seen.set(UserContextHolder.getUserId()));

        assertEquals(7L, seen.get());
    }

    @Test
    void invalidBearerTokenReturns401() throws Exception {
        UserContextFilter filter = new UserContextFilter(jwtService());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer broken");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("401"));
    }

    private JwtService jwtService() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-test-secret-test-secret-123456");
        return new JwtService(properties);
    }
}

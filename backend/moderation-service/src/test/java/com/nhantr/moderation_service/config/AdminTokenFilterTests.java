package com.nhantr.moderation_service.config;
import com.nhantr.common.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class AdminTokenFilterTests {
    final JwtTokenService jwt = new JwtTokenService("a-test-signing-key-of-at-least-thirty-two-bytes");
    @Test void userAccessTokenWithAdminRoleCannotModerate() throws Exception {
        var req = new MockHttpServletRequest("POST", "/api/v1/admin/reports/1/resolve");
        req.addHeader("Authorization", "Bearer " + jwt.create(1L, "access", "MODERATOR", Duration.ofMinutes(1)));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        new AdminTokenFilter(jwt).doFilter(req, response, chain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }
    @Test void adminTokenProceedsToRoleAuthorization() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/admin/reports/queue");
        req.addHeader("Authorization", "Bearer " + jwt.create(1L, "admin", "MODERATOR", Duration.ofMinutes(1)));
        var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
        new AdminTokenFilter(jwt).doFilter(req, response, chain);
        assertThat(chain.getRequest()).isSameAs(req);
    }
}

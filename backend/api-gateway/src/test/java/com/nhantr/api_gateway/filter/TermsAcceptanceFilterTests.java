package com.nhantr.api_gateway.filter;

import com.nhantr.common.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TermsAcceptanceFilterTests {
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final TermsAcceptanceFilter filter = new TermsAcceptanceFilter(objectMapper);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksUnacceptedUserOnBusinessRouteWithStableProblemCode() throws Exception {
        authenticate(new CurrentUser(42L, "USER", false));
        MockHttpServletResponse response = execute("/api/v1/friends");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        JsonNode problem = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(problem.path("code").asText()).isEqualTo("TERMS_ACCEPTANCE_REQUIRED");
        assertThat(problem.path("status").asInt()).isEqualTo(403);
    }

    @Test
    void allowsAcceptedUserOnBusinessRoute() throws Exception {
        authenticate(new CurrentUser(42L, "USER", true));

        assertThat(execute("/api/v1/friends").getStatus()).isEqualTo(200);
    }

    @Test
    void blocksLegacyPrincipalThatDefaultsMissingClaimToFalse() throws Exception {
        authenticate(new CurrentUser(42L, "USER", false));

        assertThat(execute("/api/v1/posts").getStatus()).isEqualTo(403);
    }

    @Test
    void allowsTermsAuthHealthAndDocumentationPaths() throws Exception {
        authenticate(new CurrentUser(42L, "USER", false));

        for (String path : List.of(
                "/api/v1/terms/latest",
                "/api/v1/terms/accept",
                "/api/v1/auth/refresh",
                "/actuator/health",
                "/actuator/info",
                "/v3/api-docs/identity",
                "/swagger-ui/index.html",
                "/swagger-ui.html"
        )) {
            assertThat(execute(path).getStatus()).as(path).isEqualTo(200);
        }
    }

    @Test
    void allowsAdminAndServicePrincipalsRegardlessOfTermsState() throws Exception {
        authenticate(new CurrentUser(1L, "SUPER_ADMIN", false));
        assertThat(execute("/api/v1/admin/reports").getStatus()).isEqualTo(200);

        authenticate(new CurrentUser(-1L, "SERVICE", false));
        assertThat(execute("/api/v1/friends").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse execute(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("X-Request-Id", "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private void authenticate(CurrentUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of())
        );
    }
}

package com.nhantr.api_gateway.filter;

import com.nhantr.common.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class TermsAcceptanceFilter extends OncePerRequestFilter {
    private static final String ERROR_CODE = "TERMS_ACCEPTANCE_REQUIRED";

    private final ObjectMapper objectMapper;

    public TermsAcceptanceFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (requiresTermsAcceptance(request.getRequestURI(), authentication)) {
            writeProblem(response, request.getHeader("X-Request-Id"));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean requiresTermsAcceptance(String path, Authentication authentication) {
        if (!isGatedUserPath(path)
                || authentication == null
                || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            return false;
        }
        return !user.termsAccepted() && !user.isAdmin() && !"SERVICE".equals(user.role());
    }

    private boolean isGatedUserPath(String path) {
        if (!path.startsWith("/api/v1/")) {
            return false;
        }
        return !path.startsWith("/api/v1/auth/")
                && !path.equals("/api/v1/terms/latest")
                && !path.equals("/api/v1/terms/accept")
                && !path.startsWith("/api/v1/admin/");
    }

    private void writeProblem(HttpServletResponse response, String requestId) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://loccoc.app/problems/terms_acceptance_required");
        problem.put("title", ERROR_CODE);
        problem.put("status", HttpServletResponse.SC_FORBIDDEN);
        problem.put("detail", "Accept the latest Terms of Service before using this endpoint.");
        problem.put("code", ERROR_CODE);
        problem.put("requestId", requestId);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}

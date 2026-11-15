package com.nhantr.common.web;

import com.nhantr.common.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.LongPredicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Re-check mutable account state for issued user JWTs. No availability bypass or cached ban delay. */
@Component
@Order(-40)
public class ActiveAccountFilter extends OncePerRequestFilter {
    private final LongPredicate active;
    @Autowired
    public ActiveAccountFilter(AccountStateClient client) { this.active = client::isActive; }
    ActiveAccountFilter(LongPredicate active) { this.active = active; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!path.startsWith("/api/v1/") || path.startsWith("/api/v1/auth/") || path.equals("/api/v1/terms/latest")
                || (path.equals("/api/v1/payments/webhook") || path.startsWith("/api/v1/payments/sandbox/checkouts/"))
                || auth == null || !(auth.getPrincipal() instanceof CurrentUser user)
                || user.isAdmin() || "SERVICE".equals(user.role())) {
            chain.doFilter(request, response); return;
        }
        boolean allowed;
        try { allowed = active.test(user.id()); }
        catch (RuntimeException exception) { problem(response, 503, "ACCOUNT_STATE_UNAVAILABLE"); return; }
        if (!allowed) { problem(response, 403, "ACCOUNT_INACTIVE"); return; }
        chain.doFilter(request, response);
    }
    private void problem(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status); response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\"}");
    }
}

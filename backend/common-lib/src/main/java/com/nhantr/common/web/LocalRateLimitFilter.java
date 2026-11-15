package com.nhantr.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded single-instance fixed-window protection. Forwarded/user headers are never identities. */
@Component
@Order(-50)
public class LocalRateLimitFilter extends OncePerRequestFilter {
    private final int publicLimit;
    private final int internalLimit;
    private final int maximumIdentities;
    private final Clock clock;
    private final Map<String, Integer> counts = new HashMap<>();
    private long window = -1;

    @Autowired
    public LocalRateLimitFilter(@Value("${security.rate-limit.public-per-minute:120}") int publicLimit,
                                @Value("${security.rate-limit.internal-per-minute:600}") int internalLimit,
                                @Value("${security.rate-limit.maximum-identities:10000}") int maximumIdentities) {
        this(publicLimit, internalLimit, maximumIdentities, Clock.systemUTC());
    }
    LocalRateLimitFilter(int publicLimit, int internalLimit, int maximumIdentities, Clock clock) {
        if (publicLimit < 1 || internalLimit < 1 || maximumIdentities < 1) throw new IllegalArgumentException("Invalid rate limits");
        this.publicLimit = publicLimit;
        this.internalLimit = internalLimit;
        this.maximumIdentities = maximumIdentities;
        this.clock = clock;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean internal = request.getRequestURI().startsWith("/internal/");
        if (!internal && !request.getRequestURI().startsWith("/api/")) { chain.doFilter(request, response); return; }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String identity = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())
                ? "principal:" + auth.getName() : "ip:" + request.getRemoteAddr();
        if (!allow((internal ? "internal:" : "public:") + identity, internal ? internalLimit : publicLimit)) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(60 - clock.instant().getEpochSecond() % 60));
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"status\":429,\"code\":\"RATE_LIMITED\",\"title\":\"Too many requests\"}");
            return;
        }
        chain.doFilter(request, response);
    }
    private synchronized boolean allow(String identity, int limit) {
        long now = clock.instant().getEpochSecond() / 60;
        if (window != now) { counts.clear(); window = now; }
        if (!counts.containsKey(identity) && counts.size() >= maximumIdentities) return false;
        int count = counts.getOrDefault(identity, 0);
        if (count >= limit) return false;
        counts.put(identity, count + 1);
        return true;
    }
}

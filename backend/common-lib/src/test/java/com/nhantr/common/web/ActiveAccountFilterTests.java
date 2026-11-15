package com.nhantr.common.web;

import com.nhantr.common.security.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class ActiveAccountFilterTests {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void inactiveIssuedJwtIsDenied() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CurrentUser(1L,"USER",true), null, List.of()));
        var response = new MockHttpServletResponse();
        new ActiveAccountFilter(id -> false).doFilter(new MockHttpServletRequest("GET", "/api/v1/posts"), response, (req, res) -> fail("inactive"));
        assertEquals(403, response.getStatus());
    }
    @Test void unavailableIdentityFailsClosed() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CurrentUser(1L,"USER",true), null, List.of()));
        var response = new MockHttpServletResponse();
        new ActiveAccountFilter(id -> { throw new IllegalStateException(); }).doFilter(new MockHttpServletRequest("GET", "/api/v1/posts"), response, (req, res) -> fail("unavailable"));
        assertEquals(503, response.getStatus());
    }
    @Test void internalDoesNotRecurseAndAdminIsIndependent() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CurrentUser(1L,"USER",true), null, List.of()));
        new ActiveAccountFilter(id -> { fail("must skip internal"); return false; })
                .doFilter(new MockHttpServletRequest("GET", "/internal/v1/users/1/active"), new MockHttpServletResponse(), (req, res) -> {});
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CurrentUser(1L,"SUPER_ADMIN",true), null, List.of()));
        new ActiveAccountFilter(id -> { fail("must skip admin"); return false; })
                .doFilter(new MockHttpServletRequest("GET", "/api/v1/admin/reports"), new MockHttpServletResponse(), (req, res) -> {});
    }
}

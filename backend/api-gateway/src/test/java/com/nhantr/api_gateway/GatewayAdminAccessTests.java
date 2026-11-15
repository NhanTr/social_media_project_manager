package com.nhantr.api_gateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@SpringBootTest
class GatewayAdminAccessTests {
    @Autowired FilterChainProxy security;
    MockMvc mvc;
    @BeforeEach void setup() { mvc=MockMvcBuilders.standaloneSetup(new Probe()).addFilters(security).build(); }
    @Test void publicAdminLoginReachesBackendBoundaryWithoutUserToken() throws Exception {
        mvc.perform(post("/api/v1/admin/auth/login")).andExpect(status().isOk());
    }
    @Test void adminOperationsStillRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/admin/reports")).andExpect(status().isForbidden());
    }
    @RestController static class Probe {
        @PostMapping("/api/v1/admin/auth/login") String login() { return "reached"; }
        @GetMapping("/api/v1/admin/reports") String reports() { return "protected"; }
    }
}

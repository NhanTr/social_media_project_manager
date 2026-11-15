package com.nhantr.api_gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class InternalEndpointDenyFilterTests {
    @Test
    void internalKeyCannotOpenInternalGatewayPath() throws Exception {
        var request = new MockHttpServletRequest("GET", "/internal/v1/users/1");
        request.addHeader("X-Internal-Api-Key", "valid-key");
        var response = new MockHttpServletResponse();
        new InternalEndpointDenyFilter().doFilter(request, response, (req, res) -> fail("must deny"));
        assertEquals(404, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
    }
}

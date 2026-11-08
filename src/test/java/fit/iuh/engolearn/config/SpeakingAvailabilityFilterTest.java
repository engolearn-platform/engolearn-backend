package fit.iuh.engolearn.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class SpeakingAvailabilityFilterTest {
    @Test void productionRejectsSpeakingRestAndWebSocketEvenWhenConfigured() throws Exception {
        for (String path : new String[] { "/api/v1/speaking/sessions", "/ws/speaking/session" }) {
            var environment = new MockEnvironment();
            environment.setActiveProfiles("prod");
            var filter = new SpeakingAvailabilityFilter(environment, true);
            var request = new MockHttpServletRequest("GET", path);
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertEquals(404, response.getStatus());
            assertNull(chain.getRequest());
            assertTrue(response.getContentAsString().contains("SPEAKING_DISABLED"));
        }
    }

    @Test void devDemoAllowsSpeakingOnlyWhenConfigured() throws Exception {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        var filter = new SpeakingAvailabilityFilter(environment, true);
        var request = new MockHttpServletRequest("GET", "/api/v1/speaking/sessions");
        var chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertSame(request, chain.getRequest());
    }
}

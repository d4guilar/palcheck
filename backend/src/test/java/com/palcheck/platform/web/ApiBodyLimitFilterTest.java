package com.palcheck.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class ApiBodyLimitFilterTest {
    private final ApiBodyLimitFilter filter = new ApiBodyLimitFilter(new ProblemWriter(new JsonMapper(), Clock.systemUTC()));

    @Test
    void rejectsKnownAndUnknownLengthBodiesWithoutReachingTheController() throws Exception {
        for (boolean unknown : new boolean[]{false, true}) {
            var request = new MockHttpServletRequest("POST", "/api/people") {
                @Override public long getContentLengthLong() { return unknown ? -1 : super.getContentLengthLong(); }
            };
            request.setContent(new byte[ApiBodyLimitFilter.MAX_BYTES + 1]);
            var response = new MockHttpServletResponse();
            AtomicInteger calls = new AtomicInteger();
            filter.doFilter(request, response, (req, res) -> calls.incrementAndGet());
            assertThat(response.getStatus()).isEqualTo(413);
            assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
            assertThat(calls.get()).isZero();
        }
    }

    @Test
    void acceptsTheExactBoundaryAndPreservesTheBody() throws Exception {
        var request = new MockHttpServletRequest("PATCH", "/api/people/id");
        byte[] content = new byte[ApiBodyLimitFilter.MAX_BYTES];
        content[content.length - 1] = 42;
        request.setContent(content);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertThat(req.getInputStream().readAllBytes()).isEqualTo(content));
    }
}

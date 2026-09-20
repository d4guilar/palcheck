package com.palcheck.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.palcheck.platform.logging.LogKeys;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Header handling is a log-injection and unbounded-input boundary, so it is tested directly. */
class CorrelationFilterTest {

    private final CorrelationFilter filter = new CorrelationFilter();

    private record Outcome(MockHttpServletResponse response, String mdcRequestId, String mdcCorrelationId) {
    }

    private Outcome run(String correlationHeader, String requestIdHeader) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/people");
        if (correlationHeader != null) {
            request.addHeader(CorrelationFilter.CORRELATION_ID_HEADER, correlationHeader);
        }
        if (requestIdHeader != null) {
            request.addHeader(CorrelationFilter.REQUEST_ID_HEADER, requestIdHeader);
        }
        var response = new MockHttpServletResponse();
        var requestId = new AtomicReference<String>();
        var correlationId = new AtomicReference<String>();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                // What code running inside the request would see.
                requestId.set(MDC.get(LogKeys.REQUEST_ID));
                correlationId.set(MDC.get(LogKeys.CORRELATION_ID));
            }
        });
        return new Outcome(response, requestId.get(), correlationId.get());
    }

    @Test
    void withoutACorrelationHeaderTheCorrelationIdIsTheGeneratedRequestId() throws Exception {
        var outcome = run(null, null);

        var requestId = outcome.response().getHeader(CorrelationFilter.REQUEST_ID_HEADER);
        assertThat(requestId).isNotBlank();
        assertThat(outcome.response().getHeader(CorrelationFilter.CORRELATION_ID_HEADER)).isEqualTo(requestId);
        assertThat(outcome.mdcRequestId()).isEqualTo(requestId);
        assertThat(outcome.mdcCorrelationId()).isEqualTo(requestId);
    }

    @Test
    void aWellFormedCorrelationIdIsKeptSoATraceCanSpanCallers() throws Exception {
        var outcome = run("checkout-2026-09-19:abc_123.7", null);

        assertThat(outcome.response().getHeader(CorrelationFilter.CORRELATION_ID_HEADER))
                .isEqualTo("checkout-2026-09-19:abc_123.7");
        assertThat(outcome.mdcCorrelationId()).isEqualTo("checkout-2026-09-19:abc_123.7");
    }

    @Test
    void aMalformedCorrelationIdIsReplacedNotEchoed() throws Exception {
        for (String hostile : new String[] {
            "line1 line2", "with space", "semi;colon", "<script>", "a".repeat(65), "", "tab\tchar"}) {
            var outcome = run(hostile, null);

            var requestId = outcome.response().getHeader(CorrelationFilter.REQUEST_ID_HEADER);
            assertThat(outcome.response().getHeader(CorrelationFilter.CORRELATION_ID_HEADER))
                    .as("correlation for %s", hostile).isEqualTo(requestId);
        }
    }

    @Test
    void theRequestIdIsAlwaysGeneratedByTheServerNeverTakenFromTheCaller() throws Exception {
        var outcome = run(null, "attacker-chosen-id");

        assertThat(outcome.response().getHeader(CorrelationFilter.REQUEST_ID_HEADER)).isNotEqualTo("attacker-chosen-id");
        assertThat(outcome.mdcRequestId()).isNotEqualTo("attacker-chosen-id");
    }

    @Test
    void eachRequestGetsItsOwnRequestId() throws Exception {
        var first = run(null, null).response().getHeader(CorrelationFilter.REQUEST_ID_HEADER);
        var second = run(null, null).response().getHeader(CorrelationFilter.REQUEST_ID_HEADER);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void theLoggingContextIsClearedWhenTheRequestEndsSoItCannotLeakToTheNextOneOnTheSameThread() throws Exception {
        run("keep-this-out", null);

        assertThat(MDC.get(LogKeys.REQUEST_ID)).isNull();
        assertThat(MDC.get(LogKeys.CORRELATION_ID)).isNull();
    }
}

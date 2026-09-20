package com.palcheck.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.palcheck.support.Api;
import com.palcheck.support.WorkforceIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Personal values must never reach application logs. A distinctive "canary" string is placed in every personal field of
 * requests that succeed, fail validation, conflict, are forbidden, are unauthenticated, are malformed, and provoke an
 * unexpected server error. No log event may contain a canary in its message, structured key/value pairs, MDC or
 * exception. The same run proves logs carry the identifiers that make them useful.
 */
class LoggingCanaryIT extends WorkforceIntegrationTest {

    private final String id = UUID.randomUUID().toString().replace("-", "");
    private final String canaryLast = "ZzCanaryFamily" + id;
    private final String canaryEmail = "zzcanary" + id + "@example.test";
    private final String canaryReason = "ZzCanaryReason" + id;

    private ListAppender<ILoggingEvent> appender;
    private Logger root;

    @BeforeEach
    void captureLogs() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void stopCapturing() {
        root.detachAppender(appender);
    }

    @Test
    void noPersonalValueEverAppearsInLogsWhileIdentifiersDo() throws Exception {
        var w = world("canary");
        var admin = w.tenant().admin();

        // Success, with canaries in every personal field.
        var body = Api.createBody(w, "Ana", canaryLast, canaryEmail);
        Api.section(body, "person").put("middleName", canaryLast);
        Api.section(body, "person").put("preferredName", canaryLast);
        body.put("reason", canaryReason);
        String personId = api.read(api.post(admin, "/api/people", body, Api.newKey()).andExpect(status().isCreated())).read("$.id");

        // Validation failures, duplicate email, forbidden, conflict, not found, malformed, unauthenticated.
        api.post(admin, "/api/people", Api.createBody(w, "Bo", canaryLast, canaryEmail), Api.newKey());
        api.post(admin, "/api/people", Api.createBody(w, canaryLast, canaryLast, "not-an-email-" + canaryLast), Api.newKey());
        api.post(w.tenant().viewer(), "/api/people", Api.createBody(w, "Cy", canaryLast, canaryEmail + ".x"), Api.newKey());
        api.patch(admin, "/api/people/" + personId, Map.of("expectedVersion", 1, "firstName", canaryLast, "reason", canaryReason));
        api.patch(admin, "/api/people/" + personId, Map.of("expectedVersion", 1, "firstName", canaryLast + "2"));
        api.patch(admin, "/api/people/" + UUID.randomUUID(), Map.of("expectedVersion", 1, "firstName", canaryLast));
        api.post(admin, "/api/people", "{ \"person\": { \"firstName\": \"" + canaryLast + "\" ", Api.newKey());
        api.getAnonymous("/api/people?q=" + canaryLast);
        api.get(admin, "/api/people?q=" + canaryLast);

        // An unexpected server error (the database rejects the audit write) with canaries in the request.
        forceAuditFailure();
        try {
            var failing = Api.createBody(w, "Di", canaryLast, canaryEmail + ".y");
            failing.put("reason", "force-audit-failure");
            api.post(admin, "/api/people", failing, Api.newKey()).andExpect(status().isInternalServerError());
        } finally {
            restoreAudit();
        }

        List<ILoggingEvent> events = new ArrayList<>(appender.list).stream()
                // The embedded test database echoes its own server log, including rejected statements; a real
                // deployment must treat database server logs as sensitive infrastructure (docs/SECURITY.md).
                .filter(e -> !e.getLoggerName().startsWith("io.zonky"))
                .toList();
        assertThat(events).isNotEmpty();

        for (ILoggingEvent event : events) {
            String everything = describe(event);
            assertThat(everything).as("log event from " + event.getLoggerName())
                    .doesNotContain(canaryLast).doesNotContain(canaryEmail).doesNotContain(canaryReason)
                    .doesNotContainIgnoringCase("bearer ").doesNotContainIgnoringCase("access_token");
        }

        // Logs are still useful: operation, result and correlation identifiers are present.
        var created = events.stream().filter(e -> value(e, "operation").equals("person.create")
                && value(e, "result").equals("success")).findFirst().orElseThrow();
        assertThat(created.getMDCPropertyMap()).containsKeys("requestId", "correlationId", "tenantId", "userId");
        assertThat(created.getMDCPropertyMap().get("tenantId")).isEqualTo(w.tenantId().toString());
        assertThat(created.getMDCPropertyMap().get("userId")).isEqualTo(admin.principalId().toString());

        assertThat(events).anySatisfy(e -> {
            assertThat(e.getLoggerName()).isEqualTo("com.palcheck.access");
            assertThat(value(e, "http.route")).isEqualTo("/api/people");
            assertThat(value(e, "http.status")).isEqualTo("201");
        });
        var unexpected = events.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
        assertThat(value(unexpected, "exception")).isNotBlank();
        assertThat(value(unexpected, "dbErrorCode")).isEqualTo("P0001");
        assertThat(unexpected.getThrowableProxy()).isNull();
    }

    private static String value(ILoggingEvent event, String key) {
        var pairs = event.getKeyValuePairs();
        if (pairs == null) {
            return "";
        }
        return pairs.stream().filter(p -> p.key.equals(key)).map(p -> String.valueOf(p.value)).findFirst().orElse("");
    }

    private static String describe(ILoggingEvent event) {
        StringBuilder text = new StringBuilder(event.getFormattedMessage()).append(' ');
        if (event.getKeyValuePairs() != null) {
            event.getKeyValuePairs().forEach(p -> text.append(p.key).append('=').append(p.value).append(' '));
        }
        event.getMDCPropertyMap().forEach((k, v) -> text.append(k).append('=').append(v).append(' '));
        if (event.getArgumentArray() != null) {
            for (Object arg : event.getArgumentArray()) {
                text.append(arg).append(' ');
            }
        }
        for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
            text.append(t.getClassName()).append(':').append(t.getMessage()).append(' ');
        }
        return text.toString();
    }

    private void forceAuditFailure() {
        jdbc.sql("""
                CREATE OR REPLACE FUNCTION audit.test_force_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.reason = 'force-audit-failure' THEN
                        RAISE EXCEPTION 'forced audit failure for test';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """).update();
        jdbc.sql("""
                CREATE TRIGGER zz_test_force_failure BEFORE INSERT ON audit.audit_entry
                FOR EACH ROW EXECUTE FUNCTION audit.test_force_failure()
                """).update();
    }

    private void restoreAudit() {
        jdbc.sql("DROP TRIGGER IF EXISTS zz_test_force_failure ON audit.audit_entry").update();
        jdbc.sql("DROP FUNCTION IF EXISTS audit.test_force_failure()").update();
    }
}

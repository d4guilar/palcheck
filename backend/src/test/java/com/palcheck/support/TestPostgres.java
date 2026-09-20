package com.palcheck.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;

/**
 * A real PostgreSQL server started in-process for integration tests (no H2, no container runtime needed).
 * One server per test JVM; tests isolate themselves by creating their own tenants. Swapping this for
 * Testcontainers, where a container runtime exists, only changes this class (ADR-0022 decision 10).
 */
public final class TestPostgres {

    private static EmbeddedPostgres server;

    private TestPostgres() {
    }

    private static synchronized EmbeddedPostgres server() {
        if (server == null) {
            try {
                server = EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new IllegalStateException("Could not start embedded PostgreSQL for tests", e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    server.close();
                } catch (IOException ignored) {
                    // best effort on JVM shutdown
                }
            }));
        }
        return server;
    }

    /** Spring properties that point the datasource at the shared embedded server. */
    public static String[] properties() {
        EmbeddedPostgres pg = server();
        return new String[] {
                "spring.datasource.url=" + pg.getJdbcUrl("postgres", "postgres"),
                "spring.datasource.username=postgres",
                "spring.datasource.password=postgres"
        };
    }
}

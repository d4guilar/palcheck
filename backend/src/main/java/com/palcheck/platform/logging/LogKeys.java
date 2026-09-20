package com.palcheck.platform.logging;

/** MDC and structured-log key names. Values must never be personal data, tokens or request bodies. */
public final class LogKeys {

    public static final String REQUEST_ID = "requestId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ID = "userId";
    public static final String OPERATION = "operation";
    public static final String RESULT = "result";

    private LogKeys() {
    }
}

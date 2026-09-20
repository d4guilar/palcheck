package com.palcheck.platform.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A deterministic identity for a request: the same operation and the same logical content always produce the same
 * hash, independent of JSON field order or whitespace. Each part is length-prefixed so different splits of the same
 * characters cannot collide.
 */
public final class Fingerprint {

    private final StringBuilder canonical = new StringBuilder();

    private Fingerprint(String operation) {
        part("op", operation);
    }

    public static Fingerprint of(String operation) {
        return new Fingerprint(operation);
    }

    /** Adds a named part. A null value is distinct from an empty string. */
    public Fingerprint part(String name, Object value) {
        String rendered = value == null ? null : value.toString();
        canonical.append(name.length()).append(':').append(name).append('=');
        if (rendered == null) {
            canonical.append('~');
        } else {
            canonical.append(rendered.length()).append(':').append(rendered);
        }
        canonical.append(';');
        return this;
    }

    public String sha256() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}

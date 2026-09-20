package com.palcheck.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies a Tenant (a PalCheck customer/account boundary). Every tenant-owned repository method requires one,
 * so a query cannot be written without stating whose data it touches.
 */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "value");
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    public static TenantId of(String value) {
        return new TenantId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

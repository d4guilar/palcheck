package com.palcheck.shared;

/** The authenticated identity has no active PalCheck principal, or its tenant is not active. */
public class TenantAccessDeniedException extends ApplicationException {

    public TenantAccessDeniedException(String message) {
        super(ErrorCode.TENANT_ACCESS_DENIED, message);
    }
}

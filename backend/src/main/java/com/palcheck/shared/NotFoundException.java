package com.palcheck.shared;

/** The record does not exist in the caller's tenant. Other tenants' records are reported the same way. */
public class NotFoundException extends ApplicationException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}

package com.palcheck.shared;

public class UnauthenticatedException extends ApplicationException {

    public UnauthenticatedException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }
}

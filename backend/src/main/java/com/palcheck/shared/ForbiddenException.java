package com.palcheck.shared;

public class ForbiddenException extends ApplicationException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}

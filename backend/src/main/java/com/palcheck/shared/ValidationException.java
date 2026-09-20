package com.palcheck.shared;

import java.util.List;

public class ValidationException extends ApplicationException {

    private final List<FieldViolation> violations;

    public ValidationException(List<FieldViolation> violations) {
        super(ErrorCode.VALIDATION_ERROR, "The request contains invalid values.");
        this.violations = List.copyOf(violations);
    }

    public ValidationException(FieldViolation violation) {
        this(List.of(violation));
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}

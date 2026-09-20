package com.palcheck.workforce.application;

import java.util.UUID;

/** An employment, rather than one arbitrarily selected employment per person. */
public record ManagerCandidate(UUID employmentId, UUID personId, String displayName, String title,
        String legalEntityName, String status) { }

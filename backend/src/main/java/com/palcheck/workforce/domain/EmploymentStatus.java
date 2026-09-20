package com.palcheck.workforce.domain;

/**
 * Placeholder lifecycle states. They are SOFTWARE BEHAVIOR, not legal concepts: whether local law requires other
 * states is RESEARCH REQUIRED (docs/DOMAIN_MODEL.md OQ-06). Nothing branches on jurisdiction.
 */
public enum EmploymentStatus {
    PENDING,
    ACTIVE,
    ENDED
}

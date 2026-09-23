package com.palcheck.workforce.domain;

/**
 * Core PalCheck software/domain lifecycle vocabulary (Q3 accepted; docs/DOMAIN_MODEL.md section 4.1). Deliberately
 * legally neutral: not a complete or authoritative Costa Rican classification, and never an input to statutory
 * calculations, eligibility or compliance. Country-specific vocabulary and mappings are separate (OQ-06).
 */
public enum EmploymentStatus {
    PENDING,
    ACTIVE,
    ENDED
}

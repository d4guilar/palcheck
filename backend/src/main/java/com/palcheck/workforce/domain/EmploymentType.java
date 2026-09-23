package com.palcheck.workforce.domain;

/**
 * Core PalCheck software/domain employment-type vocabulary (Q3 accepted; docs/DOMAIN_MODEL.md section 4.1), not a
 * legal contract or worker type. Deliberately legally neutral: never an input to statutory calculations, legal
 * eligibility or compliance. Local classifications and mappings are separate (RESEARCH_BACKLOG.md WC-1).
 */
public enum EmploymentType {
    FULL_TIME,
    PART_TIME
}

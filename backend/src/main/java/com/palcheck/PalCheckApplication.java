package com.palcheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PalCheck modular monolith. Module boundaries are ordinary Java packages enforced by ArchUnit tests
 * (see docs/MODULES.md section 2a and docs/adr/0022-phase1-technical-baseline.md).
 */
@SpringBootApplication
public class PalCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(PalCheckApplication.class, args);
    }
}

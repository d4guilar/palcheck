package com.palcheck.platform.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The fingerprint decides whether a repeated idempotency key is "the same request" (replay) or "a different request
 * under the same key" (conflict), so its identity rules matter.
 */
class FingerprintTest {

    @Test
    void theSameOperationAndContentAlwaysGiveTheSameHash() {
        var a = Fingerprint.of("person.create").part("firstName", "Mariana").part("lastName", "Vargas").sha256();
        var b = Fingerprint.of("person.create").part("firstName", "Mariana").part("lastName", "Vargas").sha256();

        assertThat(a).isEqualTo(b).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void aDifferentValueGivesADifferentHash() {
        var a = Fingerprint.of("person.create").part("firstName", "Mariana").sha256();
        var b = Fingerprint.of("person.create").part("firstName", "Marina").sha256();

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void aDifferentOperationGivesADifferentHashEvenWithIdenticalContent() {
        var create = Fingerprint.of("person.create").part("id", "1").sha256();
        var update = Fingerprint.of("person.update").part("id", "1").sha256();

        assertThat(create).isNotEqualTo(update);
    }

    @Test
    void absentIsDistinctFromEmptyBecauseAPatchTreatsThemDifferently() {
        var absent = Fingerprint.of("op").part("middleName", null).sha256();
        var empty = Fingerprint.of("op").part("middleName", "").sha256();

        assertThat(absent).isNotEqualTo(empty);
    }

    @Test
    void differentSplitsOfTheSameCharactersCannotCollide() {
        var a = Fingerprint.of("op").part("first", "ab").part("second", "c").sha256();
        var b = Fingerprint.of("op").part("first", "a").part("second", "bc").sha256();
        var c = Fingerprint.of("op").part("firsta", "b").part("second", "c").sha256();

        assertThat(a).isNotEqualTo(b).isNotEqualTo(c);
        assertThat(b).isNotEqualTo(c);
    }

    @Test
    void separatorCharactersInsideValuesDoNotForgeAnotherStructure() {
        var honest = Fingerprint.of("op").part("a", "1").part("b", "2").sha256();
        var forged = Fingerprint.of("op").part("a", "1;1:b=1:2").sha256();

        assertThat(honest).isNotEqualTo(forged);
    }

    @Test
    void theOrderOfPartsIsPartOfTheIdentity() {
        var ab = Fingerprint.of("op").part("a", "1").part("b", "2").sha256();
        var ba = Fingerprint.of("op").part("b", "2").part("a", "1").sha256();

        // Callers add parts in one fixed order; that order, not the client's JSON field order, defines the identity.
        assertThat(ab).isNotEqualTo(ba);
    }
}

package com.palcheck.workforce.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** JSON merge-patch semantics: absent leaves a field alone, a value sets it, null clears an optional one. */
class PersonPatchTest {

    private static final PersonData CURRENT =
            new PersonData("Mariana", "Isabel", "Vargas Mora", "Mari", "mariana.vargas@example.test", "es-CR");

    private static PersonPatch patch(Patch<String> first, Patch<String> middle, Patch<String> last,
            Patch<String> preferred, Patch<String> email, Patch<String> locale) {
        return new PersonPatch(first, middle, last, preferred, email, locale);
    }

    @Test
    void anEmptyPatchChangesNothing() {
        var result = PersonPatch.none().applyTo(CURRENT);

        assertThat(result.data()).isEqualTo(CURRENT);
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void aValueSetsAndOtherFieldsAreUntouched() {
        var result = patch(Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
                Patch.set("  New.Address@Example.test "), Patch.absent()).applyTo(CURRENT);

        assertThat(result.data().workEmail()).isEqualTo("new.address@example.test");
        assertThat(result.data().firstName()).isEqualTo("Mariana");
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void anOptionalFieldCanBeCleared() {
        var result = patch(Patch.absent(), Patch.clear(), Patch.absent(), Patch.clear(), Patch.absent(), Patch.absent())
                .applyTo(CURRENT);

        assertThat(result.data().middleName()).isNull();
        assertThat(result.data().preferredName()).isNull();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void aBlankValueForAnOptionalFieldClearsItAfterNormalization() {
        var result = patch(Patch.absent(), Patch.set("   "), Patch.absent(), Patch.absent(), Patch.absent(),
                Patch.absent()).applyTo(CURRENT);

        assertThat(result.data().middleName()).isNull();
    }

    @Test
    void aRequiredFieldCannotBeClearedAndKeepsItsValue() {
        var result = patch(Patch.clear(), Patch.absent(), Patch.clear(), Patch.absent(), Patch.clear(), Patch.clear())
                .applyTo(CURRENT);

        assertThat(result.violations()).extracting(v -> v.field() + ":" + v.code())
                .containsExactlyInAnyOrder("firstName:REQUIRED", "lastName:REQUIRED", "workEmail:REQUIRED",
                        "locale:REQUIRED");
        assertThat(result.data()).isEqualTo(CURRENT);
    }

    @Test
    void thePatchedResultIsRevalidatedByTheSameRulesAsCreation() {
        var result = patch(Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.set("nope"),
                Patch.absent()).applyTo(CURRENT);

        assertThat(result.violations()).isEmpty(); // applying does not validate...
        assertThat(result.data().validate("")).extracting(v -> v.field() + ":" + v.code())
                .containsExactly("workEmail:INVALID_FORMAT"); // ...the caller does, with the creation rules
    }

    @Test
    void thePatchStatesAreDistinct() {
        assertThat(Patch.<String>absent().isAbsent()).isTrue();
        assertThat(Patch.<String>clear().isClear()).isTrue();
        assertThat(Patch.set("x").isSet()).isTrue();
        assertThat(Patch.<String>absent().applyTo("keep")).isEqualTo("keep");
        assertThat(Patch.<String>clear().applyTo("gone")).isNull();
        assertThat(Patch.set("new").applyTo("old")).isEqualTo("new");
    }
}

package com.palcheck.workforce.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.palcheck.shared.FieldViolation;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The identity rules that create and patch share. Fictional names only. */
class PersonDataTest {

    private static PersonData valid() {
        return new PersonData("Mariana", null, "Vargas Mora", null, "mariana.vargas@example.test", "es-CR");
    }

    private static List<String> codes(List<FieldViolation> violations) {
        return violations.stream().map(v -> v.field() + ":" + v.code()).toList();
    }

    @Test
    void aValidPersonHasNoViolations() {
        assertThat(valid().normalized().validate("")).isEmpty();
    }

    @Test
    void normalizationTrimsLowercasesTheEmailAndTurnsBlankOptionalsIntoNull() {
        var raw = new PersonData("  Mariana ", "   ", " Vargas Mora", "", "  Mariana.Vargas@Example.TEST ", " es-CR ");

        var normalized = raw.normalized();

        assertThat(normalized.firstName()).isEqualTo("Mariana");
        assertThat(normalized.middleName()).isNull();
        assertThat(normalized.preferredName()).isNull();
        assertThat(normalized.workEmail()).isEqualTo("mariana.vargas@example.test");
        assertThat(normalized.locale()).isEqualTo("es-CR");
    }

    @Test
    void accentedNamesCompareEqualWhetherTypedComposedOrDecomposed() {
        String composed = "Mar\u00EDa"; // i-acute as one character
        String decomposed = "Mari\u0301a"; // i followed by a combining acute accent

        var a = new PersonData(composed, null, "Solís", null, "a@example.test", "es").normalized();
        var b = new PersonData(decomposed, null, "Solís", null, "a@example.test", "es").normalized();

        assertThat(a).isEqualTo(b);
    }

    @Test
    void requiredFieldsAreReportedTogetherRatherThanOneAtATime() {
        var empty = new PersonData(null, null, null, null, null, null).validate("person.");

        assertThat(codes(empty)).containsExactlyInAnyOrder(
                "person.firstName:REQUIRED", "person.lastName:REQUIRED", "person.workEmail:REQUIRED",
                "person.locale:REQUIRED");
    }

    @Test
    void lengthIsCountedInCharactersNotUtf16Units() {
        // 100 supplementary-plane characters are 200 UTF-16 units but exactly at the limit.
        String atLimit = "😀".repeat(PersonData.FIRST_NAME_MAX);
        String overLimit = atLimit + "x";

        assertThat(new PersonData(atLimit, null, "V", null, "a@example.test", "en").validate("")).isEmpty();
        assertThat(codes(new PersonData(overLimit, null, "V", null, "a@example.test", "en").validate("")))
                .containsExactly("firstName:TOO_LONG");
    }

    @Test
    void controlCharactersAreRejectedSoTheyCannotReachLogsOrExports() {
        var withNewline = new PersonData("Ma\nriana", null, "V", null, "a@example.test", "en");

        assertThat(codes(withNewline.validate(""))).containsExactly("firstName:INVALID_FORMAT");
    }

    @Test
    void emailMustLookLikeAnAddressAndFitTheLimit() {
        assertThat(codes(new PersonData("A", null, "B", null, "not-an-email", "en").validate("")))
                .containsExactly("workEmail:INVALID_FORMAT");
        assertThat(codes(new PersonData("A", null, "B", null, "a b@example.test", "en").validate("")))
                .containsExactly("workEmail:INVALID_FORMAT");
        String tooLong = "a".repeat(PersonData.EMAIL_MAX) + "@example.test";
        assertThat(codes(new PersonData("A", null, "B", null, tooLong, "en").validate("")))
                .containsExactly("workEmail:TOO_LONG");
    }

    @Test
    void localeIsAPresentationChoiceFromAFixedSet() {
        assertThat(codes(new PersonData("A", null, "B", null, "a@example.test", "fr").validate("")))
                .containsExactly("locale:UNSUPPORTED_VALUE");
    }

    @Test
    void displayNamePrefersThePreferredNameThenTheGivenName() {
        assertThat(PersonData.displayName("Mariana", null, "Vargas Mora")).isEqualTo("Mariana Vargas Mora");
        assertThat(PersonData.displayName("Mariana", "Mari", "Vargas Mora")).isEqualTo("Mari Vargas Mora");
        assertThat(PersonData.displayName("Mariana", "  ", "Vargas Mora")).isEqualTo("Mariana Vargas Mora");
    }

    @Test
    void diffNamesOnlyTheFieldsThatChanged() {
        var before = valid();
        var after = new PersonData("Mariana", "Isabel", "Vargas Mora", null, "mariana.v@example.test", "es-CR");

        assertThat(before.diff(after)).containsExactlyInAnyOrder(
                new FieldChange("middleName", null, "Isabel"),
                new FieldChange("workEmail", "mariana.vargas@example.test", "mariana.v@example.test"));
        assertThat(before.diff(before)).isEmpty();
    }

    @Test
    void creationHistoryListsEveryFieldThatHasAValue() {
        assertThat(valid().asCreation()).extracting(FieldChange::field)
                .containsExactlyInAnyOrder("firstName", "lastName", "workEmail", "locale");
    }
}

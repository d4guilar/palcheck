package com.palcheck.workforce.domain;

import com.palcheck.shared.FieldViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The editable identity fields of a Person. No personal email, birth date, national ID or other sensitive data is
 * held (data minimization); {@code lastName} may contain several family names.
 */
public record PersonData(String firstName, String middleName, String lastName, String preferredName,
        String workEmail, String locale) {

    public static final int FIRST_NAME_MAX = 100;
    public static final int MIDDLE_NAME_MAX = 100;
    public static final int LAST_NAME_MAX = 150;
    public static final int PREFERRED_NAME_MAX = 100;
    public static final int EMAIL_MAX = 254;
    /** Presentation locales the application can render. Locale is presentation, never jurisdiction. */
    public static final Set<String> SUPPORTED_LOCALES = Set.of("en", "es", "es-CR");

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** Trimmed, NFC-normalized text; blank optionals become null; the work email is lowercased. */
    public PersonData normalized() {
        String email = Rules.clean(workEmail);
        return new PersonData(Rules.clean(firstName), Rules.clean(middleName), Rules.clean(lastName),
                Rules.clean(preferredName), email == null ? null : email.toLowerCase(Locale.ROOT),
                Rules.clean(locale));
    }

    /** Validates an already-normalized value. {@code prefix} is prepended to field names (for example {@code person.}). */
    public List<FieldViolation> validate(String prefix) {
        List<FieldViolation> out = new ArrayList<>();
        Rules.requiredText(out, prefix + "firstName", firstName, FIRST_NAME_MAX);
        Rules.optionalText(out, prefix + "middleName", middleName, MIDDLE_NAME_MAX);
        Rules.requiredText(out, prefix + "lastName", lastName, LAST_NAME_MAX);
        Rules.optionalText(out, prefix + "preferredName", preferredName, PREFERRED_NAME_MAX);

        if (workEmail == null) {
            out.add(new FieldViolation(prefix + "workEmail", Rules.REQUIRED, "A work email is required."));
        } else if (workEmail.length() > EMAIL_MAX) {
            out.add(new FieldViolation(prefix + "workEmail", Rules.TOO_LONG, "Use at most " + EMAIL_MAX + " characters."));
        } else if (!EMAIL.matcher(workEmail).matches()) {
            out.add(new FieldViolation(prefix + "workEmail", Rules.INVALID_FORMAT, "Enter a valid email address."));
        }

        if (locale == null) {
            out.add(new FieldViolation(prefix + "locale", Rules.REQUIRED, "A locale is required."));
        } else if (!SUPPORTED_LOCALES.contains(locale)) {
            out.add(new FieldViolation(prefix + "locale", Rules.UNSUPPORTED_VALUE,
                    "Supported locales: " + String.join(", ", SUPPORTED_LOCALES.stream().sorted().toList()) + "."));
        }
        return out;
    }

    /** How a person is shown in lists: the preferred name if there is one, otherwise the given name, then the family name. */
    public static String displayName(String firstName, String preferredName, String lastName) {
        String given = preferredName != null && !preferredName.isBlank() ? preferredName : firstName;
        return (given + " " + lastName).strip();
    }

    public String displayName() {
        return displayName(firstName, preferredName, lastName);
    }

    public List<FieldChange> diff(PersonData after) {
        List<FieldChange> out = new ArrayList<>();
        FieldChange.addIfChanged(out, "firstName", firstName, after.firstName);
        FieldChange.addIfChanged(out, "middleName", middleName, after.middleName);
        FieldChange.addIfChanged(out, "lastName", lastName, after.lastName);
        FieldChange.addIfChanged(out, "preferredName", preferredName, after.preferredName);
        FieldChange.addIfChanged(out, "workEmail", workEmail, after.workEmail);
        FieldChange.addIfChanged(out, "locale", locale, after.locale);
        return out;
    }

    /** All fields of a newly created Person, for the creation history entry. */
    public List<FieldChange> asCreation() {
        return new PersonData(null, null, null, null, null, null).diff(this);
    }
}

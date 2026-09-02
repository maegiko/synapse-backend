package com.synapse.backend.shared.validation;

import java.util.Locale;

/**
 * Normalises request text before its constraints are checked.
 *
 * <p>Every DTO normalises in its compact constructor, which runs before validation. That
 * ordering is the point: a field is trimmed first and then measured, so the value a
 * constraint accepts is the value that gets persisted, and padding cannot smuggle a name
 * past a minimum length or leave a title stored with the spaces the user did not mean to
 * type.</p>
 */
public final class RequestText {

    /**
     * Trims a value, leaving null alone.
     *
     * @param value the value as the client sent it, possibly null.
     * @return the trimmed value, or null.
     */
    public static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * Trims and lowercases an email address, leaving null alone.
     *
     * <p>Addresses are stored and looked up in this form, so normalising here means the
     * {@code @Email} and length constraints check the address that is actually used.</p>
     *
     * @param email the address as the client sent it, possibly null.
     * @return the normalised address, or null.
     */
    public static String normalisedEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Trims a value and cuts it to a maximum length.
     *
     * <p>This is for text the user did not type and cannot correct, which is to say text the
     * LLM generated. Such text is bounded by the same limit its edit endpoint enforces, so a
     * generated note, deck or quiz can always be saved again unchanged. Rejecting an
     * over-long generation instead would spend the model call and hand the user an error
     * about content they never wrote.</p>
     *
     * @param value the generated value, possibly null.
     * @param max the limit from {@link ValidationLimits}.
     * @return the trimmed value, cut to {@code max} characters, or null.
     */
    public static String clamped(String value, int max) {
        String trimmed = trimmed(value);

        if (trimmed == null || trimmed.length() <= max)
            return trimmed;

        return trimmed.substring(0, max);
    }

    private RequestText() {
    }

}

package com.synapse.backend.shared.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The one rule for a person's name, applied on registration and on the profile edit.
 *
 * <p>A name is letters and the three characters that join them: a space, a hyphen as in
 * Jean-Pierre, and an apostrophe as in O'Brien. Both apostrophe forms are accepted because a
 * phone keyboard produces the curly one. It must begin and end with a letter, so a lone
 * hyphen or a trailing quote is not a name.</p>
 *
 * <p>Letters means {@code \p{L}}, every script's letters, not {@code A-Z}. Restricting to
 * ASCII would reject José, Müller, Ngô, Владимир and 陳 — ordinary names whose owners would
 * simply be unable to register. {@code \p{M}} accompanies it so that an accent submitted as a
 * separate combining character counts as part of the letter it sits on.</p>
 *
 * <p>What this excludes is what it is for: digits, punctuation, emoji, and the angle brackets
 * and {@code @} that show up when a field is being probed rather than filled in.</p>
 */
@Size(min = ValidationLimits.FULL_NAME_MIN, max = ValidationLimits.FULL_NAME_MAX)
@Pattern(
    regexp = "[\\p{L}\\p{M}][\\p{L}\\p{M} '’-]*[\\p{L}\\p{M}]",
    message = "must contain only letters, spaces, hyphens and apostrophes"
)
@Documented
@Constraint(validatedBy = {})
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface FullName {

    /**
     * Unused: the composing constraints each report their own message.
     *
     * @return the message template.
     */
    String message() default "is not a valid name";

    /**
     * The validation groups this constraint belongs to.
     *
     * @return the groups.
     */
    Class<?>[] groups() default {};

    /**
     * The payload associated with this constraint.
     *
     * @return the payload.
     */
    Class<? extends Payload>[] payload() default {};

}

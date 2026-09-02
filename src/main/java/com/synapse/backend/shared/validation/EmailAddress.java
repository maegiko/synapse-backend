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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The one rule for an email address, applied wherever one is submitted.
 *
 * <p>This deliberately replaces {@code @Email} rather than adding to it. Bean Validation's
 * built-in constraint asks only that there be something either side of an {@code @}, so it
 * accepts {@code asdfasdf@asdf}, {@code a@b}, {@code test@localhost}, {@code ada@123} and
 * {@code ada@[127.0.0.1]}. None of those can receive the verification mail this application
 * sends, so accepting them means taking a registration that can never be completed and
 * spending a provider call to discover it.</p>
 *
 * <p>What is required here is a deliverable shape: a dot-atom local part of at most 64
 * characters, then a domain of dot-separated labels that do not begin or end with a hyphen,
 * ending in an alphabetic top-level domain of at least two characters. Consecutive dots, a
 * leading or trailing dot, a bare hostname and an address literal are all refused.</p>
 *
 * <p>Unlike {@link FullName}, this is ASCII only, and the difference is deliberate. A name is
 * a display string and belongs to its owner, so it takes every script. An address is a
 * routing identifier that has to survive SMTP, where the internationalised form is the
 * punycode one; a client that accepts a Unicode domain here would be accepting something the
 * mail provider cannot send to.</p>
 */
@NotBlank
@Size(max = ValidationLimits.EMAIL_MAX)
@Pattern(
    regexp = "(?=[^@]{1,64}@)"
        + "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
        + "@"
        + "(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}",
    message = "must be a valid email address"
)
@Documented
@Constraint(validatedBy = {})
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface EmailAddress {

    /**
     * Unused: the composing constraints each report their own message.
     *
     * @return the message template.
     */
    String message() default "is not a valid email address";

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

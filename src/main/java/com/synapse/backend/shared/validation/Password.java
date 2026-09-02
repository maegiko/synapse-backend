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
import jakarta.validation.constraints.Size;

/**
 * The one password rule, applied wherever a password is submitted.
 *
 * <p>Registering, logging in, changing a password and resetting a password all carry this,
 * so the rule cannot be tightened on one path and left behind on another.</p>
 *
 * <p>The byte bound is not decoration. BCrypt refuses to hash a secret over
 * {@link ValidationLimits#PASSWORD_MAX_BYTES} bytes, and a character count cannot see that:
 * 64 characters of a three byte script encode to 192 bytes, clear the character bound, and
 * would otherwise reach the encoder and fail there as a server error.</p>
 *
 * <p>The composed constraints report their own messages rather than being collapsed into
 * one, so a client is told which rule it broke.</p>
 */
@NotBlank
@Size(min = ValidationLimits.PASSWORD_MIN, max = ValidationLimits.PASSWORD_MAX)
@MaxUtf8Bytes(ValidationLimits.PASSWORD_MAX_BYTES)
@Documented
@Constraint(validatedBy = {})
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface Password {

    /**
     * Unused: the composing constraints each report their own message.
     *
     * @return the message template.
     */
    String message() default "is not a valid password";

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

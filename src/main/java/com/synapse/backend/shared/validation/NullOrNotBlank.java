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

/**
 * The constraint a field on a partial update carries: absent is allowed, present but empty
 * is not.
 *
 * <p>On a PATCH body a null field means "leave this alone", so {@code @NotBlank} is too
 * strong and {@code @Size} alone is too weak. This sits between them, and reports the same
 * {@code must not be blank} wording the equivalent required field reports, so a client sees
 * one message for one mistake whichever endpoint it used.</p>
 */
@Documented
@Constraint(validatedBy = NullOrNotBlankValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface NullOrNotBlank {

    /**
     * The message reported when the value is present but blank.
     *
     * @return the message template.
     */
    String message() default "must not be blank";

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

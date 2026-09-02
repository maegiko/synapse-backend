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
 * Bounds a string by its encoded size rather than its character count.
 *
 * <p>{@code @Size} counts UTF-16 characters, which is the wrong unit whenever the value is
 * handed to something that counts bytes. A 64 character password of three byte characters is
 * 192 bytes, so it satisfies {@code @Size(max = 64)} and is still refused by BCrypt.</p>
 *
 * <p>A null value passes, matching how the built-in size constraints behave.</p>
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface MaxUtf8Bytes {

    /**
     * The largest number of UTF-8 bytes the value may encode to.
     *
     * @return the inclusive maximum size in bytes.
     */
    int value();

    /**
     * The message reported when the value is too large.
     *
     * @return the message template.
     */
    String message() default "must be at most {value} bytes long";

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

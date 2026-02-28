package com.example.springmvccontroller.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark fields that should be masked.
 * Specifies which MaskFunctions enum value to use for masking.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MaskedField {
    /**
     * The MaskFunctions enum value to use for masking this field.
     */
    String maskFunctionType() default "";
}


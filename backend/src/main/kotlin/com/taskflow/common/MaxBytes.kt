package com.taskflow.common

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/** String must be at most [value] UTF-8 bytes. BCrypt only uses 72 bytes, so longer passwords would be silently cut. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [MaxBytesValidator::class])
annotation class MaxBytes(
    val value: Int,
    val message: String = "Must be at most {value} bytes",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class MaxBytesValidator : ConstraintValidator<MaxBytes, String?> {
    private var max = 0

    override fun initialize(annotation: MaxBytes) {
        max = annotation.value
    }

    override fun isValid(value: String?, context: ConstraintValidatorContext) =
        value == null || value.toByteArray(Charsets.UTF_8).size <= max
}

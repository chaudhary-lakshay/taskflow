package com.taskflow.auth

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * The one password rule: 8 or more characters, at most 72 UTF-8 bytes (BCrypt ignores the rest, so
 * we reject instead of truncating). Used by registration (via @ValidPassword) and the demo seeder.
 */
object PasswordPolicy {
    const val MIN_LENGTH = 8
    const val MAX_BYTES = 72

    /** Null when the password is acceptable, otherwise a message that never contains the password. */
    fun violation(password: String?): String? = when {
        password.isNullOrBlank() -> "Password is required"
        password.codePointCount(0, password.length) < MIN_LENGTH -> "Password must be at least $MIN_LENGTH characters"
        password.toByteArray(Charsets.UTF_8).size > MAX_BYTES -> "Password must be at most $MAX_BYTES bytes"
        else -> null
    }
}

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ValidPasswordValidator::class])
annotation class ValidPassword(
    val message: String = "Invalid password",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class ValidPasswordValidator : ConstraintValidator<ValidPassword, String?> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        val problem = PasswordPolicy.violation(value) ?: return true
        context.disableDefaultConstraintViolation()
        context.buildConstraintViolationWithTemplate(problem.replace("{", "").replace("}", "")).addConstraintViolation()
        return false
    }
}

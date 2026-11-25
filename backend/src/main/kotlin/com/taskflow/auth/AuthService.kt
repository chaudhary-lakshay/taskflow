package com.taskflow.auth

import com.taskflow.security.IssuedToken
import com.taskflow.security.TokenService
import com.taskflow.user.User
import com.taskflow.user.UserRepository
import org.hibernate.exception.ConstraintViolationException
import org.postgresql.util.PSQLException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class LoginResult(val user: User, val token: IssuedToken)

@Service
class AuthService(
    private val users: UserRepository,
    private val encoder: PasswordEncoder,
    private val tokens: TokenService,
) {
    // Compared against when the email is unknown, so every login does exactly one hash comparison.
    // Built here, not lazily, so the first unknown-email login is not the slow one.
    private val dummyHash: String = encoder.encode("not-a-real-password")

    @Transactional
    fun register(name: String, email: String, password: String): User {
        val normalized = User.normalizeEmail(email)
        if (users.existsByEmail(normalized)) throw DuplicateEmailException()
        try {
            return users.saveAndFlush(User(name.trim(), normalized, encoder.encode(password)))
        } catch (e: DataIntegrityViolationException) {
            // Two registrations racing past existsByEmail: the unique constraint decides.
            if (e.isEmailUniqueViolation()) throw DuplicateEmailException()
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun login(email: String, password: String): LoginResult {
        val user = users.findByEmail(User.normalizeEmail(email))
        val matches = encoder.matches(password, user?.passwordHash ?: dummyHash)
        if (user == null || !matches) throw InvalidCredentialsException()
        return LoginResult(user, tokens.issue(user.id!!))
    }

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
        const val EMAIL_CONSTRAINT = "uq_users_email"
    }

    /**
     * True only for a unique violation (SQLState 23505) of uq_users_email, read from typed data in the
     * cause chain: the PostgreSQL server error's constraint name, or Hibernate's constraintName.
     * Message text is never consulted, and any other integrity violation stays an error.
     */
    private fun Throwable.isEmailUniqueViolation(): Boolean =
        generateSequence(this) { it.cause.takeIf { c -> c !== it } }.take(20).any { t ->
            when (t) {
                is PSQLException ->
                    t.sqlState == UNIQUE_VIOLATION && t.serverErrorMessage?.constraint == EMAIL_CONSTRAINT
                is ConstraintViolationException ->
                    t.sqlState == UNIQUE_VIOLATION && t.constraintName.equals(EMAIL_CONSTRAINT, ignoreCase = true)
                else -> false
            }
        }
}

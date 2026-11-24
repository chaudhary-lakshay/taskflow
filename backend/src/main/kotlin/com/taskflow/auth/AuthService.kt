package com.taskflow.auth

import com.taskflow.security.IssuedToken
import com.taskflow.security.TokenService
import com.taskflow.user.User
import com.taskflow.user.UserRepository
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
    // Compared against when the email is unknown, so unknown and wrong-password answer in similar time.
    private val dummyHash: String by lazy { encoder.encode("not-a-real-password")!! }

    @Transactional
    fun register(name: String, email: String, password: String): User {
        val normalized = User.normalizeEmail(email)
        if (users.existsByEmail(normalized)) throw DuplicateEmailException()
        try {
            return users.saveAndFlush(User(name.trim(), normalized, encoder.encode(password)!!))
        } catch (e: DataIntegrityViolationException) {
            // Two registrations racing past existsByEmail: the unique constraint decides.
            if (e.message?.contains("uq_users_email") == true) throw DuplicateEmailException()
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
}

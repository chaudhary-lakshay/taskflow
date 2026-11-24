package com.taskflow.auth

import com.taskflow.security.IssuedToken
import com.taskflow.security.TokenService
import com.taskflow.user.User
import com.taskflow.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.sql.SQLException
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import org.postgresql.util.ServerErrorMessage
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder

class AuthServiceTest {
    private val users = mockk<UserRepository>()
    private val encoder = mockk<PasswordEncoder>()
    private val tokens = mockk<TokenService>()
    private lateinit var service: AuthService

    @BeforeEach
    fun setUp() {
        every { encoder.encode(any()) } returns "DUMMY" // the dummy hash is computed when the service is built
        service = AuthService(users, encoder, tokens)
        every { encoder.encode("s3cret-pass") } returns "HASHED"
    }

    private fun stored(email: String = "a@example.test", hash: String = "HASH") =
        User("Ann", email, hash).also { it.id = 7L }

    /** What PostgreSQL + pgjdbc + Hibernate + Spring really throw: DataIntegrityViolation > Hibernate CVE > PSQLException. */
    private fun pgViolation(sqlState: String, constraint: String?, hibernate: Boolean = true): DataIntegrityViolationException {
        val raw = "SERROR\u0000C$sqlState\u0000Mviolation\u0000" + (constraint?.let { "n$it\u0000" } ?: "") + "\u0000"
        val psql = PSQLException(ServerErrorMessage(raw))
        val cause: Throwable =
            if (hibernate) ConstraintViolationException("could not execute statement", psql as SQLException, constraint)
            else psql
        return DataIntegrityViolationException("could not execute statement", cause)
    }

    @Test
    fun `register normalises email, trims name and stores only the hash`() {
        val saved = slot<User>()
        every { users.existsByEmail("ann@example.test") } returns false
        every { users.saveAndFlush(capture(saved)) } answers { saved.captured }

        service.register("  Ann ", "  Ann@Example.TEST ", "s3cret-pass")

        assertEquals("ann@example.test", saved.captured.email)
        assertEquals("Ann", saved.captured.name)
        assertEquals("HASHED", saved.captured.passwordHash)
        assertNotEquals("s3cret-pass", saved.captured.passwordHash)
    }

    @Test
    fun `register rejects an existing email without saving`() {
        every { users.existsByEmail("ann@example.test") } returns true
        assertThrows<DuplicateEmailException> { service.register("Ann", "ANN@example.test", "s3cret-pass") }
        verify(exactly = 0) { users.saveAndFlush(any<User>()) }
    }

    // ---- duplicate classification uses typed data (SQLState + constraint name), never message text ----

    @Test
    fun `Hibernate constraint violation on uq_users_email becomes DuplicateEmailException`() {
        every { users.existsByEmail(any()) } returns false
        every { users.saveAndFlush(any<User>()) } throws pgViolation("23505", "uq_users_email", hibernate = true)
        assertThrows<DuplicateEmailException> { service.register("Ann", "ann@example.test", "s3cret-pass") }
    }

    @Test
    fun `bare PSQLException 23505 on uq_users_email becomes DuplicateEmailException`() {
        every { users.existsByEmail(any()) } returns false
        every { users.saveAndFlush(any<User>()) } throws pgViolation("23505", "uq_users_email", hibernate = false)
        assertThrows<DuplicateEmailException> { service.register("Ann", "ann@example.test", "s3cret-pass") }
    }

    @Test
    fun `a different unique constraint is not reported as a duplicate email`() {
        every { users.existsByEmail(any()) } returns false
        every { users.saveAndFlush(any<User>()) } throws pgViolation("23505", "uq_something_else")
        assertThrows<DataIntegrityViolationException> { service.register("Ann", "ann@example.test", "s3cret-pass") }
    }

    @Test
    fun `the email constraint name with a non-unique SQLState is not a duplicate email`() {
        every { users.existsByEmail(any()) } returns false
        every { users.saveAndFlush(any<User>()) } throws pgViolation("23514", "uq_users_email")
        assertThrows<DataIntegrityViolationException> { service.register("Ann", "ann@example.test", "s3cret-pass") }
        every { users.saveAndFlush(any<User>()) } throws pgViolation("23502", "uq_users_email", hibernate = false)
        assertThrows<DataIntegrityViolationException> { service.register("Ann", "ann@example.test", "s3cret-pass") }
    }

    @Test
    fun `23505 without a constraint name is not a duplicate email`() {
        every { users.existsByEmail(any()) } returns false
        every { users.saveAndFlush(any<User>()) } throws pgViolation("23505", null, hibernate = false)
        assertThrows<DataIntegrityViolationException> { service.register("Ann", "ann@example.test", "s3cret-pass") }
    }

    @Test
    fun `message text alone never classifies a violation`() {
        every { users.existsByEmail(any()) } returns false
        every { users.saveAndFlush(any<User>()) } throws
            DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_users_email\"")
        assertThrows<DataIntegrityViolationException> { service.register("Ann", "ann@example.test", "s3cret-pass") }
    }

    // ---- login ----

    @Test
    fun `login issues a token for the stored user id with exactly one hash comparison`() {
        val user = stored()
        every { users.findByEmail("a@example.test") } returns user
        every { encoder.matches("pw-12345678", "HASH") } returns true
        val token = IssuedToken("jwt-value", 3600)
        every { tokens.issue(7L) } returns token

        val result = service.login(" A@Example.test ", "pw-12345678")

        assertSame(user, result.user)
        assertSame(token, result.token)
        verify(exactly = 1) { encoder.matches(any(), any()) }
    }

    @Test
    fun `login with a wrong password is InvalidCredentials, issues nothing, one comparison`() {
        every { users.findByEmail(any()) } returns stored()
        every { encoder.matches(any(), "HASH") } returns false
        assertThrows<InvalidCredentialsException> { service.login("a@example.test", "wrong") }
        verify(exactly = 0) { tokens.issue(any()) }
        verify(exactly = 1) { encoder.matches(any(), any()) }
    }

    @Test
    fun `login with an unknown email compares once against the dummy hash built at construction`() {
        every { users.findByEmail(any()) } returns null
        every { encoder.matches(any(), "DUMMY") } returns false
        // setUp already built the service: exactly one encode() so far (the dummy hash)
        verify(exactly = 1) { encoder.encode(any()) }
        repeat(3) { assertThrows<InvalidCredentialsException> { service.login("nobody@example.test", "whatever") } }
        verify(exactly = 3) { encoder.matches(any(), "DUMMY") }
        verify(exactly = 1) { encoder.encode(any()) } // never recomputed lazily on the first unknown login
        verify(exactly = 0) { tokens.issue(any()) }
    }
}

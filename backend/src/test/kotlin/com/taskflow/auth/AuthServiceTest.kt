package com.taskflow.auth

import com.taskflow.security.IssuedToken
import com.taskflow.security.TokenService
import com.taskflow.user.User
import com.taskflow.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder

class AuthServiceTest {
    private val users = mockk<UserRepository>()
    private val encoder = mockk<PasswordEncoder>()
    private val tokens = mockk<TokenService>()
    private val service = AuthService(users, encoder, tokens)

    private fun stored(email: String = "a@example.test", hash: String = "HASH") =
        User("Ann", email, hash).also { it.id = 7L }

    @Test
    fun `register normalises email, trims name and stores only the hash`() {
        val saved = slot<User>()
        every { users.existsByEmail("ann@example.test") } returns false
        every { encoder.encode("s3cret-pass") } returns "HASHED"
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

    @Test
    fun `register maps a unique-constraint race to DuplicateEmailException`() {
        every { users.existsByEmail(any()) } returns false
        every { encoder.encode(any()) } returns "HASHED"
        every { users.saveAndFlush(any<User>()) } throws
            DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_users_email\"")
        assertThrows<DuplicateEmailException> { service.register("Ann", "ann@example.test", "s3cret-pass") }
    }

    @Test
    fun `register does not hide other integrity errors`() {
        every { users.existsByEmail(any()) } returns false
        every { encoder.encode(any()) } returns "HASHED"
        every { users.saveAndFlush(any<User>()) } throws DataIntegrityViolationException("null value in column name")
        assertThrows<DataIntegrityViolationException> { service.register("Ann", "ann@example.test", "s3cret-pass") }
    }

    @Test
    fun `login issues a token for the stored user id`() {
        val user = stored()
        every { users.findByEmail("a@example.test") } returns user
        every { encoder.matches("pw-12345678", "HASH") } returns true
        val token = IssuedToken("jwt-value", 3600)
        every { tokens.issue(7L) } returns token

        val result = service.login(" A@Example.test ", "pw-12345678")

        assertSame(user, result.user)
        assertSame(token, result.token)
    }

    @Test
    fun `login with a wrong password is InvalidCredentials and issues nothing`() {
        every { users.findByEmail(any()) } returns stored()
        every { encoder.matches(any(), "HASH") } returns false
        assertThrows<InvalidCredentialsException> { service.login("a@example.test", "wrong") }
        verify(exactly = 0) { tokens.issue(any()) }
    }

    @Test
    fun `login with an unknown email is InvalidCredentials and still runs a hash comparison`() {
        every { users.findByEmail(any()) } returns null
        every { encoder.encode(any()) } returns "DUMMY"
        every { encoder.matches(any(), "DUMMY") } returns false
        assertThrows<InvalidCredentialsException> { service.login("nobody@example.test", "whatever") }
        verify(exactly = 1) { encoder.matches(any(), "DUMMY") }
        verify(exactly = 0) { tokens.issue(any()) }
    }
}

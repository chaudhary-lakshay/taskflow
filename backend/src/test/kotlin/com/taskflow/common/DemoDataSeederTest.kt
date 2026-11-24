package com.taskflow.common

import com.taskflow.project.Project
import com.taskflow.project.ProjectRepository
import com.taskflow.task.TaskRepository
import com.taskflow.user.User
import com.taskflow.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

class DemoDataSeederTest {
    private val users = mockk<UserRepository>(relaxed = true)
    private val projects = mockk<ProjectRepository>(relaxed = true)
    private val tasks = mockk<TaskRepository>(relaxed = true)
    private val encoder = mockk<PasswordEncoder>()

    private fun seeder(pw: String) = DemoDataSeeder(users, projects, tasks, encoder, Clock.systemUTC(), pw)

    @Test
    fun `refuses to seed without a demo password and writes nothing`() {
        listOf("", "   ").forEach { pw ->
            val e = assertThrows<IllegalStateException> { seeder(pw).run(null) }
            assertTrue("DEMO_PASSWORD" in e.message!!)
        }
        verify(exactly = 0) { users.save(any()) }
    }

    @Test
    fun `refuses a demo password that registration would reject, and writes nothing`() {
        listOf("short", "1234567", "x".repeat(73), "é".repeat(37)).forEach { pw ->
            val e = assertThrows<IllegalStateException>("password '${pw.take(10)}'") { seeder(pw).run(null) }
            assertTrue("DEMO_PASSWORD" in e.message!!, e.message)
            assertTrue(pw !in e.message!!, "the password must not be echoed")
        }
        verify(exactly = 0) { users.save(any()) }
        verify(exactly = 0) { encoder.encode(any()) }
    }

    @Test
    fun `accepts passwords at the exact bounds`() {
        every { encoder.encode(any()) } returns "H"
        every { users.existsByEmail(any()) } returns false
        every { users.save(any<User>()) } answers { firstArg() }
        every { projects.save(any<Project>()) } answers { firstArg() }
        listOf("x".repeat(8), "x".repeat(72), "é".repeat(36)).forEach { seeder(it).run(null) }
    }

    @Test
    fun `hashes each account separately (two encode calls)`() {
        var n = 0
        every { encoder.encode("demo-password-1") } answers { "HASH-" + (++n) }
        every { users.existsByEmail(any()) } returns false
        every { projects.save(any<Project>()) } answers { firstArg() }
        val saved = mutableListOf<User>()
        every { users.save(capture(saved)) } answers { firstArg() }

        seeder("demo-password-1").run(null)

        verify(exactly = 2) { encoder.encode("demo-password-1") }
        assertTrue(saved.map { it.passwordHash }.toSet().size == 2, "each account has its own hash: ${saved.map { it.passwordHash }}")
    }

    @Test
    fun `real BCrypt gives the two demo accounts different hashes that both verify`() {
        val real = BCryptPasswordEncoder(4)
        every { users.existsByEmail(any()) } returns false
        val saved = mutableListOf<User>()
        every { users.save(capture(saved)) } answers { firstArg() }
        every { projects.save(any<Project>()) } answers { firstArg() }
        DemoDataSeeder(users, projects, tasks, real, Clock.systemUTC(), "demo-password-1").run(null)
        assertTrue(saved[0].passwordHash != saved[1].passwordHash)
        assertTrue(real.matches("demo-password-1", saved[0].passwordHash) && real.matches("demo-password-1", saved[1].passwordHash))
    }
}

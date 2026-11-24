package com.taskflow.common

import com.taskflow.project.ProjectRepository
import com.taskflow.task.TaskRepository
import com.taskflow.user.UserRepository
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class DemoDataSeederTest {
    private val users = mockk<UserRepository>(relaxed = true)
    private val projects = mockk<ProjectRepository>(relaxed = true)
    private val tasks = mockk<TaskRepository>(relaxed = true)

    @Test
    fun `refuses to seed without a demo password and writes nothing`() {
        listOf("", "   ").forEach { pw ->
            val seeder = DemoDataSeeder(users, projects, tasks, BCryptPasswordEncoder(4), Clock.systemUTC(), pw)
            val e = assertThrows<IllegalStateException> { seeder.run(null) }
            assertTrue("DEMO_PASSWORD" in e.message!!)
        }
        verify(exactly = 0) { users.save(any()) }
        verify(exactly = 0) { users.saveAll(any<Iterable<com.taskflow.user.User>>()) }
    }
}

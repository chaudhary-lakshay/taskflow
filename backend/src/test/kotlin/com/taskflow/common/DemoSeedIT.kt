package com.taskflow.common

import com.taskflow.project.ProjectRepository
import com.taskflow.support.PostgresIT
import com.taskflow.task.TaskRepository
import com.taskflow.task.TaskStatus
import com.taskflow.user.UserRepository
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@ActiveProfiles("demo")
@SpringBootTest(properties = ["taskflow.demo.password=test-only-demo-pw"])
@Transactional
class DemoSeedIT : PostgresIT() {
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var projects: ProjectRepository
    @Autowired lateinit var tasks: TaskRepository
    @Autowired lateinit var encoder: PasswordEncoder
    @Autowired lateinit var seeder: DemoDataSeeder

    @Test
    fun `seeds two demo users with hashed passwords, owned projects and varied tasks`() {
        val alice = users.findByEmail(DemoDataSeeder.ALICE_EMAIL)!!
        val bob = users.findByEmail(DemoDataSeeder.BOB_EMAIL)!!
        assertNotEquals("test-only-demo-pw", alice.passwordHash)
        assertTrue(encoder.matches("test-only-demo-pw", alice.passwordHash))

        val aliceProjects = projects.findAllByOwnerIdOrderById(alice.id!!)
        assertEquals(2, aliceProjects.size)
        assertEquals(1, projects.findAllByOwnerIdOrderById(bob.id!!).size)

        val all = aliceProjects.flatMap { tasks.findAllByProjectIdAndProjectOwnerIdOrderById(it.id!!, alice.id!!) }
        assertEquals(setOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.DONE), all.map { it.status }.toSet())
        assertTrue(all.any { it.dueDate != null && it.dueDate!!.isBefore(LocalDate.now()) && it.status != TaskStatus.DONE }, "an overdue open task")
        assertTrue(all.any { it.assignee?.id == bob.id }, "a task assigned to bob")
        assertTrue(all.any { it.assignee == null }, "an unassigned task")
        // being assigned does not grant access
        val assignedToBob = all.first { it.assignee?.id == bob.id }
        assertNull(tasks.findByIdAndProjectOwnerId(assignedToBob.id!!, bob.id!!))
    }

    @Test
    fun `running the seeder again changes nothing`() {
        val before = users.count() + projects.count() + tasks.count()
        seeder.run(null)
        assertEquals(before, users.count() + projects.count() + tasks.count())
    }
}

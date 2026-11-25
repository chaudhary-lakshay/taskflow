package com.taskflow.common

import com.taskflow.auth.PasswordPolicy
import com.taskflow.project.Project
import com.taskflow.project.ProjectRepository
import com.taskflow.task.Task
import com.taskflow.task.TaskPriority
import com.taskflow.task.TaskRepository
import com.taskflow.task.TaskStatus
import com.taskflow.user.User
import com.taskflow.user.UserRepository
import java.time.Clock
import java.time.LocalDate
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Demo data for local runs only (profile "demo"). Both demo users share the password given in
 * DEMO_PASSWORD; there is no default, so nothing known is ever baked into the repo or database.
 * Idempotent: does nothing if the demo users already exist.
 */
@Component
@Profile("demo")
class DemoDataSeeder(
    private val users: UserRepository,
    private val projects: ProjectRepository,
    private val tasks: TaskRepository,
    private val encoder: PasswordEncoder,
    private val clock: Clock,
    @Value("\${taskflow.demo.password:}") private val demoPassword: String,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments?) {
        check(demoPassword.isNotBlank()) {
            "Profile 'demo' needs a password for the demo users: set the DEMO_PASSWORD environment variable."
        }
        // Same rule as registration, so a demo account never has a password the API would refuse.
        PasswordPolicy.violation(demoPassword)?.let { error("DEMO_PASSWORD is not acceptable: $it") }
        if (users.existsByEmail(ALICE_EMAIL)) return

        // One BCrypt hash per account: separate salts.
        val alice = users.save(User("Alice Demo", ALICE_EMAIL, encoder.encode(demoPassword)))
        val bob = users.save(User("Bob Demo", BOB_EMAIL, encoder.encode(demoPassword)))
        val today = LocalDate.now(clock)

        val launch = projects.save(Project("Website launch", "Demo project owned by Alice", alice))
        val ops = projects.save(Project("Ops checklist", null, alice))
        val bobsProject = projects.save(Project("Bob's private project", "Alice must not see this", bob))

        tasks.saveAll(
            listOf(
                Task("Write launch copy", "Draft the landing page text", TaskStatus.TODO, TaskPriority.HIGH, today.plusDays(3), null, launch),
                Task("Fix broken footer link", null, TaskStatus.TODO, TaskPriority.HIGH, today.minusDays(5), alice, launch),
                Task("Review pricing page", null, TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, today.plusDays(1), bob, launch),
                Task("Set up analytics", null, TaskStatus.DONE, TaskPriority.LOW, today.minusDays(10), alice, launch),
                Task("Rotate API keys", "Overdue and finished: not highlighted", TaskStatus.DONE, TaskPriority.MEDIUM, today.minusDays(2), null, ops),
                Task("Update runbook", null, TaskStatus.IN_PROGRESS, TaskPriority.LOW, null, null, ops),
                Task("Plan Bob's week", null, TaskStatus.TODO, TaskPriority.MEDIUM, today.plusDays(7), bob, bobsProject),
            ),
        )
    }

    companion object {
        const val ALICE_EMAIL = "alice.demo@example.test"
        const val BOB_EMAIL = "bob.demo@example.test"
    }
}

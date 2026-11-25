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

        val launch = projects.save(Project("Website relaunch", "Marketing site refresh for the spring launch", alice))
        val onboarding = projects.save(Project("Team onboarding", null, alice))
        val interviews = projects.save(Project("Customer interviews", "Private notes: Alice must not see this", bob))

        fun task(title: String, status: TaskStatus, priority: TaskPriority, due: Long?, who: User?, project: Project, note: String? = null) =
            Task(title, note, status, priority, due?.let { today.plusDays(it) }, who, project)

        val todo = TaskStatus.TODO
        val doing = TaskStatus.IN_PROGRESS
        val done = TaskStatus.DONE
        val high = TaskPriority.HIGH
        val mid = TaskPriority.MEDIUM
        val low = TaskPriority.LOW
        tasks.saveAll(
            listOf(
                task("Write homepage copy", todo, high, 3, null, launch, "First draft of the hero and feature sections"),
                task("Collect customer testimonials", todo, mid, 9, bob, launch),
                task("Compress hero images", todo, low, 12, alice, launch),
                task("Fix broken footer links", todo, high, -3, alice, launch, "Overdue and still open: highlighted on the board"),
                task("Draft cookie banner text", todo, mid, null, null, launch),
                task("Review pricing page layout", doing, high, 1, bob, launch),
                task("Set up analytics dashboard", doing, mid, 5, alice, launch),
                task("Migrate blog posts", doing, mid, -1, bob, launch),
                task("Choose hosting provider", done, high, -10, alice, launch),
                task("Register new domain", done, low, -14, alice, launch),
                task("Approve brand colours", done, mid, -6, bob, launch, "Overdue date but finished: not highlighted"),
                task("Plan sitemap and redirects", done, mid, -2, null, launch),
                task("Prepare laptop checklist", todo, mid, 4, null, onboarding),
                task("Write first-week guide", todo, high, 7, alice, onboarding),
                task("Schedule intro meetings", doing, low, null, null, onboarding),
                task("Set up shared drive access", done, mid, -2, null, onboarding),
                task("Interview customer A", todo, mid, 2, bob, interviews),
                task("Summarise interview notes", doing, low, 6, bob, interviews),
            ),
        )
    }

    companion object {
        const val ALICE_EMAIL = "alice.demo@example.test"
        const val BOB_EMAIL = "bob.demo@example.test"
    }
}

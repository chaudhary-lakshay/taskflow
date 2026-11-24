package com.taskflow.persistence

import com.taskflow.project.Project
import com.taskflow.project.ProjectRepository
import com.taskflow.support.PostgresIT
import com.taskflow.task.Task
import com.taskflow.task.TaskPriority
import com.taskflow.task.TaskRepository
import com.taskflow.task.TaskStatus
import com.taskflow.user.User
import com.taskflow.user.UserRepository
import jakarta.persistence.EntityManager
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

/** Entities and owner-scoped repository queries against real PostgreSQL (rolled back per test). */
@Transactional
class RepositoryIT : PostgresIT() {
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var projects: ProjectRepository
    @Autowired lateinit var tasks: TaskRepository
    @Autowired lateinit var em: EntityManager

    private fun user(name: String = "N", email: String = "${UUID.randomUUID()}@example.test") =
        users.saveAndFlush(User(name, email, "hash"))

    private fun project(owner: User, name: String = "P") = projects.save(Project(name, null, owner)).also { em.flush() }

    private fun task(project: Project, title: String = "T", assignee: User? = null) =
        tasks.save(Task(title = title, project = project, assignee = assignee)).also { em.flush() }

    private fun reload() = em.clear()

    @Test
    fun `user email is normalised and found case-insensitively by normalised lookup`() {
        val id = UUID.randomUUID()
        val u = user(email = "  Mixed.${id}@Example.TEST ")
        reload()
        assertEquals("mixed.${id}@example.test", users.findById(u.id!!).get().email)
        assertEquals(u.id, users.findByEmail("mixed.${id}@example.test")?.id)
        assertNull(users.findByEmail("nobody-${id}@example.test"))
        assertEquals(true, users.existsByEmail("mixed.${id}@example.test"))
    }

    @Test
    fun `task gets defaults, ids and timestamps, and enums round-trip as strings`() {
        val p = project(user())
        val t = tasks.save(Task("Ship", "desc", TaskStatus.IN_PROGRESS, TaskPriority.HIGH, LocalDate.of(2030, 1, 31), null, p)).also { em.flush() }
        val plain = task(p)
        reload()
        val loaded = em.find(Task::class.java, t.id!!)!!
        assertEquals(TaskStatus.IN_PROGRESS, loaded.status)
        assertEquals(TaskPriority.HIGH, loaded.priority)
        assertEquals(LocalDate.of(2030, 1, 31), loaded.dueDate)
        assertNotNull(loaded.createdAt)
        val defaults = em.find(Task::class.java, plain.id!!)!!
        assertEquals(TaskStatus.TODO, defaults.status)
        assertEquals(TaskPriority.MEDIUM, defaults.priority)
        assertNull(defaults.dueDate)
        assertNull(defaults.assignee)
    }

    @Test
    fun `updating a task moves updatedAt forward and keeps createdAt`() {
        val t = task(project(user()))
        val created = t.createdAt
        val firstUpdate = t.updatedAt
        Thread.sleep(5)
        t.status = TaskStatus.DONE
        tasks.save(t)
        em.flush()
        reload()
        val loaded = em.find(Task::class.java, t.id!!)!!
        assertEquals(created, loaded.createdAt)
        assertEquals(true, loaded.updatedAt.isAfter(firstUpdate))
    }

    @Test
    fun `task relationships load owner, project and assignee`() {
        val owner = user("Owner")
        val helper = user("Helper")
        val t = task(project(owner), assignee = helper)
        reload()
        val loaded = em.find(Task::class.java, t.id!!)!!
        assertEquals("Helper", loaded.assignee!!.name)
        assertEquals(owner.id, loaded.project.owner.id)
    }

    @Test
    fun `project list is owner scoped and ordered by id`() {
        val alice = user()
        val bob = user()
        val a1 = project(alice, "A1")
        val a2 = project(alice, "A2")
        val b1 = project(bob, "B1")
        assertEquals(listOf(a1.id, a2.id), projects.findAllByOwnerIdOrderById(alice.id!!).map { it.id })
        assertEquals(listOf(b1.id), projects.findAllByOwnerIdOrderById(bob.id!!).map { it.id })
    }

    @Test
    fun `project lookup by id requires the owner`() {
        val alice = user()
        val bob = user()
        val p = project(alice)
        assertEquals(p.id, projects.findByIdAndOwnerId(p.id!!, alice.id!!)?.id)
        assertNull(projects.findByIdAndOwnerId(p.id!!, bob.id!!))
    }

    @Test
    fun `task lookup requires the project owner, even for the assignee`() {
        val alice = user()
        val bob = user()
        val t = task(project(alice), assignee = bob)
        assertEquals(t.id, tasks.findByIdAndProjectOwnerId(t.id!!, alice.id!!)?.id)
        assertNull(tasks.findByIdAndProjectOwnerId(t.id!!, bob.id!!))
    }

    @Test
    fun `task list for a project is owner scoped`() {
        val alice = user()
        val bob = user()
        val p = project(alice)
        val t1 = task(p, "one")
        val t2 = task(p, "two")
        task(project(alice), "other project")
        assertEquals(listOf(t1.id, t2.id), tasks.findAllByProjectIdAndProjectOwnerIdOrderById(p.id!!, alice.id!!).map { it.id })
        assertEquals(emptyList<Task>(), tasks.findAllByProjectIdAndProjectOwnerIdOrderById(p.id!!, bob.id!!))
    }
}

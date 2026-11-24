package com.taskflow.persistence

import com.taskflow.support.PostgresIT
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

/** Flyway V1 against real PostgreSQL: constraints, defaults and indexes, using plain SQL. */
class SchemaIT : PostgresIT() {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun user(email: String = "u-${UUID.randomUUID()}@example.test"): Long =
        jdbc.queryForObject(
            "insert into users(name, email, password_hash) values ('N', ?, 'h') returning id",
            Long::class.java,
            email,
        )!!

    private fun project(owner: Long): Long =
        jdbc.queryForObject(
            "insert into projects(name, owner_id) values ('P', ?) returning id",
            Long::class.java,
            owner,
        )!!

    private fun insertTask(
        project: Long?,
        title: String = "T",
        status: String? = null,
        priority: String? = null,
        assignee: Long? = null,
    ) = jdbc.update(
        "insert into tasks(title, status, priority, assignee_id, project_id) values (?, coalesce(?, 'TODO'), coalesce(?, 'MEDIUM'), ?, ?)",
        title, status, priority, assignee, project,
    )

    @Test
    fun `task defaults to TODO, MEDIUM, no assignee, no due date`() {
        val p = project(user())
        jdbc.update("insert into tasks(title, project_id) values ('T', ?)", p)
        val row = jdbc.queryForMap("select status, priority, assignee_id, due_date from tasks where project_id = ?", p)
        assertEquals("TODO", row["status"])
        assertEquals("MEDIUM", row["priority"])
        assertEquals(null, row["assignee_id"])
        assertEquals(null, row["due_date"])
    }

    @Test
    fun `duplicate email is rejected`() {
        val email = "dup-${UUID.randomUUID()}@example.test"
        user(email)
        assertThrows<DataIntegrityViolationException> { user(email) }
    }

    @Test
    fun `email must be stored normalised (lower case, trimmed)`() {
        assertThrows<DataIntegrityViolationException> { user("Upper-${UUID.randomUUID()}@Example.test") }
        assertThrows<DataIntegrityViolationException> { user(" pad-${UUID.randomUUID()}@example.test") }
    }

    @Test
    fun `task title is limited to 120 characters and cannot be blank`() {
        val p = project(user())
        insertTask(p, "x".repeat(120))
        assertThrows<DataIntegrityViolationException> { insertTask(p, "x".repeat(121)) }
        assertThrows<DataIntegrityViolationException> { insertTask(p, "   ") }
    }

    @Test
    fun `status and priority accept only the enum values`() {
        val p = project(user())
        listOf("TODO", "IN_PROGRESS", "DONE").forEach { insertTask(p, status = it) }
        listOf("LOW", "MEDIUM", "HIGH").forEach { insertTask(p, priority = it) }
        assertThrows<DataIntegrityViolationException> { insertTask(p, status = "BLOCKED") }
        assertThrows<DataIntegrityViolationException> { insertTask(p, priority = "URGENT") }
        assertThrows<DataIntegrityViolationException> { insertTask(p, status = "todo") }
    }

    @Test
    fun `foreign keys and not-null columns are enforced`() {
        val owner = user()
        assertThrows<DataIntegrityViolationException> { project(-1L) }
        assertThrows<DataIntegrityViolationException> {
            jdbc.update("insert into projects(name, owner_id) values ('P', null)")
        }
        assertThrows<DataIntegrityViolationException> { insertTask(null) }
        assertThrows<DataIntegrityViolationException> { insertTask(-1L) }
        assertThrows<DataIntegrityViolationException> { insertTask(project(owner), assignee = -1L) }
    }

    @Test
    fun `referenced rows cannot be deleted from under tasks and projects`() {
        val owner = user()
        val assignee = user()
        val p = project(owner)
        insertTask(p, assignee = assignee)
        assertThrows<DataIntegrityViolationException> { jdbc.update("delete from users where id = ?", assignee) }
        assertThrows<DataIntegrityViolationException> { jdbc.update("delete from projects where id = ?", p) }
    }

    @Test
    fun `planned indexes exist`() {
        val names = jdbc.queryForList(
            "select indexname from pg_indexes where schemaname = 'public' and tablename in ('projects','tasks')",
            String::class.java,
        )
        listOf("idx_projects_owner_id", "idx_tasks_project_status_id", "idx_tasks_project_due_date_id", "idx_tasks_project_assignee")
            .forEach { assertTrue(it in names, "missing index $it, have $names") }
    }
}

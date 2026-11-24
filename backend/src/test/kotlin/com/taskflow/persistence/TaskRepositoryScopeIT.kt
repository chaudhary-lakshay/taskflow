package com.taskflow.persistence

import com.taskflow.support.ApiIT
import com.taskflow.task.TaskListQuery
import com.taskflow.task.TaskPriority
import com.taskflow.task.TaskRepository
import com.taskflow.task.TaskSortField
import com.taskflow.task.TaskStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/** The repository itself, on real PostgreSQL: a foreign owner id sees nothing, whatever else is asked. */
class TaskRepositoryScopeIT : ApiIT() {
    @Autowired lateinit var tasks: TaskRepository

    private val a by lazy { person("Alice") }
    private val b by lazy { person("Bob") }
    private val pA by lazy { project(a) }
    private val pB by lazy { project(b) }

    private fun q(status: TaskStatus? = null, priority: TaskPriority? = null, assignee: Long? = null, size: Int = 20, page: Int = 0) =
        TaskListQuery(status, priority, assignee, TaskSortField.DUE_DATE, true, page, size)

    @Test
    fun `findPage with a foreign owner id returns no rows and a zero count for an existing project`() {
        task(pA, "one", "TODO", "HIGH", assignee = b)
        task(pA, "two", "DONE", "LOW")
        task(pA, "three", "TODO", "MEDIUM")

        val own = tasks.findPage(a.id, pA, q())
        assertEquals(3, own.totalElements)
        assertEquals(3, own.content.size)

        val foreign = tasks.findPage(b.id, pA, q())
        assertEquals(0, foreign.totalElements)
        assertEquals(emptyList<Long>(), foreign.content.map { it.id })
        assertEquals(0, foreign.totalPages)
    }

    @Test
    fun `a foreign owner id stays empty with every filter, sort and page combination, even when assigned`() {
        task(pA, "x", "TODO", "HIGH", assignee = b)
        listOf(
            q(), q(status = TaskStatus.TODO), q(priority = TaskPriority.HIGH), q(assignee = b.id),
            q(TaskStatus.TODO, TaskPriority.HIGH, b.id), q(size = 1, page = 0), q(size = 100, page = 3),
        ).forEach {
            val page = tasks.findPage(b.id, pA, it)
            assertEquals(0, page.totalElements, it.toString())
            assertEquals(0, page.content.size, it.toString())
        }
    }

    @Test
    fun `an owner asking for a project id of someone else's project, or a missing id, gets nothing`() {
        task(pA, "mine")
        task(pB, "bobs")
        assertEquals(0, tasks.findPage(a.id, pB, q()).totalElements)
        assertEquals(0, tasks.findPage(a.id, 999999999L, q()).totalElements)
        assertEquals(listOf("bobs"), tasks.findPage(b.id, pB, q()).content.map { it.title })
    }

    @Test
    fun `the other scoped queries also return nothing for a foreign owner id`() {
        val t = task(pA, "x", "TODO", "MEDIUM", today().minusDays(2))
        assertNull(tasks.findByIdAndProjectOwnerId(t, b.id))
        assertEquals(t, tasks.findByIdAndProjectOwnerId(t, a.id)?.id)
        assertEquals(emptyList<Long>(), tasks.findAllByProjectIdAndProjectOwnerIdOrderById(pA, b.id).map { it.id })
        assertEquals(emptyList<Long>(), tasks.countByStatus(pA, b.id).map { it.n })
        assertEquals(0L, tasks.countOverdue(pA, b.id, today()))
        assertEquals(1L, tasks.countOverdue(pA, a.id, today()))
    }
}

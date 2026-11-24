package com.taskflow.task

import com.taskflow.common.NotFoundException
import com.taskflow.common.RequestValidationException
import com.taskflow.project.Project
import com.taskflow.project.ProjectRepository
import com.taskflow.user.User
import com.taskflow.user.UserRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification

/** Service rules with a fixed clock: today is 2026-11-24 (UTC). */
class TaskServiceTest {
    private val today = LocalDate.of(2026, 11, 24)
    private val clock = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC)
    private val projects = mockk<ProjectRepository>()
    private val tasks = mockk<TaskRepository>()
    private val users = mockk<UserRepository>()
    private val service = TaskService(projects, tasks, users, clock)

    private val owner = User("Owner", "o@example.test", "h").also { it.id = 1L }
    private val project = Project("P", null, owner).also { it.id = 10L }
    private fun task(due: LocalDate? = null, status: TaskStatus = TaskStatus.TODO) =
        Task(title = "T", project = project, dueDate = due, status = status).also { it.id = 5L }

    private fun create(title: String? = "T", due: LocalDate? = null, assigneeId: Long? = null) =
        CreateTaskRequest(title = title, dueDate = due, assigneeId = assigneeId)

    private fun update(due: LocalDate? = null, assigneeId: Long? = null) =
        UpdateTaskRequest(title = " New ", status = TaskStatus.DONE, priority = TaskPriority.HIGH, dueDate = due, assigneeId = assigneeId)

    private fun fieldsOf(block: () -> Unit) = assertThrows<RequestValidationException> { block() }.fieldErrors.keys

    // ---- create ----

    @Test
    fun `create saves under the owned project with defaults, a trimmed title and the injected clock for overdue`() {
        every { projects.findByIdAndOwnerId(10L, 1L) } returns project
        val saved = slot<Task>()
        every { tasks.save(capture(saved)) } answers { saved.captured.also { it.id = 77L } }

        val dto = service.create(10L, 1L, create("  Write  "))

        assertEquals("Write", saved.captured.title)
        assertEquals(TaskStatus.TODO, saved.captured.status)
        assertEquals(TaskPriority.MEDIUM, saved.captured.priority)
        assertNull(saved.captured.assignee)
        assertEquals(project, saved.captured.project)
        assertEquals(77L, dto.id)
        assertFalse(dto.overdue)
    }

    @Test
    fun `create on a foreign or missing project is NotFound and saves nothing`() {
        every { projects.findByIdAndOwnerId(10L, 2L) } returns null
        assertThrows<NotFoundException> { service.create(10L, 2L, create()) }
        verify(exactly = 0) { tasks.save(any<Task>()) }
    }

    @Test
    fun `create due date - today, future and none are accepted, yesterday is rejected`() {
        every { projects.findByIdAndOwnerId(10L, 1L) } returns project
        every { tasks.save(any<Task>()) } answers { firstArg<Task>().also { it.id = 1L } }
        listOf(today, today.plusDays(1), null).forEach { service.create(10L, 1L, create(due = it)) }
        assertEquals(setOf("dueDate"), fieldsOf { service.create(10L, 1L, create(due = today.minusDays(1))) })
        verify(exactly = 3) { tasks.save(any<Task>()) }
    }

    @Test
    fun `create with an unknown assignee is a field error, an existing one is stored`() {
        every { projects.findByIdAndOwnerId(10L, 1L) } returns project
        every { users.findById(99L) } returns Optional.empty()
        assertEquals(setOf("assigneeId"), fieldsOf { service.create(10L, 1L, create(assigneeId = 99L)) })
        verify(exactly = 0) { tasks.save(any<Task>()) }

        val helper = User("Helper", "h@example.test", "h").also { it.id = 3L }
        every { users.findById(3L) } returns Optional.of(helper)
        val saved = slot<Task>()
        every { tasks.save(capture(saved)) } answers { saved.captured.also { it.id = 1L } }
        service.create(10L, 1L, create(assigneeId = 3L))
        assertEquals(helper, saved.captured.assignee)
    }

    @Test
    fun `create reports a past date and an unknown assignee together`() {
        every { projects.findByIdAndOwnerId(10L, 1L) } returns project
        every { users.findById(99L) } returns Optional.empty()
        assertEquals(setOf("dueDate", "assigneeId"), fieldsOf { service.create(10L, 1L, create(due = today.minusDays(2), assigneeId = 99L)) })
    }

    // ---- update (D04) ----

    @Test
    fun `update keeps an unchanged past due date`() {
        val past = today.minusDays(10)
        every { tasks.findByIdAndProjectOwnerId(5L, 1L) } returns task(due = past)
        every { tasks.flush() } just Runs

        assertEquals(past, service.update(5L, 1L, update(due = past)).dueDate)
    }

    @Test
    fun `update rejects a changed past date`() {
        val past = today.minusDays(10)
        every { tasks.findByIdAndProjectOwnerId(5L, 1L) } returns task(due = past)
        every { tasks.flush() } just Runs
        assertEquals(setOf("dueDate"), fieldsOf { service.update(5L, 1L, update(due = past.plusDays(1))) })
        assertEquals(setOf("dueDate"), fieldsOf { service.update(5L, 1L, update(due = today.minusDays(1))) })
    }

    @Test
    fun `update accepts today, a future date and clearing the date`() {
        every { tasks.findByIdAndProjectOwnerId(5L, 1L) } answers { task(due = today.minusDays(10)) }
        every { tasks.flush() } just Runs
        listOf(today, today.plusDays(3), null).forEach { service.update(5L, 1L, update(due = it)) }
    }

    @Test
    fun `update sets every editable field, clears the assignee, and flushes`() {
        val t = task(due = today.plusDays(1)).also { it.assignee = User("Old", "x@example.test", "h").also { u -> u.id = 8L } }
        every { tasks.findByIdAndProjectOwnerId(5L, 1L) } returns t
        every { tasks.flush() } just Runs
        val dto = service.update(5L, 1L, update(due = null, assigneeId = null))
        assertEquals("New", t.title)
        assertEquals(TaskStatus.DONE, t.status)
        assertEquals(TaskPriority.HIGH, t.priority)
        assertNull(t.dueDate)
        assertNull(t.assignee)
        assertNull(dto.assignee)
        verify(exactly = 1) { tasks.flush() }
    }

    @Test
    fun `update with an unknown assignee changes nothing`() {
        val t = task()
        every { tasks.findByIdAndProjectOwnerId(5L, 1L) } returns t
        every { users.findById(99L) } returns Optional.empty()
        assertEquals(setOf("assigneeId"), fieldsOf { service.update(5L, 1L, update(assigneeId = 99L)) })
        assertEquals("T", t.title)
    }

    @Test
    fun `update, status change and delete of a foreign task are NotFound and touch nothing`() {
        every { tasks.findByIdAndProjectOwnerId(5L, 2L) } returns null
        assertThrows<NotFoundException> { service.update(5L, 2L, update()) }
        assertThrows<NotFoundException> { service.changeStatus(5L, 2L, TaskStatus.DONE) }
        assertThrows<NotFoundException> { service.delete(5L, 2L) }
        verify(exactly = 0) { tasks.delete(any<Task>()) }
        verify(exactly = 0) { tasks.flush() }
    }

    // ---- status and delete ----

    @Test
    fun `changeStatus updates only the status and delete removes the owned task`() {
        val t = task(status = TaskStatus.TODO)
        every { tasks.findByIdAndProjectOwnerId(5L, 1L) } returns t
        every { tasks.flush() } just Runs
        every { tasks.delete(t) } just Runs
        val dto = service.changeStatus(5L, 1L, TaskStatus.IN_PROGRESS)
        assertEquals(TaskStatus.IN_PROGRESS, dto.status)
        assertEquals("T", dto.title)
        service.delete(5L, 1L)
        verify(exactly = 1) { tasks.delete(t) }
    }

    // ---- overdue flag on the DTO uses the injected clock ----

    @Test
    fun `overdue flag - yesterday is overdue, today is not, DONE is not, no date is not`() {
        every { tasks.findByIdAndProjectOwnerId(5L, 1L) } answers { task() }
        every { tasks.flush() } just Runs
        fun overdue(due: LocalDate?, status: TaskStatus): Boolean {
            val t = task(due = due, status = TaskStatus.TODO)
            every { tasks.findByIdAndProjectOwnerId(5L, 1L) } returns t
            return service.changeStatus(5L, 1L, status).overdue
        }
        assertTrue(overdue(today.minusDays(1), TaskStatus.TODO))
        assertTrue(overdue(today.minusDays(1), TaskStatus.IN_PROGRESS))
        assertFalse(overdue(today.minusDays(1), TaskStatus.DONE))
        assertFalse(overdue(today, TaskStatus.TODO))
        assertFalse(overdue(today.plusDays(1), TaskStatus.TODO))
        assertFalse(overdue(null, TaskStatus.TODO))
    }

    // ---- list ----

    private val query = TaskListQuery(null, null, null, TaskSortField.DUE_DATE, true, 0, 20)

    @Test
    fun `list of a foreign project is NotFound and never queries tasks`() {
        every { projects.findByIdAndOwnerId(10L, 2L) } returns null
        assertThrows<NotFoundException> { service.list(10L, 2L, query) }
        verify(exactly = 0) { tasks.findAll(any<Specification<Task>>(), any<Pageable>()) }
    }

    @Test
    fun `list maps a page of tasks to DTOs with paging metadata`() {
        every { projects.findByIdAndOwnerId(10L, 1L) } returns project
        every { tasks.findAll(any<Specification<Task>>(), any<Pageable>()) } returns
            PageImpl(listOf(task(due = today.minusDays(1))), org.springframework.data.domain.PageRequest.of(0, 20), 41)
        val page = service.list(10L, 1L, query)
        assertEquals(1, page.content.size)
        assertTrue(page.content[0].overdue)
        assertEquals(41, page.totalElements)
        assertEquals(3, page.totalPages)
        assertEquals(20, page.size)
    }
}

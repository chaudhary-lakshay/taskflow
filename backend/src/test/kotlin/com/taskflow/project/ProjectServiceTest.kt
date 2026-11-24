package com.taskflow.project

import com.taskflow.common.NotFoundException
import com.taskflow.task.StatusCount
import com.taskflow.task.TaskRepository
import com.taskflow.task.TaskStatus
import com.taskflow.user.User
import com.taskflow.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProjectServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-24T23:59:00Z"), ZoneOffset.UTC)
    private val projects = mockk<ProjectRepository>()
    private val tasks = mockk<TaskRepository>()
    private val users = mockk<UserRepository>()
    private val service = ProjectService(projects, tasks, users, clock)
    private val owner = User("Owner", "o@example.test", "h").also { it.id = 1L }

    private fun count(s: TaskStatus, c: Long) = object : StatusCount { override val status = s; override val n = c }

    @Test
    fun `create stores the project under the caller and trims the name`() {
        every { users.findById(1L) } returns Optional.of(owner)
        val saved = slot<Project>()
        every { projects.save(capture(saved)) } answers { saved.captured.also { it.id = 9L } }
        val dto = service.create(1L, CreateProjectRequest("  Launch ", "  "))
        assertEquals("Launch", saved.captured.name)
        assertEquals(null, saved.captured.description)
        assertEquals(owner, saved.captured.owner)
        assertEquals(1L, dto.ownerId)
    }

    @Test
    fun `list asks only for the caller's projects`() {
        every { projects.findAllByOwnerIdOrderById(1L) } returns listOf(Project("A", null, owner).also { it.id = 2L })
        assertEquals(listOf(2L), service.listOwned(1L).map { it.id })
        verify(exactly = 1) { projects.findAllByOwnerIdOrderById(1L) }
    }

    @Test
    fun `summary combines status counts, fills missing statuses with 0 and asks for overdue as of today (UTC)`() {
        every { projects.findByIdAndOwnerId(10L, 1L) } returns Project("P", null, owner)
        every { tasks.countByStatus(10L, 1L) } returns listOf(count(TaskStatus.TODO, 3), count(TaskStatus.DONE, 2))
        every { tasks.countOverdue(10L, 1L, LocalDate.of(2026, 11, 24)) } returns 1
        val s = service.summary(10L, 1L)
        assertEquals(listOf(3L, 0L, 2L, 1L, 5L), listOf(s.todo, s.inProgress, s.done, s.overdue, s.total))
    }

    @Test
    fun `summary of a foreign or missing project is NotFound and counts nothing`() {
        every { projects.findByIdAndOwnerId(10L, 2L) } returns null
        assertThrows<NotFoundException> { service.summary(10L, 2L) }
        verify(exactly = 0) { tasks.countByStatus(any(), any()) }
        verify(exactly = 0) { tasks.countOverdue(any(), any(), any()) }
    }
}

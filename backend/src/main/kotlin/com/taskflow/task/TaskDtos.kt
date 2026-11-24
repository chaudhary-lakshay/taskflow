package com.taskflow.task

import com.taskflow.user.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

data class CreateTaskRequest(
    @field:NotBlank(message = "Title is required") @field:Size(max = 120, message = "Title must be at most 120 characters")
    val title: String?,
    @field:Size(max = 2000, message = "Description must be at most 2000 characters")
    val description: String? = null,
    val priority: TaskPriority? = null, // default MEDIUM
    val status: TaskStatus? = null, // default TODO
    val dueDate: LocalDate? = null,
    val assigneeId: Long? = null,
)

/** Complete replacement of the editable fields; null description, dueDate and assigneeId clear them. */
data class UpdateTaskRequest(
    @field:NotBlank(message = "Title is required") @field:Size(max = 120, message = "Title must be at most 120 characters")
    val title: String?,
    @field:Size(max = 2000, message = "Description must be at most 2000 characters")
    val description: String? = null,
    @field:NotNull(message = "Status is required") val status: TaskStatus?,
    @field:NotNull(message = "Priority is required") val priority: TaskPriority?,
    val dueDate: LocalDate? = null,
    val assigneeId: Long? = null,
)

data class StatusRequest(@field:NotNull(message = "Status is required") val status: TaskStatus?)

data class AssigneeDto(val id: Long, val name: String)

data class TaskDto(
    val id: Long,
    val title: String,
    val description: String?,
    val status: TaskStatus,
    val priority: TaskPriority,
    val dueDate: LocalDate?,
    val assignee: AssigneeDto?,
    val projectId: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val overdue: Boolean,
) {
    companion object {
        fun from(t: Task, today: LocalDate) = TaskDto(
            id = t.id!!,
            title = t.title,
            description = t.description,
            status = t.status,
            priority = t.priority,
            dueDate = t.dueDate,
            assignee = t.assignee?.let { u: User -> AssigneeDto(u.id!!, u.name) },
            projectId = t.project.id!!,
            createdAt = t.createdAt,
            updatedAt = t.updatedAt,
            overdue = TaskRules.isOverdue(t.dueDate, t.status, today),
        )
    }
}

data class PageDto<T>(val content: List<T>, val page: Int, val size: Int, val totalElements: Long, val totalPages: Int)

/** Validated query parameters of GET /api/projects/{id}/tasks. */
data class TaskListQuery(
    val status: TaskStatus?,
    val priority: TaskPriority?,
    val assigneeId: Long?,
    val sortBy: TaskSortField,
    val ascending: Boolean,
    val page: Int,
    val size: Int,
)

package com.taskflow.task

import com.taskflow.common.RequestValidationException
import com.taskflow.security.userId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class TaskController(private val tasks: TaskService) {
    @GetMapping("/api/projects/{projectId}/tasks")
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable projectId: Long,
        @RequestParam(required = false) status: TaskStatus?,
        @RequestParam(required = false) priority: TaskPriority?,
        @RequestParam(required = false) assigneeId: Long?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(required = false) direction: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageDto<TaskDto> = tasks.list(projectId, jwt.userId(), parseQuery(status, priority, assigneeId, sortBy, direction, page, size))

    @PostMapping("/api/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticationPrincipal jwt: Jwt, @PathVariable projectId: Long, @Valid @RequestBody req: CreateTaskRequest): TaskDto =
        tasks.create(projectId, jwt.userId(), req)

    @PutMapping("/api/tasks/{id}")
    fun update(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long, @Valid @RequestBody req: UpdateTaskRequest): TaskDto =
        tasks.update(id, jwt.userId(), req)

    @PatchMapping("/api/tasks/{id}/status")
    fun changeStatus(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long, @Valid @RequestBody req: StatusRequest): TaskDto =
        tasks.changeStatus(id, jwt.userId(), req.status!!)

    @DeleteMapping("/api/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = tasks.delete(id, jwt.userId())

    companion object {
        const val MAX_PAGE_SIZE = 100

        /** Unsupported sort or paging values are 400 with a field error, never silently corrected. */
        fun parseQuery(
            status: TaskStatus?,
            priority: TaskPriority?,
            assigneeId: Long?,
            sortBy: String?,
            direction: String?,
            page: Int,
            size: Int,
        ): TaskListQuery {
            val errors = linkedMapOf<String, String>()
            val field = if (sortBy == null) TaskSortField.DUE_DATE else TaskSortField.parse(sortBy)
            if (field == null) errors["sortBy"] = "sortBy must be dueDate or priority"
            val ascending = when (direction?.lowercase()) {
                null -> field?.strategy?.defaultAscending ?: true
                "asc" -> true
                "desc" -> false
                else -> { errors["direction"] = "direction must be asc or desc"; true }
            }
            if (page < 0) errors["page"] = "page must be 0 or greater"
            if (size < 1 || size > MAX_PAGE_SIZE) errors["size"] = "size must be between 1 and " + MAX_PAGE_SIZE
            if (errors.isNotEmpty()) throw RequestValidationException(errors)
            return TaskListQuery(status, priority, assigneeId, field!!, ascending, page, size)
        }
    }
}

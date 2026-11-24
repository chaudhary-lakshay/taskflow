package com.taskflow.task

import com.taskflow.common.NotFoundException
import com.taskflow.common.RequestValidationException
import com.taskflow.project.Project
import com.taskflow.project.ProjectRepository
import com.taskflow.user.User
import com.taskflow.user.UserRepository
import java.time.Clock
import java.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TaskService(
    private val projects: ProjectRepository,
    private val tasks: TaskRepository,
    private val users: UserRepository,
    private val clock: Clock,
) {
    private fun today(): LocalDate = LocalDate.now(clock)

    private fun ownedProject(projectId: Long, ownerId: Long): Project =
        projects.findByIdAndOwnerId(projectId, ownerId) ?: throw NotFoundException()

    private fun ownedTask(taskId: Long, ownerId: Long): Task =
        tasks.findByIdAndProjectOwnerId(taskId, ownerId) ?: throw NotFoundException()

    @Transactional(readOnly = true)
    fun list(projectId: Long, ownerId: Long, q: TaskListQuery): PageDto<TaskDto> {
        ownedProject(projectId, ownerId) // 404 for foreign or missing, before anything else
        val page = tasks.findPage(ownerId, projectId, q)
        val today = today()
        return PageDto(page.content.map { TaskDto.from(it, today) }, page.number, page.size, page.totalElements, page.totalPages)
    }

    @Transactional
    fun create(projectId: Long, ownerId: Long, req: CreateTaskRequest): TaskDto {
        val project = ownedProject(projectId, ownerId)
        val today = today()
        val errors = linkedMapOf<String, String>()
        if (req.dueDate != null && req.dueDate.isBefore(today)) errors["dueDate"] = "Due date must not be in the past"
        val assignee = resolveAssignee(req.assigneeId, errors)
        if (errors.isNotEmpty()) throw RequestValidationException(errors)

        val task = tasks.save(
            Task(
                title = req.title!!.trim(),
                description = blankToNull(req.description),
                status = req.status ?: TaskStatus.TODO,
                priority = req.priority ?: TaskPriority.MEDIUM,
                dueDate = req.dueDate,
                assignee = assignee,
                project = project,
            ),
        )
        return TaskDto.from(task, today)
    }

    @Transactional
    fun update(taskId: Long, ownerId: Long, req: UpdateTaskRequest): TaskDto {
        val task = ownedTask(taskId, ownerId)
        val today = today()
        val errors = linkedMapOf<String, String>()
        // D04: an overdue date that is left unchanged may stay; a changed date must not be in the past.
        if (req.dueDate != null && req.dueDate != task.dueDate && req.dueDate.isBefore(today)) {
            errors["dueDate"] = "Due date must not be in the past"
        }
        val assignee = resolveAssignee(req.assigneeId, errors)
        if (errors.isNotEmpty()) throw RequestValidationException(errors)

        task.title = req.title!!.trim()
        task.description = blankToNull(req.description)
        task.status = req.status!!
        task.priority = req.priority!!
        task.dueDate = req.dueDate
        task.assignee = assignee
        tasks.flush() // fires @PreUpdate so the response carries the stored updatedAt
        return TaskDto.from(task, today)
    }

    @Transactional
    fun changeStatus(taskId: Long, ownerId: Long, status: TaskStatus): TaskDto {
        val task = ownedTask(taskId, ownerId)
        task.status = status
        tasks.flush()
        return TaskDto.from(task, today())
    }

    @Transactional
    fun delete(taskId: Long, ownerId: Long) {
        tasks.delete(ownedTask(taskId, ownerId))
    }

    /** Any existing user may be assigned; being assigned grants no access to the project. */
    private fun resolveAssignee(assigneeId: Long?, errors: MutableMap<String, String>): User? {
        if (assigneeId == null) return null
        val user = users.findById(assigneeId).orElse(null)
        if (user == null) errors["assigneeId"] = "Assignee does not exist"
        return user
    }

    private fun blankToNull(s: String?) = s?.trim()?.ifEmpty { null }
}

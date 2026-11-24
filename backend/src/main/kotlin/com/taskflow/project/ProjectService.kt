package com.taskflow.project

import com.taskflow.common.NotFoundException
import com.taskflow.task.TaskRepository
import com.taskflow.task.TaskStatus
import com.taskflow.user.UserRepository
import java.time.Clock
import java.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProjectService(
    private val projects: ProjectRepository,
    private val tasks: TaskRepository,
    private val users: UserRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun listOwned(ownerId: Long): List<ProjectDto> = projects.findAllByOwnerIdOrderById(ownerId).map(ProjectDto::from)

    /** The owner is always the authenticated user; the request has no owner field. */
    @Transactional
    fun create(ownerId: Long, req: CreateProjectRequest): ProjectDto {
        val owner = users.findById(ownerId).orElseThrow { NotFoundException() }
        val description = req.description?.trim()?.ifEmpty { null }
        return ProjectDto.from(projects.save(Project(req.name!!.trim(), description, owner)))
    }

    /** Independent of any board filter: counts every task of the project. */
    @Transactional(readOnly = true)
    fun summary(projectId: Long, ownerId: Long): SummaryDto {
        projects.findByIdAndOwnerId(projectId, ownerId) ?: throw NotFoundException()
        val byStatus = tasks.countByStatus(projectId, ownerId).associate { it.status to it.n }
        val todo = byStatus[TaskStatus.TODO] ?: 0
        val inProgress = byStatus[TaskStatus.IN_PROGRESS] ?: 0
        val done = byStatus[TaskStatus.DONE] ?: 0
        val overdue = tasks.countOverdue(projectId, ownerId, LocalDate.now(clock))
        return SummaryDto(todo, inProgress, done, overdue, todo + inProgress + done)
    }
}

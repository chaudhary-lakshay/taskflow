package com.taskflow.task

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface StatusCount {
    val status: TaskStatus
    val n: Long
}

/**
 * Deliberately NOT a JpaRepository (see ProjectRepository). Every read and write of a task goes
 * through a query that names the owning user; list filters are composed as Specifications that
 * always start from TaskSpecifications.scoped(projectId, ownerId).
 */
interface TaskRepository : Repository<Task, Long>, JpaSpecificationExecutor<Task> {
    fun <S : Task> save(task: S): S

    fun <S : Task> saveAll(tasks: Iterable<S>): List<S>

    fun delete(task: Task)

    fun flush()

    /** Null when the task does not exist or its project belongs to someone else. Assignment grants no access. */
    fun findByIdAndProjectOwnerId(id: Long, ownerId: Long): Task?

    fun findAllByProjectIdAndProjectOwnerIdOrderById(projectId: Long, ownerId: Long): List<Task>

    @EntityGraph(attributePaths = ["assignee"])
    override fun findAll(spec: Specification<Task>?, pageable: Pageable): Page<Task>

    @Query(
        "select t.status as status, count(t) as n from Task t " +
            "where t.project.id = :projectId and t.project.owner.id = :ownerId group by t.status",
    )
    fun countByStatus(@Param("projectId") projectId: Long, @Param("ownerId") ownerId: Long): List<StatusCount>

    /** Mirrors [TaskRules.isOverdue]: due before today and not DONE. A null due date never matches. */
    @Query(
        "select count(t) from Task t where t.project.id = :projectId and t.project.owner.id = :ownerId " +
            "and t.dueDate < :today and t.status <> com.taskflow.task.TaskStatus.DONE",
    )
    fun countOverdue(
        @Param("projectId") projectId: Long,
        @Param("ownerId") ownerId: Long,
        @Param("today") today: LocalDate,
    ): Long
}

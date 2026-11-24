package com.taskflow.task

import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.JoinType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification

/** Repository fragment: the only way to list tasks with filters. The owner is a required argument. */
interface TaskPageQuery {
    /**
     * One page of tasks of [projectId] that belongs to [ownerId], filtered and ordered as in [query].
     * A project owned by someone else gives an empty page with totalElements 0.
     */
    fun findPage(ownerId: Long, projectId: Long, query: TaskListQuery): Page<Task>
}

/** Picked up by Spring Data because the name is the fragment name plus Impl. */
class TaskPageQueryImpl(private val em: EntityManager) : TaskPageQuery {
    override fun findPage(ownerId: Long, projectId: Long, query: TaskListQuery): Page<Task> {
        // Specification pattern: the scope is always the first element, the optional filters follow.
        val spec = listOfNotNull(
            TaskSpecifications.scoped(projectId, ownerId),
            query.status?.let { TaskSpecifications.hasStatus(it) },
            query.priority?.let { TaskSpecifications.hasPriority(it) },
            query.assigneeId?.let { TaskSpecifications.assignedTo(it) },
        ).reduce { a: Specification<Task>, b: Specification<Task> -> a.and(b) }

        val cb = em.criteriaBuilder

        val dataQuery = cb.createQuery(Task::class.java)
        val root = dataQuery.from(Task::class.java)
        root.fetch<Task, Any>("assignee", JoinType.LEFT)
        dataQuery.select(root).where(spec.toPredicate(root, dataQuery, cb))
        dataQuery.orderBy(query.sortBy.strategy.orders(root, cb, query.ascending))
        val rows = em.createQuery(dataQuery)
            .setFirstResult(query.page * query.size)
            .setMaxResults(query.size)
            .resultList

        val countQuery = cb.createQuery(Long::class.javaObjectType)
        val countRoot = countQuery.from(Task::class.java)
        countQuery.select(cb.count(countRoot)).where(spec.toPredicate(countRoot, countQuery, cb))
        val total = em.createQuery(countQuery).singleResult

        return PageImpl(rows, PageRequest.of(query.page, query.size), total)
    }
}

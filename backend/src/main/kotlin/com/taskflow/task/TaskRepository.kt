package com.taskflow.task

import org.springframework.data.jpa.repository.JpaRepository

interface TaskRepository : JpaRepository<Task, Long> {
    /** Null when the task does not exist or its project belongs to someone else. Assignment grants no access. */
    fun findByIdAndProjectOwnerId(id: Long, ownerId: Long): Task?

    fun findAllByProjectIdAndProjectOwnerIdOrderById(projectId: Long, ownerId: Long): List<Task>
}

package com.taskflow.task

import com.taskflow.project.Project
import com.taskflow.user.User
import org.springframework.data.jpa.domain.Specification

/** Specification pattern: optional filters composed on top of the mandatory project + owner scope. */
object TaskSpecifications {
    /** Every task query starts here: the project must be the given one AND owned by [ownerId]. */
    fun scoped(projectId: Long, ownerId: Long) = Specification<Task> { root, _, cb ->
        val project = root.get<Project>("project")
        cb.and(
            cb.equal(project.get<Long>("id"), projectId),
            cb.equal(project.get<User>("owner").get<Long>("id"), ownerId),
        )
    }

    fun hasStatus(status: TaskStatus) = Specification<Task> { root, _, cb -> cb.equal(root.get<TaskStatus>("status"), status) }

    fun hasPriority(priority: TaskPriority) =
        Specification<Task> { root, _, cb -> cb.equal(root.get<TaskPriority>("priority"), priority) }

    fun assignedTo(userId: Long) =
        Specification<Task> { root, _, cb -> cb.equal(root.get<User>("assignee").get<Long>("id"), userId) }
}

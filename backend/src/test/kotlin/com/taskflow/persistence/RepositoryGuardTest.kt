package com.taskflow.persistence

import com.taskflow.project.ProjectRepository
import com.taskflow.task.TaskRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Owned resources must not be reachable without naming the owner, so the repositories services use
 * have a closed, reviewed method list. Anything not on the list fails here, including Spring Data
 * defaults (findById, findAll, deleteById...) and JpaSpecificationExecutor (findAll/findOne/count/
 * exists/delete(Specification)). Adding a method means deciding, in review, that it is owner-scoped.
 */
class RepositoryGuardTest {
    private fun surface(type: Class<*>) = type.methods.filter { it.declaringClass != Any::class.java }.map { it.name }.toSortedSet()

    @Test
    fun `ProjectRepository has exactly the reviewed methods`() = assertEquals(
        sortedSetOf("save", "findAllByOwnerIdOrderById", "findByIdAndOwnerId"),
        surface(ProjectRepository::class.java),
    )

    @Test
    fun `TaskRepository has exactly the reviewed methods, all owner-scoped or entity based`() = assertEquals(
        sortedSetOf(
            "save", "saveAll", "delete", "flush", // act on entities that were fetched through a scoped query
            "findByIdAndProjectOwnerId", "findAllByProjectIdAndProjectOwnerIdOrderById",
            "countByStatus", "countOverdue", "findPage", // each takes the owner id
        ),
        surface(TaskRepository::class.java),
    )

    @Test
    fun `findPage takes the owner id as a required first argument`() {
        val m = TaskRepository::class.java.methods.single { it.name == "findPage" }
        assertEquals(listOf(Long::class.javaPrimitiveType, Long::class.javaPrimitiveType), m.parameterTypes.take(2))
    }
}

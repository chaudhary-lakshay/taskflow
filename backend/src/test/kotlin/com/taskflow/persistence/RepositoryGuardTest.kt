package com.taskflow.persistence

import com.taskflow.project.ProjectRepository
import com.taskflow.task.TaskRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Owned resources must not be reachable without naming the owner. These repositories therefore do
 * not extend JpaRepository/CrudRepository: no findById, findAll(), deleteById, getReferenceById...
 */
class RepositoryGuardTest {
    private val unscoped = setOf(
        "findById", "findAll", "findAllById", "getReferenceById", "getById", "getOne", "deleteById", "deleteAll", "deleteAllById",
        "deleteAllInBatch", "deleteInBatch", "deleteAllByIdInBatch", "existsById", "count", "saveAndFlush", "saveAllAndFlush",
    )

    private fun leaked(type: Class<*>) =
        type.methods.filter { it.name in unscoped && !it.parameterTypes.any { p -> p.name.endsWith("Specification") } }.map { it.toString() }

    @Test
    fun `ProjectRepository exposes no unscoped lookup or delete`() = assertEquals(emptyList<String>(), leaked(ProjectRepository::class.java))

    @Test
    fun `TaskRepository exposes no unscoped lookup or delete (specification queries are scoped by their callers)`() =
        assertEquals(emptyList<String>(), leaked(TaskRepository::class.java))
}

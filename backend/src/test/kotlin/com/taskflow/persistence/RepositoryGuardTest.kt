package com.taskflow.persistence

import com.taskflow.project.ProjectRepository
import com.taskflow.task.TaskRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Owned resources must not be reachable without naming the owner, so the repositories services use
 * have a closed, reviewed list of method signatures. Anything not on the list fails here, including Spring Data
 * defaults (findById, findAll, deleteById...) and JpaSpecificationExecutor (findAll/findOne/count/
 * exists/delete(Specification)). Adding a method means deciding, in review, that it is owner-scoped.
 */
class RepositoryGuardTest {
    /** Name, parameter types and return type, so an unreviewed overload of an approved name fails too. */
    private fun surface(type: Class<*>) = type.methods.filter { it.declaringClass != Any::class.java }
        .map { m -> m.name + m.parameterTypes.joinToString(",", "(", ")") { it.simpleName } + ":" + m.returnType.simpleName }
        .toSortedSet()

    @Test
    fun `ProjectRepository has exactly the reviewed methods`() = assertEquals(
        sortedSetOf(
            "save(Project):Project",
            "findAllByOwnerIdOrderById(long):List",
            "findByIdAndOwnerId(long,long):Project",
        ),
        surface(ProjectRepository::class.java),
    )

    @Test
    fun `TaskRepository has exactly the reviewed methods, all owner-scoped or entity based`() = assertEquals(
        sortedSetOf(
            // act on entities that were fetched through a scoped query
            "save(Task):Task", "saveAll(Iterable):List", "delete(Task):void", "flush():void",
            // each takes the owner id
            "findByIdAndProjectOwnerId(long,long):Task",
            "findAllByProjectIdAndProjectOwnerIdOrderById(long,long):List",
            "countByStatus(long,long):List",
            "countOverdue(long,long,LocalDate):long",
            "findPage(long,long,TaskListQuery):Page",
        ),
        surface(TaskRepository::class.java),
    )

    @Test
    fun `findPage takes the owner id as a required first argument`() {
        val m = TaskRepository::class.java.methods.single { it.name == "findPage" }
        assertEquals(listOf(Long::class.javaPrimitiveType, Long::class.javaPrimitiveType), m.parameterTypes.take(2))
    }
}

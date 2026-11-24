package com.taskflow.project

import org.springframework.data.repository.Repository

/**
 * Deliberately NOT a JpaRepository: findById, findAll, deleteById, getReferenceById... are not
 * available, so a service cannot reach a project without naming its owner.
 */
interface ProjectRepository : Repository<Project, Long> {
    fun <S : Project> save(project: S): S

    fun findAllByOwnerIdOrderById(ownerId: Long): List<Project>

    /** Null when the project does not exist or belongs to someone else (callers answer 404 for both). */
    fun findByIdAndOwnerId(id: Long, ownerId: Long): Project?
}

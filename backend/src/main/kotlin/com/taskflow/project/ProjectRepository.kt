package com.taskflow.project

import org.springframework.data.jpa.repository.JpaRepository

interface ProjectRepository : JpaRepository<Project, Long> {
    fun findAllByOwnerIdOrderById(ownerId: Long): List<Project>

    /** Null when the project does not exist or belongs to someone else (callers answer 404 for both). */
    fun findByIdAndOwnerId(id: Long, ownerId: Long): Project?
}

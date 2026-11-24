package com.taskflow.project

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateProjectRequest(
    @field:NotBlank(message = "Name is required") @field:Size(max = 120, message = "Name must be at most 120 characters")
    val name: String?,
    @field:Size(max = 2000, message = "Description must be at most 2000 characters")
    val description: String? = null,
)

data class ProjectDto(val id: Long, val name: String, val description: String?, val ownerId: Long, val createdAt: Instant) {
    companion object {
        fun from(p: Project) = ProjectDto(p.id!!, p.name, p.description, p.owner.id!!, p.createdAt)
    }
}

data class SummaryDto(val todo: Long, val inProgress: Long, val done: Long, val overdue: Long, val total: Long)

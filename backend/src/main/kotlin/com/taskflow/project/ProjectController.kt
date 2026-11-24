package com.taskflow.project

import com.taskflow.security.userId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class ProjectController(private val projects: ProjectService) {
    @GetMapping("/api/projects")
    fun list(@AuthenticationPrincipal jwt: Jwt): List<ProjectDto> = projects.listOwned(jwt.userId())

    @PostMapping("/api/projects")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody req: CreateProjectRequest): ProjectDto =
        projects.create(jwt.userId(), req)

    @GetMapping("/api/projects/{id}/summary")
    fun summary(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long): SummaryDto = projects.summary(id, jwt.userId())
}

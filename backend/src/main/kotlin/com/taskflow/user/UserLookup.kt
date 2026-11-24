package com.taskflow.user

import com.taskflow.common.RequestValidationException
import com.taskflow.security.userId
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Only id and name leave the system: no email, no hash. */
data class UserOption(val id: Long, val name: String)

@Service
class UserLookupService(private val users: UserRepository) {
    @Transactional(readOnly = true)
    fun search(query: String): List<UserOption> {
        val q = query.trim()
        if (q.codePointCount(0, q.length) < MIN_QUERY) {
            throw RequestValidationException("query", "query must be at least " + MIN_QUERY + " characters")
        }
        return users.findByNameContainingIgnoreCase(q, PageRequest.of(0, MAX_RESULTS, Sort.by("name", "id")))
            .map { UserOption(it.id!!, it.name) }
    }

    companion object {
        const val MIN_QUERY = 2
        const val MAX_RESULTS = 20
    }
}

/** Extra endpoint beyond the assignment (decision D02): lets the UI pick an assignee by name. */
@RestController
class UserLookupController(private val lookup: UserLookupService) {
    @GetMapping("/api/users")
    fun search(@AuthenticationPrincipal jwt: Jwt, @RequestParam(defaultValue = "") query: String): List<UserOption> = lookup.search(query)
}

package com.taskflow.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    /** Pass an already normalised email (see [User.normalizeEmail]). */
    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean

    /** Directory search by name only (never email); wildcards in [name] are escaped by Spring Data. */
    fun findByNameContainingIgnoreCase(name: String, pageable: org.springframework.data.domain.Pageable): List<User>
}

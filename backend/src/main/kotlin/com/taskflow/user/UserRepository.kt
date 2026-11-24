package com.taskflow.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    /** Pass an already normalised email (see [User.normalizeEmail]). */
    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean
}

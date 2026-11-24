package com.taskflow.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, length = 100)
    var name: String,
    email: String,
    @Column(name = "password_hash", nullable = false, length = 100)
    var passwordHash: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** Stored trimmed and lower case; the database CHECK refuses anything else. */
    @Column(nullable = false, length = 254)
    var email: String = normalizeEmail(email)

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS) // Postgres timestamptz keeps microseconds

    companion object {
        fun normalizeEmail(raw: String) = raw.trim().lowercase()
    }
}

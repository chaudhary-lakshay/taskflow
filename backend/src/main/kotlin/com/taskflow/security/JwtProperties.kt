package com.taskflow.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("taskflow.jwt")
data class JwtProperties(
    /** Base64 HMAC key, at least 32 bytes. Comes only from the environment (JWT_SECRET). */
    val secret: String,
    val issuer: String = "taskflow",
    val ttlSeconds: Long = 3600,
) {
    init {
        check(ttlSeconds > 0) { "JWT_TTL_SECONDS must be positive" }
        check(issuer.isNotBlank()) { "JWT_ISSUER must not be blank" }
    }

    // Keep the key out of logs and exception messages.
    override fun toString() = "JwtProperties(issuer=$issuer, ttlSeconds=$ttlSeconds)"
}

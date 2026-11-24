package com.taskflow.security

import java.time.Clock
import java.time.Duration
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.stereotype.Service

data class IssuedToken(val value: String, val expiresInSeconds: Long)

@Service
class TokenService(
    private val encoder: JwtEncoder,
    private val props: JwtProperties,
    private val clock: Clock,
) {
    /** Subject is the immutable user id. */
    fun issue(userId: Long): IssuedToken {
        val now = clock.instant()
        val claims = JwtClaimsSet.builder()
            .issuer(props.issuer)
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofSeconds(props.ttlSeconds)))
            .build()
        val token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        return IssuedToken(token.tokenValue, props.ttlSeconds)
    }
}

/** The authenticated owner id. The decoder has already checked it is a positive number. */
fun Jwt.userId(): Long = subject.toLong()

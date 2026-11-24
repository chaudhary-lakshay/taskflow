package com.taskflow.security

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jose.jws.MacAlgorithm

/** JWT issuing and validation without Spring or Docker: signature, expiry, issuer, subject, algorithm. */
class JwtTokenTest {
    private fun secret(seed: Char) = Base64.getEncoder().encodeToString(seed.toString().repeat(32).toByteArray())
    private val props = JwtProperties(secret('a'), "taskflow", 3600)
    private val now = Instant.parse("2026-11-24T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val config = JwtConfig()
    private val decoder = config.jwtDecoder(config.jwtSecretKey(props), props, clock)
    private val tokens = TokenService(config.jwtEncoder(config.jwtSecretKey(props)), props, clock)

    @Test
    fun `issued token carries issuer, numeric subject and the configured lifetime`() {
        val issued = tokens.issue(42L)
        val jwt = decoder.decode(issued.value)
        assertEquals("taskflow", jwt.getClaimAsString("iss"))
        assertEquals(42L, jwt.userId())
        assertEquals(Duration.ofSeconds(3600), Duration.between(jwt.issuedAt, jwt.expiresAt))
        assertEquals(3600, issued.expiresInSeconds)
    }

    @Test
    fun `expired token is rejected`() {
        val old = TokenService(config.jwtEncoder(config.jwtSecretKey(props)), props, Clock.fixed(now.minusSeconds(7200), ZoneOffset.UTC))
        assertThrows<JwtException> { decoder.decode(old.issue(1L).value) }
    }

    @Test
    fun `token signed with another secret is rejected`() {
        val other = JwtProperties(secret('b'), "taskflow", 3600)
        val forged = TokenService(config.jwtEncoder(config.jwtSecretKey(other)), other, clock)
        assertThrows<JwtException> { decoder.decode(forged.issue(1L).value) }
    }

    @Test
    fun `token from another issuer is rejected`() {
        val other = JwtProperties(props.secret, "someone-else", 3600)
        val foreign = TokenService(config.jwtEncoder(config.jwtSecretKey(other)), other, clock)
        assertThrows<JwtException> { decoder.decode(foreign.issue(1L).value) }
    }

    @Test
    fun `token with a non numeric or non positive subject is rejected`() {
        listOf("abc", "0", "-5", "").forEach { sub ->
            assertThrows<JwtException>("subject '$sub'") { decoder.decode(raw(sub = sub)) }
        }
    }

    @Test
    fun `token without an expiry is rejected`() {
        assertThrows<JwtException> { decoder.decode(raw(exp = null)) }
    }

    @Test
    fun `token signed with another HMAC algorithm is rejected`() {
        assertThrows<JwtException> { decoder.decode(raw(alg = MacAlgorithm.HS512)) }
    }

    @Test
    fun `garbage is rejected`() {
        assertThrows<JwtException> { decoder.decode("not-a-jwt") }
    }

    @Test
    fun `missing or short secrets stop startup`() {
        assertTrue(assertThrows<IllegalStateException> { config.jwtSecretKey(JwtProperties("", "taskflow", 3600)) }.message!!.contains("JWT_SECRET"))
        assertThrows<IllegalStateException> { config.jwtSecretKey(JwtProperties("not base64 !!", "taskflow", 3600)) }
        val short = Base64.getEncoder().encodeToString("too-short".toByteArray())
        assertThrows<IllegalStateException> { config.jwtSecretKey(JwtProperties(short, "taskflow", 3600)) }
    }

    @Test
    fun `non positive lifetime stops startup`() {
        assertThrows<IllegalStateException> { JwtProperties(props.secret, "taskflow", 0) }
    }

    private fun raw(sub: String = "1", exp: Instant? = now.plusSeconds(600), alg: MacAlgorithm = MacAlgorithm.HS256): String {
        val encoder = config.jwtEncoder(config.jwtSecretKey(props))
        val claims = JwtClaimsSet.builder().issuer("taskflow").subject(sub).issuedAt(now)
            .also { if (exp != null) it.expiresAt(exp) }.build()
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(alg).build(), claims)).tokenValue
    }
}

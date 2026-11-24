package com.taskflow.auth

import com.taskflow.security.JwtProperties
import com.taskflow.support.PostgresIT
import com.taskflow.user.UserRepository
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Full filter chain + real PostgreSQL: registration, login, validation, JWT protection. */
@AutoConfigureMockMvc
class AuthApiIT : PostgresIT() {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var jdbc: org.springframework.jdbc.core.JdbcTemplate
    @Autowired lateinit var encoder: JwtEncoder
    @Autowired lateinit var props: JwtProperties

    private val pw = "correct-horse-battery"
    private fun email() = "it-${UUID.randomUUID()}@example.test"

    private fun json(vararg kv: Pair<String, Any?>) =
        kv.joinToString(",", "{", "}") { (k, v) -> "\"$k\":" + if (v == null) "null" else "\"$v\"" }

    private fun register(email: String, password: String = pw, name: String = "Ann") =
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json("name" to name, "email" to email, "password" to password)))

    private fun login(email: String, password: String = pw) =
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json("email" to email, "password" to password)))

    private fun body(r: MvcResult) = r.response.contentAsString
    private fun field(r: MvcResult, name: String): String = Regex("\"$name\":\"?([^\",}]*)").find(body(r))!!.groupValues[1]

    private fun token(claims: JwtClaimsSet.Builder.() -> Unit, enc: JwtEncoder = encoder): String {
        val b = JwtClaimsSet.builder().issuer(props.issuer).subject("1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
        b.apply(claims)
        return enc.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), b.build())).tokenValue
    }

    private fun whoami(token: String?) =
        mvc.perform(get("/api/test/whoami").apply { if (token != null) header("Authorization", "Bearer $token") })

    // ---- registration ----

    @Test
    fun `register returns 201 with a safe user, normalised email and a BCrypt hash in the database`() {
        val raw = email().uppercase()
        val r = register(raw).andExpect(status().isCreated).andExpect(jsonPath("$.email").value(raw.lowercase()))
            .andExpect(jsonPath("$.name").value("Ann")).andExpect(jsonPath("$.id").isNumber)
            .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn()
        assertFalse(body(r).contains(pw))
        val hash = users.findByEmail(raw.lowercase())!!.passwordHash
        assertTrue(hash.startsWith("$2"), "BCrypt hash expected")
        assertFalse(hash.contains(pw))
    }

    @Test
    fun `duplicate email, in any letter case, is 409 with a field error`() {
        val e = email()
        register(e).andExpect(status().isCreated)
        register(e.uppercase()).andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409)).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"))
            .andExpect(jsonPath("$.fieldErrors.email").exists())
        assertEquals(1, jdbc.queryForObject("select count(*) from users where email = ?", Int::class.java, e))
    }

    @Test
    fun `invalid input gives 400 with field-level errors`() {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json("name" to " ", "email" to "not-an-email", "password" to "short")))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors.name").exists()).andExpect(jsonPath("$.fieldErrors.email").exists())
            .andExpect(jsonPath("$.fieldErrors.password").exists())
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.email").exists())
    }

    @Test
    fun `password length is bounded at 8 characters and at 72 bytes (no silent BCrypt truncation)`() {
        register(email(), "x".repeat(7)).andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.password").exists())
        register(email(), "x".repeat(73)).andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.password").exists())
        register(email(), "é".repeat(37)).andExpect(status().isBadRequest) // 74 bytes, 37 characters
        register(email(), "x".repeat(72)).andExpect(status().isCreated)
        register(email(), "é".repeat(36)).andExpect(status().isCreated) // exactly 72 bytes
    }

    @Test
    fun `email and name length limits are enforced`() {
        register("a".repeat(250) + "@x.test").andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.email").exists())
        register(email(), name = "n".repeat(101)).andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.name").exists())
    }

    @Test
    fun `malformed JSON is 400 in the standard shape`() {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{nope"))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
    }

    // ---- login ----

    @Test
    fun `login returns a bearer token for the registered user and never the password`() {
        val e = email()
        val id = field(register(e).andReturn(), "id")
        val r = login(e.uppercase()).andExpect(status().isOk).andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(3600)).andExpect(jsonPath("$.user.email").value(e))
            .andExpect(jsonPath("$.user.password").doesNotExist()).andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andReturn()
        assertFalse(body(r).contains(pw))
        val tok = field(r, "accessToken")
        whoami(tok).andExpect(status().isOk).andExpect(jsonPath("$.userId").value(id.toLong()))
    }

    @Test
    fun `wrong password and unknown email give the same 401`() {
        val e = email()
        register(e)
        val wrong = login(e, "wrong-password-1").andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")).andReturn()
        val unknown = login(email(), "wrong-password-1").andExpect(status().isUnauthorized).andReturn()
        assertEquals(body(wrong), body(unknown))
    }

    @Test
    fun `login without credentials is a 400 field error`() {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.email").exists()).andExpect(jsonPath("$.fieldErrors.password").exists())
    }

    // ---- protected access ----

    @Test
    fun `no token is 401 in the standard shape with a Bearer challenge`() {
        whoami(null).andExpect(status().isUnauthorized).andExpect(header().string("WWW-Authenticate", "Bearer"))
            .andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.code").value("UNAUTHORIZED")).andExpect(jsonPath("$.message").exists())
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `garbage, expired, forged, wrong-issuer and bad-subject tokens are all 401 in the standard shape`() {
        val other = NimbusJwtEncoder(ImmutableSecret<com.nimbusds.jose.proc.SecurityContext>(javax.crypto.spec.SecretKeySpec("z".repeat(32).toByteArray(), "HmacSHA256")))
        listOf(
            "garbage",
            token({ expiresAt(Instant.now().minusSeconds(3600)); issuedAt(Instant.now().minusSeconds(7200)) }),
            token({}, other),
            token({ issuer("someone-else") }),
            token({ subject("abc") }),
        ).forEach { t -> whoami(t).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("UNAUTHORIZED")) }
    }

    @Test
    fun `owner id comes from the token, not from the request`() {
        whoami(token({ subject("5") }).let { it }).andExpect(status().isOk).andExpect(jsonPath("$.userId").value(5))
        mvc.perform(get("/api/test/whoami").param("ownerId", "999").header("Authorization", "Bearer " + token({ subject("5") })))
            .andExpect(status().isOk).andExpect(jsonPath("$.userId").value(5))
    }

    @Test
    fun `only register, login and health are public`() {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk)
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/projects/1/tasks")).andExpect(status().isUnauthorized)
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized)
    }
}

package com.taskflow.auth

import com.taskflow.support.PostgresIT
import com.taskflow.support.RaceGate
import com.taskflow.support.RaceGateConfig
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * Real concurrent registration against real PostgreSQL. RaceGate holds both requests after their
 * "is this email free?" check, so both see "free" and both attempt the INSERT: the unique
 * constraint, not the pre-check, decides the winner. One must get 201, the other 409 EMAIL_TAKEN.
 */
@AutoConfigureMockMvc
@Import(RaceGateConfig::class)
class AuthRaceIT : PostgresIT() {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun disarm() = RaceGate.disarm()

    private fun register(email: String): MvcResult =
        mvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Racer","email":"$email","password":"race-pass-123"}"""),
        ).andReturn()

    @Test
    fun `two concurrent registrations of one new email give exactly one 201 and one 409 EMAIL_TAKEN`() {
        val email = "race-${UUID.randomUUID()}@example.test"
        RaceGate.arm(email, parties = 2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val go = CountDownLatch(1)
            val calls = (1..2).map { pool.submit<MvcResult> { go.await(); register(email) } }
            go.countDown()
            val results = calls.map { it.get(60, TimeUnit.SECONDS) }

            // The mechanism worked: both requests finished the pre-check and both saw "free".
            assertEquals(2, RaceGate.preChecksPassed.get())
            assertEquals(2, RaceGate.sawFree.get())

            assertEquals(listOf(201, 409), results.map { it.response.status }.sorted())
            val loser = results.first { it.response.status == 409 }.response.contentAsString
            assert("\"code\":\"EMAIL_TAKEN\"" in loser) { loser }
            assert("\"fieldErrors\":{\"email\"" in loser) { loser }
            assertEquals(1, jdbc.queryForObject("select count(*) from users where email = ?", Int::class.java, email))
        } finally {
            pool.shutdownNow()
        }
    }
}

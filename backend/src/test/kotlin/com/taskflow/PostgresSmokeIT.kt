package com.taskflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
class PostgresSmokeIT {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17")
    }

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Test
    fun `context starts, Flyway ran, Postgres 17 answers`() {
        assertEquals(1, jdbc.queryForObject("select 1", Int::class.java))
        assertEquals(
            1,
            jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'flyway_schema_history'",
                Int::class.java,
            ),
        )
        val version = jdbc.queryForObject("show server_version", String::class.java)!!
        assert(version.startsWith("17")) { "unexpected server_version $version" }
    }
}

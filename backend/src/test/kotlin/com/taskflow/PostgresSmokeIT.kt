package com.taskflow

import com.taskflow.support.PostgresIT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class PostgresSmokeIT : PostgresIT() {
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

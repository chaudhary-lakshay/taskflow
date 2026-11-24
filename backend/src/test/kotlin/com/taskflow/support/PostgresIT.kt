package com.taskflow.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

/** Base class: one real PostgreSQL 17 container shared by all integration tests in the JVM. */
@SpringBootTest
abstract class PostgresIT {
    companion object {
        /** Test-only key (32 bytes, base64). Never used outside tests. */
        const val TEST_JWT_SECRET = "dGVzdC1vbmx5LWp3dC1zZWNyZXQtMzItYnl0ZXMhISEh"

        private val postgres = PostgreSQLContainer("postgres:17").also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("taskflow.jwt.secret") { TEST_JWT_SECRET }
        }
    }
}

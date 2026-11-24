package com.taskflow

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MockkBaselineTest {
    @Test
    fun `MockK stubs and verifies a collaborator`() {
        val clock = mockk<Clock>()
        every { clock.instant() } returns Instant.parse("2026-11-24T00:00:00Z")
        every { clock.zone } returns ZoneOffset.UTC

        assertEquals(Instant.parse("2026-11-24T00:00:00Z"), clock.instant())
        verify(exactly = 1) { clock.instant() }
    }
}

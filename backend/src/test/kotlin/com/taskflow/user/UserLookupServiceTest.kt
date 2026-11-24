package com.taskflow.user

import com.taskflow.common.RequestValidationException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Pageable

class UserLookupServiceTest {
    private val users = mockk<UserRepository>()
    private val service = UserLookupService(users)

    @Test
    fun `shorter than 2 characters after trimming is rejected without querying`() {
        listOf("", " ", "a", "  a  ").forEach { assertThrows<RequestValidationException> { service.search(it) } }
        verify(exactly = 0) { users.findByNameContainingIgnoreCase(any(), any()) }
    }

    @Test
    fun `queries with the trimmed text, caps at 20 and returns only id and name`() {
        val page = slot<Pageable>()
        every { users.findByNameContainingIgnoreCase("ann", capture(page)) } returns listOf(User("Ann", "ann@example.test", "hash").also { it.id = 4L })
        val r = service.search("  ann ")
        assertEquals(listOf(UserOption(4L, "Ann")), r)
        assertEquals(20, page.captured.pageSize)
    }
}

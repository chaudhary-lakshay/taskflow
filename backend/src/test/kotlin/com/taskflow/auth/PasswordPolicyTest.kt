package com.taskflow.auth

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PasswordPolicyTest {
    @Test
    fun `accepts 8 characters up to 72 bytes`() {
        listOf("x".repeat(8), "x".repeat(72), "é".repeat(36), "pässword-ok").forEach { assertNull(PasswordPolicy.violation(it), it) }
    }

    @Test
    fun `rejects null, blank, short and over-long (bytes, not characters)`() {
        listOf(null, "", "        ", "x".repeat(7), "x".repeat(73), "é".repeat(37)).forEach { assertNotNull(PasswordPolicy.violation(it), it) }
    }
}

package com.taskflow.auth

import com.taskflow.common.MaxBytes
import com.taskflow.user.User
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// Fields are nullable so a missing field becomes a field-level 400, not a JSON error.
data class RegisterRequest(
    @field:NotBlank(message = "Name is required") @field:Size(max = 100, message = "Name must be at most 100 characters")
    val name: String?,
    @field:NotBlank(message = "Email is required") @field:Email(message = "Email is not valid") @field:Size(max = 254, message = "Email must be at most 254 characters")
    val email: String?,
    @field:NotBlank(message = "Password is required") @field:Size(min = 8, message = "Password must be at least 8 characters")
    @field:MaxBytes(72, message = "Password must be at most 72 bytes")
    val password: String?,
) {
    override fun toString() = "RegisterRequest(email=$email)"
}

data class LoginRequest(
    @field:NotBlank(message = "Email is required") val email: String?,
    @field:NotBlank(message = "Password is required") @field:MaxBytes(72, message = "Password must be at most 72 bytes") val password: String?,
) {
    override fun toString() = "LoginRequest(email=$email)"
}

data class UserDto(val id: Long, val name: String, val email: String) {
    companion object {
        fun from(u: User) = UserDto(u.id!!, u.name, u.email)
    }
}

data class AuthResponse(val accessToken: String, val tokenType: String, val expiresIn: Long, val user: UserDto)

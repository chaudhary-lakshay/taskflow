package com.taskflow.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskflow.common.ApiError
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler

/**
 * Stateless bearer-token API. CSRF is off because no cookie ever authenticates a request.
 * Public: POST /api/auth/register, POST /api/auth/login, and GET /actuator/health (infrastructure).
 * No CORS rules: the browser reaches the API same-origin (Vite proxy in dev).
 */
@Configuration
class SecurityConfig(private val mapper: ObjectMapper) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val entryPoint = AuthenticationEntryPoint { _, response, _ ->
            response.setHeader("WWW-Authenticate", "Bearer")
            write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required or the token is invalid")
        }
        val denied = AccessDeniedHandler { _, response, _ ->
            write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied")
        }
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { it.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied) }
            .oauth2ResourceServer {
                it.jwt(Customizer.withDefaults()).authenticationEntryPoint(entryPoint).accessDeniedHandler(denied)
            }
        return http.build()
    }

    private fun write(response: HttpServletResponse, status: HttpStatus, code: String, message: String) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        mapper.writeValue(response.outputStream, ApiError(status.value(), code, message))
    }
}

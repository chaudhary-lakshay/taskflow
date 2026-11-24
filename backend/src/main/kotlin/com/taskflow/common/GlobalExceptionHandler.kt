package com.taskflow.common

import com.taskflow.auth.DuplicateEmailException
import com.taskflow.auth.InvalidCredentialsException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun reply(status: HttpStatus, code: String, message: String, fields: Map<String, String>? = null, correlationId: String? = null) =
        ResponseEntity.status(status).body(ApiError(status.value(), code, message, fields, correlationId))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(e: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val fields = linkedMapOf<String, String>()
        e.bindingResult.fieldErrors.forEach { fields.putIfAbsent(it.field, it.defaultMessage ?: "Invalid value") }
        return reply(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", fields)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(e: HttpMessageNotReadableException): ResponseEntity<ApiError> {
        // A well-formed body with a wrong value for one field (bad enum, bad date) is a field error.
        val field = (e.cause as? MismatchedInputException)?.path?.lastOrNull()?.fieldName
        return if (field != null) {
            reply(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", mapOf(field to "Invalid value"))
        } else {
            reply(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or malformed")
        }
    }

    @ExceptionHandler(RequestValidationException::class)
    fun validation(e: RequestValidationException) =
        reply(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", e.fieldErrors)

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun badParameter(e: MethodArgumentTypeMismatchException) =
        reply(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", mapOf(e.name to "Invalid value"))

    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException) = reply(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found")

    @ExceptionHandler(DuplicateEmailException::class)
    fun duplicateEmail(e: DuplicateEmailException) =
        reply(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered", mapOf("email" to "Email is already registered"))

    @ExceptionHandler(InvalidCredentialsException::class)
    fun badCredentials(e: InvalidCredentialsException) =
        reply(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password")

    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception): ResponseEntity<ApiError> {
        // Spring's own 404/405/415 etc. keep their status but get our shape.
        if (e is ErrorResponse) {
            val status = HttpStatus.resolve(e.statusCode.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR
            if (status.is4xxClientError) return reply(status, status.name, status.reasonPhrase)
        }
        val id = UUID.randomUUID().toString()
        log.error("Unhandled error, correlationId={}", id, e)
        return reply(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong", correlationId = id)
    }
}

package com.taskflow.common

/** The resource does not exist or belongs to someone else: both answer 404 so existence is not leaked. */
class NotFoundException : RuntimeException("Resource not found")

/** Field-level problems found by the service layer; answered as 400 VALIDATION_ERROR. */
class RequestValidationException(val fieldErrors: Map<String, String>) : RuntimeException("Invalid request") {
    constructor(field: String, message: String) : this(mapOf(field to message))
}

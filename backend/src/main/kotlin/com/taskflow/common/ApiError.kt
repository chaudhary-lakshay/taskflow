package com.taskflow.common

import com.fasterxml.jackson.annotation.JsonInclude

/** The one error shape for every failure, including the ones produced by security filters. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiError(
    val status: Int,
    val code: String,
    val message: String,
    val fieldErrors: Map<String, String>? = null,
    val correlationId: String? = null,
)

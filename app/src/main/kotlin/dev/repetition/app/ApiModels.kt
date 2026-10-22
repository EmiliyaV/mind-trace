package dev.repetition.app

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(val code: String, val message: String)

@Serializable
data class HealthResponse(val status: String)

class ValidationException(message: String) : RuntimeException(message)

class NotFoundException(message: String) : RuntimeException(message)

class ConflictException(message: String) : RuntimeException(message)

class ExternalDependencyException(message: String) : RuntimeException(message)

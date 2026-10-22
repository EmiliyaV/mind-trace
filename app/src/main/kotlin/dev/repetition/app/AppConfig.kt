package dev.repetition.app

import dev.repetition.infrastructure.DatabaseSettings
import dev.repetition.infrastructure.LlmSettings
import dev.repetition.infrastructure.TranscriptionSettings
import java.time.ZoneId

data class AppConfig(
    val host: String,
    val port: Int,
    val timeZone: ZoneId,
    val database: DatabaseSettings,
    val llm: LlmSettings,
    val transcription: TranscriptionSettings
) {
    companion object {
        fun fromEnvironment(): AppConfig = AppConfig(
            host = System.getenv("SERVER_HOST") ?: "0.0.0.0",
            port = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 8080,
            timeZone = ZoneId.systemDefault(),
            database = DatabaseSettings(
                host = required("DATABASE_HOST"),
                port = System.getenv("DATABASE_PORT")?.toIntOrNull() ?: 5432,
                name = required("DATABASE_NAME"),
                user = required("DATABASE_USER"),
                password = required("DATABASE_PASSWORD")
            ),
            llm = LlmSettings(
                chatCompletionsUrl = required("LLM_CHAT_COMPLETIONS_URL"),
                model = required("LLM_MODEL"),
                apiKey = required("LLM_API_KEY"),
                timeoutMs = System.getenv("LLM_TIMEOUT_MS")?.toLongOrNull() ?: 30000L
            ),
            transcription = TranscriptionSettings(
                baseUrl = required("TRANSCRIPTION_BASE_URL"),
                timeoutMs = System.getenv("TRANSCRIPTION_TIMEOUT_MS")?.toLongOrNull() ?: 180000L
            )
        )

        private fun required(name: String): String =
            System.getenv(name)?.takeIf { it.isNotBlank() }
                ?: error("Required environment variable is missing: $name")
    }
}

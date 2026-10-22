package dev.repetition.app

import dev.repetition.infrastructure.DatabaseFactoryProvider
import dev.repetition.infrastructure.DatabaseHealth
import dev.repetition.infrastructure.JdbcNoteRepository
import dev.repetition.infrastructure.JdbcRepetitionRepository
import dev.repetition.infrastructure.LlmNoteAnalyzer
import dev.repetition.infrastructure.LlmAnswerEvaluator
import dev.repetition.infrastructure.PythonTranscriber
import dev.repetition.infrastructure.JdbcAssessmentCompletionStore
import dev.repetition.assessment.AssessmentService
import dev.repetition.assessment.VoiceAssessmentService
import dev.repetition.assessment.InvalidAudioException
import dev.repetition.assessment.UnrecognizableAudioException
import dev.repetition.assessment.AudioTooLargeException
import dev.repetition.assessment.UnsupportedAudioFormatException
import dev.repetition.assessment.TranscriptionExternalException
import dev.repetition.assessment.InvalidAnswerException
import dev.repetition.assessment.AssessmentNotFoundException
import dev.repetition.assessment.AssessmentConflictException
import dev.repetition.assessment.AssessmentExternalException
import dev.repetition.scheduling.AdaptiveSchedulePlanner
import dev.repetition.notes.InvalidNoteException
import dev.repetition.notes.NoteAnalysisException
import dev.repetition.notes.NoteService
import dev.repetition.scheduling.FixedIntervalSchedulePlanner
import dev.repetition.scheduling.RepetitionService
import java.time.Clock
import dev.repetition.infrastructure.Migrations
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.calllogging.CallLogging
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main() {
    val config = AppConfig.fromEnvironment()
    val dataSource = DatabaseFactoryProvider.create(config.database)
    val analyzer = LlmNoteAnalyzer(config.llm)
    val evaluator = LlmAnswerEvaluator(config.llm)
    val transcriber = PythonTranscriber(config.transcription)
    val clock = Clock.system(config.timeZone)
    val noteService = NoteService(JdbcNoteRepository(dataSource), analyzer, clock, FixedIntervalSchedulePlanner())
    val repetitionRepository = JdbcRepetitionRepository(dataSource)
    val repetitionService = RepetitionService(repetitionRepository, clock)
    val assessmentService = AssessmentService(
        JdbcNoteRepository(dataSource), repetitionRepository,
        JdbcAssessmentCompletionStore(dataSource, AdaptiveSchedulePlanner(), config.timeZone),
        evaluator, clock
    )
    val voiceAssessmentService = VoiceAssessmentService(assessmentService, transcriber)
    try {
        Migrations.apply(dataSource)
        Runtime.getRuntime().addShutdownHook(Thread { analyzer.close(); evaluator.close(); transcriber.close(); dataSource.close() })
        embeddedServer(Netty, host = config.host, port = config.port) {
            configureHttp(DatabaseHealth(dataSource), noteService, repetitionService, assessmentService, voiceAssessmentService)
        }.start(wait = true)
    } catch (error: Exception) {
        analyzer.close()
        evaluator.close()
        transcriber.close()
        dataSource.close()
        throw error
    }
}

fun Application.configureHttp(
    databaseHealth: DatabaseHealth, noteService: NoteService, repetitionService: RepetitionService,
    assessmentService: AssessmentService, voiceAssessmentService: VoiceAssessmentService
) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = false })
    }
    install(CallLogging) {
        level = Level.INFO
    }
    install(StatusPages) {
        exception<Throwable> { call, error ->
            if (error is CancellationException) throw error
            val (status, code, message) = when (error) {
                is ValidationException, is InvalidNoteException, is InvalidAnswerException, is InvalidAudioException, is BadRequestException ->
                    Triple(HttpStatusCode.BadRequest, "INVALID_REQUEST", error.message ?: "Invalid request")
                is UnrecognizableAudioException -> Triple(HttpStatusCode.UnprocessableEntity, "AUDIO_UNRECOGNIZABLE", error.message ?: "Audio not recognized")
                is AudioTooLargeException -> Triple(HttpStatusCode.PayloadTooLarge, "AUDIO_TOO_LARGE", error.message ?: "Audio too large")
                is UnsupportedAudioFormatException -> Triple(HttpStatusCode.UnsupportedMediaType, "UNSUPPORTED_AUDIO", error.message ?: "Unsupported audio type")
                is NotFoundException, is AssessmentNotFoundException ->
                    Triple(HttpStatusCode.NotFound, "NOT_FOUND", error.message ?: "Not found")
                is ConflictException, is AssessmentConflictException ->
                    Triple(HttpStatusCode.Conflict, "CONFLICT", error.message ?: "Conflict")
                is TranscriptionExternalException -> Triple(HttpStatusCode.BadGateway, "TRANSCRIPTION_ERROR", "Transcription service unavailable")
                is NoteAnalysisException, is AssessmentExternalException, is ExternalDependencyException ->
                    Triple(HttpStatusCode.BadGateway, "EXTERNAL_ERROR", "External dependency unavailable")
                else -> {
                    call.application.log.error("Unhandled HTTP error", error)
                    Triple(HttpStatusCode.InternalServerError, "INTERNAL_ERROR", "Internal server error")
                }
            }
            call.respond(status, ApiError(code, message))
        }
    }
    routing {
        get("/health") {
            call.respond(HealthResponse("UP"))
        }
        get("/ready") {
            if (databaseHealth.isReady()) {
                call.respond(HealthResponse("READY"))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, ApiError("DATABASE_UNAVAILABLE", "Database unavailable"))
            }
        }
        noteRoutes(noteService)
        repetitionRoutes(noteService, repetitionService)
        assessmentRoutes(assessmentService, repetitionService)
        audioAssessmentRoutes(voiceAssessmentService, repetitionService)
    }
}

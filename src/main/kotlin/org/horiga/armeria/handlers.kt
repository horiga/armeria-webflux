package org.horiga.armeria

import com.fasterxml.jackson.annotation.JsonProperty
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.server.ServiceRequestContext
import com.linecorp.armeria.server.annotation.Default
import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction
import com.linecorp.armeria.server.annotation.Get
import com.linecorp.armeria.server.annotation.Param
import com.linecorp.armeria.server.annotation.Produces
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.util.annotation.Nullable
import kotlin.random.Random

data class EchoResponseMessage(
    var message: String
)

@Component
class ApiRequestHandler(
    private val properties: Properties,
    val apiClient: ApiClient
) {
    companion object {
        val log = LoggerFactory.getLogger(ApiRequestHandler::class.java)!!
    }

    @Get("/produce")
    @Produces("application/json; charset=utf-8")
    fun produce(
        @Param("message") @Default("echo") message: String,
        @Param("delay") @Nullable delay: Long?
    ): Mono<EchoResponseMessage> {
        log.info(
            "[start] api#produce: message:{}, delay:{}, credentials:{}",
            message, delay, RequestContexts.credentials()
        )
        try {
            return apiClient.fetch("/echo?message=$message")
                .zipWhen {
                    log.info("handle @zipWhen, message=${it.message}")
                    apiClient.fetch("/echo?message=${it.message.reversed()}")
                }.flatMap { tuple ->
                    log.info("handle @flatMap, t1.message=${tuple.t1.message}, t2.message=${tuple.t2.message}")
                    if (message in "error") {
                        return@flatMap Mono.error(RuntimeException("contains error message"))
                    }
                    val results = "${tuple.t1.message}:${tuple.t2.message}"
                    Mono.just(EchoResponseMessage(results))
                }.onErrorMap(RuntimeException::class.java) {
                    // handle RuntimeException and map to IllegalStateException
                    log.error("handle @onErrorMap")
                    throw IllegalStateException(it)
                }
        } finally {
            // Note: this log will be output before the contents of the try are processed.
            log.info("[end] api#produce")
        }
    }

    private fun delay(delay: Long?) =
        delay?.let { "delay=$delay" } ?: Random.nextInt(0, 99).takeIf { it < 3 }
            ?.let { "delay=${properties.connectionTimeout.toMillis() + 2000}" } ?: ""
}

@Component
class ExceptionHandler : ExceptionHandlerFunction {

    data class ErrorMessage(
        @JsonProperty("error_message")
        val message: String
    )

    companion object {
        val log = LoggerFactory.getLogger(ExceptionHandler::class.java)!!
    }

    override fun handleException(
        ctx: ServiceRequestContext,
        req: HttpRequest,
        cause: Throwable
    ): HttpResponse {
        log.error("HANDLE ERROR IN ERROR_HANDLER!!", cause)
        return when (cause) {
            is NoSuchElementException -> HttpResponse.of(
                HttpStatus.NOT_FOUND, MediaType.JSON_UTF_8, """
            {"error_message": "No such elements"}
        """.trimIndent()
            )
            else -> HttpResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR, MediaType.JSON_UTF_8, """
            {"error_message": "Internal server error"}
        """.trimIndent()
            )
        }
    }
}
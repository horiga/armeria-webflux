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

    // Sequential task (Mono.zipWhen(task_result) -> flatMap)
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
                .zipWhen { res ->
                    log.info("handle @zipWhen, message=${res.message}")
                    apiClient.fetch("/echo?message=${res.message.reversed()}")
                }.flatMap { tuple ->
                    log.info("handle @flatMap, t1.message=${tuple.t1.message}, t2.message=${tuple.t2.message}")
                    if (message in "error") {
                        return@flatMap Mono.error(RuntimeException("contains error message"))
                    }
                    val results = "${tuple.t1.message}:${tuple.t2.message}"
                    Mono.just(EchoResponseMessage(results))
                }.onErrorMap(RuntimeException::class.java) { err ->
                    // handle RuntimeException and map to IllegalStateException
                    log.error("handle @onErrorMap")
                    throw IllegalStateException(err)
                }
        } finally {
            // Note: this log will be output before the contents of the try are processed.
            log.info("[end] api#produce")
        }
    }

    // Parallel task (Mono.zip(task...) -> flatMap)
    @Get("/parallel")
    @Produces("application/json; charset=utf-8")
    fun parallel(
        @Param("message") @Default("echo") message: String
    ): Mono<EchoResponseMessage> {
        return Mono.zip(
            apiClient.fetch("/echo?message=${message}1&delay=3000"),
            apiClient.fetch("/echo?message=${message}2&delay=2000"),
            apiClient.fetch("/echo?message=${if(message in "err") "nf" else message + "3"}"),
            apiClient.fetch("/echo?message=${message}4&delay=1000"),
        ).flatMap {
            log.info("handle Mono.zip/@flatMap {}")
            val results = "${it.t1.message},${it.t2.message},${it.t3.message},${it.t4.message}"
            Mono.just(EchoResponseMessage(results))
        }.onErrorMap { err ->
            log.error("handle @onErrorMap", err)
            throw IllegalStateException(err)
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
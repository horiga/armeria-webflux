package org.horiga.armeria

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.HttpHeaderNames
import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.RequestHeaders
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.util.concurrent.CompletionException

@Component
class ApiClient(
    private val webClient: WebClient
) {
    fun fetch(path: String): Mono<EchoResponseMessage> {
        log.info("START API CALLING. path={}", path)
        return webClient
            .execute(
                RequestHeaders.builder(HttpMethod.GET, path)
                    .add(HttpHeaderNames.ACCEPT, "application/json")
                    .build()
            )
            .aggregate()
            .handle { res, cause ->
                log.info("RECEIVED HTTP RESPONSE! res=${res}, cause=$cause")
                if (cause != null) {
                    throw CompletionException("Failed to Calling API", cause)
                }
                val responseBody = res.contentUtf8()
                when {
                    !res.status().isSuccess ->
                        throw CompletionException(
                            IllegalStateException(
                                "Handle error response from network. status=${res.status().code()}"
                            )
                        )
                    else -> try {
                        objectMapper.readValue(responseBody, EchoResponseMessage::class.java)
                    } catch (e: JsonProcessingException) {
                        throw CompletionException("Failed to parse response json. $responseBody", e)
                    }
                }
            }.toMono()
    }

    companion object {
        val log = LoggerFactory.getLogger(ApiClient::class.java)!!
        val objectMapper = jacksonObjectMapper()
            .configure(SerializationFeature.INDENT_OUTPUT, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
            .configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)!!
    }
}
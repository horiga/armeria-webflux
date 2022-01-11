package org.horiga.armeria.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.RequestHeaders
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

data class VerifyResponseMessage(
    val userId: String
)

@Service
class VerifyService(
    val webClient: WebClient,
    val objectMapper: ObjectMapper
) {
    companion object {
        val log = LoggerFactory.getLogger(VerifyService::class.java)!!
    }

    data class RequestCredentials(
        val userId: String,
        val requestId: String = UUID.randomUUID().toString()
    )

    fun verify(request: HttpRequest): CompletableFuture<RequestCredentials> = try {
        log.info("[start] VERIFY")
        val requestId = request.headers().get("X-REQUEST-ID") ?: UUID.randomUUID().toString()
        val future =
            webClient.execute(RequestHeaders.builder(HttpMethod.GET, "/verify?token=$requestId").build())
                .aggregate()
                .thenApply {
                    val json = it.contentUtf8()
                    if (!it.status().isSuccess) {
                        throw IllegalStateException(
                            "handle error response, status=${it.status().code()}, $json"
                        )
                    }
                    val credentials: VerifyResponseMessage = objectMapper.readValue(json)
                    RequestCredentials(credentials.userId, requestId)
                }.exceptionally { err ->
                    log.warn("Failed to request 'GET /verify?request=$requestId'", err)
                    if (err is CompletionException) {
                        throw err
                    }
                    throw CompletionException(err)
                }
        future
    } catch (e: Throwable) {
        log.error("HANDLE IN VERIFY", e)
        CompletableFuture.failedFuture(e)
    } finally {
        log.info("[end] VERIFY")
    }
}
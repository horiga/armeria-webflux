package org.horiga.armeria

import com.fasterxml.jackson.annotation.JsonProperty
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.common.MediaTypeNames
import com.linecorp.armeria.server.ServiceRequestContext
import com.linecorp.armeria.server.annotation.Consumes
import com.linecorp.armeria.server.annotation.Default
import com.linecorp.armeria.server.annotation.Delete
import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction
import com.linecorp.armeria.server.annotation.Get
import com.linecorp.armeria.server.annotation.Param
import com.linecorp.armeria.server.annotation.Post
import com.linecorp.armeria.server.annotation.Produces
import com.linecorp.armeria.server.annotation.RequestObject
import org.horiga.armeria.service.BookService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.annotation.Nullable
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Pattern
import javax.validation.constraints.Size

data class EchoResponseMessage(
    var message: String
)

data class BookMessage(
    @field:NotBlank
    @field:Size(max = 200)
    var name: String,
    @field:NotBlank
    @field:Size(max = 200)
    var isbn: String,
    @field:Min(1)
    var price: Int,
    @field:NotBlank
    @field:Pattern(regexp = "^[12][0-9]{3}-([0][1-9]|[1][0-2])-([0-2][0-9]|[3][0-1])$")
    var publishedAt: String,

    // for response message
    var id: Long = 0
) {
    companion object {
        fun of(book: Book) = BookMessage(
            book.name,
            book.isbn,
            book.price,
            book.publishedAt.toString("yyyy-MM-dd"),
            book.id!!
        )
    }

}

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
    @Produces(MediaTypeNames.JSON_UTF_8)
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

}

@Component
class BooksHandler(
    val bookService: BookService
) {
    @Get("/books/search")
    @Produces(MediaTypeNames.JSON_UTF_8)
    fun search(
        @Param("name") @Default("") name: String,
    ): Flux<BookMessage> {
        val books = if (name.isBlank())
            bookService.findAll()
        else bookService.searchByName(name)
        return books.map {
            BookMessage.of(it)
        }
    }

    @Get("/books")
    @Produces(MediaTypeNames.JSON_UTF_8)
    fun findBy(
        @Param("id") @Default("-1") id: Long,
        @Param("isbn") @Default("") isbn: String,
    ): Mono<BookMessage> = when {
        id >= 0 -> bookService.findById(id)
        isbn.isNotBlank() -> bookService.findByIsbn(isbn)
        else -> throw IllegalArgumentException("search condition is not specified.")
    }.map { BookMessage.of(it) }

    @Post("/books")
    @Consumes(MediaTypeNames.JSON_UTF_8)
    @Produces(MediaTypeNames.JSON_UTF_8)
    fun add(
        @RequestObject message: BookMessage
    ) = bookService.add(message.name, message.isbn, message.price, message.publishedAt).map {
        BookMessage.of(it)
    }

    @Delete("/books/{id}")
    fun delete(@Param("id") id: Long) = bookService.delete(id)
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
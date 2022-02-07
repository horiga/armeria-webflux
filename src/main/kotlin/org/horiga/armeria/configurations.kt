package org.horiga.armeria

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.fasterxml.jackson.datatype.jsr310.JSR310Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.linecorp.armeria.client.ClientFactory
import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.client.logging.LoggingClient
import com.linecorp.armeria.common.HttpHeaderNames
import com.linecorp.armeria.common.metric.MeterIdPrefixFunction
import com.linecorp.armeria.server.annotation.JacksonRequestConverterFunction
import com.linecorp.armeria.server.annotation.JacksonResponseConverterFunction
import com.linecorp.armeria.server.metric.MetricCollectingService
import com.linecorp.armeria.spring.ArmeriaServerConfigurator
import io.micrometer.core.instrument.MeterRegistry
import org.horiga.armeria.service.VerifyService
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@ConfigurationProperties(prefix = "app")
data class Properties(
    var endpoint: String = "",
    var connectionTimeout: Duration = Duration.ofMillis(1000),
    var responseTimeout: Duration = Duration.ofMillis(3000),
    var writeTimeout: Duration = Duration.ofMillis(3000)
)

@Configuration
@EnableConfigurationProperties(Properties::class)
class ArmeriaServerConfiguration(
    val properties: Properties
) {
    @Bean
    fun armeriaServer(
        objectMapper: ObjectMapper,
        exceptionHandler: ExceptionHandler,
        apiRequestHandler: ApiRequestHandler,
        booksHandler: BooksHandler,
        verifyService: VerifyService
    ) = ArmeriaServerConfigurator { sb ->
        sb.annotatedService()
            .defaultServiceName("ApiService")
            .pathPrefix("/api/")
            .requestTimeout(Duration.ofMillis(30000))
            .responseConverters(JacksonResponseConverterFunction(objectMapper))
            //.accessLogWriter(AccessLogWriter.combined(), false)
            .decorators(
                MetricCollectingService.newDecorator(MeterIdPrefixFunction.ofDefault("http-api")),
                AuthenticationService.newDecorator(verifyService),
            )
            .exceptionHandlers(exceptionHandler)
            .build(apiRequestHandler)

        sb.annotatedService()
            .defaultServiceName("books")
            .requestTimeout(Duration.ofMillis(30000))
            .requestConverters(JacksonRequestConverterFunction(objectMapper))
            .responseConverters(JacksonResponseConverterFunction(objectMapper))
            .decorators(MetricCollectingService.newDecorator(MeterIdPrefixFunction.ofDefault("books")))
            .exceptionHandlers(exceptionHandler)
            .build(booksHandler)
    }

    @Bean
    fun clientFactory(meterRegistry: MeterRegistry) = ClientFactory.builder()
        .connectTimeout(properties.connectionTimeout)
        .useHttp1Pipelining(false)
        .meterRegistry(meterRegistry)
        .build()

    @Bean
    fun webClient(clientFactory: ClientFactory): WebClient {
        return WebClient.builder(properties.endpoint)
            .factory(clientFactory)
            .responseTimeout(properties.responseTimeout)
            .writeTimeout(properties.writeTimeout)
            .decorator(LoggingClient.newDecorator())
            .addHeader(HttpHeaderNames.USER_AGENT, "armeria-webflux")
            .build()
    }

    @Bean
    fun objectMapper() = jacksonObjectMapper()
        .registerModule(JavaTimeModule()) // java.time.*
        .registerModule(JodaModule())
        .configure(SerializationFeature.INDENT_OUTPUT, false)
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
        .configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        .configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)!!
}
package org.horiga.armeria.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.horiga.armeria.ApiClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.util.concurrent.Executors
import kotlin.random.Random

@Component
class DataHolder(
    val apiClient: ApiClient
) {

    companion object {
        val log = LoggerFactory.getLogger(DataHolder::class.java)!!
    }

    private val workerThreadPool =
        Executors.newFixedThreadPool(
            5, ThreadFactoryBuilder()
                .setNameFormat("data-holder-worker-%d")
                .build()
        )

    val holder: Cache<String, String> = Caffeine.newBuilder().maximumSize(10).recordStats().build()

    @Scheduled(fixedDelay = 10000)
    fun refresh() {
        log.info("")
        log.info("Scheduler started. current cached-keys: ${holder.asMap().keys}")
        val num = Random.nextInt(1, 13)
        newKeys(num).collectList()
            .subscribeOn(Schedulers.fromExecutorService(workerThreadPool))
            .subscribe { messages ->
                if (messages.isEmpty()) {
                    log.warn("==== INVALIDATE ALL ====")
                    holder.invalidateAll()
                } else {
                    val removeKeys = holder.asMap().keys.filter { !messages.contains(it) }
                    log.warn("=== INVALIDATE KEYS: $removeKeys ====")
                    if (removeKeys.isNotEmpty())
                        holder.invalidateAll(removeKeys)
                    messages.map { message ->
                        val delay = Random.nextInt(30, 3000)
                        apiClient.fetch("/echo?message=$message&delay=$delay")
                            .subscribe({ res ->
                                           log.info("#### handle @subscribe. key=$message ####")
                                           holder.put(message, res.message.reversed())
                                       },
                                       { err ->
                                           log.error(
                                               "FATAL FAILED TO CACHE RELOADED. " +
                                                   "handle subscribe onErrorDropped!! ${err.message}", err
                                           )
                                       }
                            )
                    }
                }
            }
        log.info("scheduler end")
    }

    // generate random cache keys
    private fun newKeys(num: Int): Flux<String> {
        log.info("=> NUM OF MESSAGES = $num")
        if (num > 10)
            return Flux.empty()
        val range: IntRange = 1..num
        return Flux.fromIterable(range.map {
            // 'Message9' always api errors. For testing subscribe errors
            if (it == 6) "nf" else "Message$it"
        })
    }

}
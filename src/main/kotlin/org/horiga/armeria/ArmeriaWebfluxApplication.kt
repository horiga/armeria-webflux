package org.horiga.armeria

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import java.security.Security

@SpringBootApplication
@EnableScheduling
class ArmeriaWebfluxApplication {
	companion object {
		init {
			Security.setProperty("networkaddress.cache.ttl", "10")
			Security.setProperty("networkaddress.cache.negative.ttl", "0")
			System.setProperty("io.netty.tryReflectionSetAccessible", "true")
		}
	}
}

fun main(args: Array<String>) {
	runApplication<ArmeriaWebfluxApplication>(*args)
}

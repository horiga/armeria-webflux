import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "2.5.8"
	id("io.spring.dependency-management") version "1.0.11.RELEASE"
	kotlin("jvm") version "1.6.10"
	kotlin("plugin.spring") version "1.6.10"
}

group = "org.horiga"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencyManagement {
	imports {
		mavenBom("com.linecorp.armeria:armeria-bom:1.1.0")
		mavenBom("io.netty:netty-bom:4.1.52.Final")
	}
}

dependencies {
	implementation("com.linecorp.armeria:armeria-spring-boot2-webflux-starter")
	implementation("com.linecorp.armeria:armeria-spring-boot2-actuator-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
	implementation("io.projectreactor.netty:reactor-netty")
	implementation("io.micrometer:micrometer-registry-prometheus:1.8.1")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("io.projectreactor.addons:reactor-extra:3.3.4.RELEASE")
	implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.0.2.RELEASE")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("com.github.jasync-sql:jasync-r2dbc-mysql:1.2.3")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-joda")

	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "11"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

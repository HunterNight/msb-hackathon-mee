plugins {
  java
  id("org.springframework.boot") version "4.0.8"
  id("io.spring.dependency-management") version "1.1.7"
}

group = "vn.msb.digibank"
version = "1.0.0"
description = "memory-hook-service"

java {
  toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories { mavenCentral() }

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webmvc")
  // Boot 4 splits RestClient's auto-configuration out of the web starter; peer clients need it.
  implementation("org.springframework.boot:spring-boot-restclient")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  // Boot 4 ships Liquibase's auto-configuration in its own module; without it the changelog
  // never runs and Hibernate validation fails on an empty schema.
  implementation("org.springframework.boot:spring-boot-liquibase")
  implementation("org.liquibase:liquibase-core")
  runtimeOnly("org.postgresql:postgresql")
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("net.javacrumbs.shedlock:shedlock-spring:7.10.0")
  implementation("net.javacrumbs.shedlock:shedlock-provider-redis-spring:7.10.0")
  implementation("org.springframework.kafka:spring-kafka")
  implementation("io.cloudevents:cloudevents-kafka:4.1.1")
  implementation("io.cloudevents:cloudevents-json-jackson:4.1.1")
  // pgvector is reached through JdbcTemplate rather than a vector-store abstraction: the rows are
  // encrypted per customer and namespaced by pseudo id, which no generic store models.
  implementation("io.micrometer:micrometer-tracing-bridge-otel")
  implementation("io.micrometer:micrometer-registry-prometheus")

  compileOnly("org.projectlombok:lombok:1.18.48")
  annotationProcessor("org.projectlombok:lombok:1.18.48")
  implementation("org.mapstruct:mapstruct:1.6.3")
  annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.security:spring-security-test")

  testImplementation("org.testcontainers:junit-jupiter")
  testImplementation("org.testcontainers:postgresql")
  testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:3.0.1")
}

dependencyManagement {
  imports {
    mavenBom("org.testcontainers:testcontainers-bom:1.21.3")
  }
}

tasks.withType<JavaCompile> {
  options.compilerArgs.addAll(listOf("-parameters", "-Amapstruct.defaultComponentModel=spring"))
}

tasks.withType<Test> { useJUnitPlatform() }

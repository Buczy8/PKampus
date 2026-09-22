plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "pl.edu.pk"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

extra["commons-lang3.version"] = "3.20.0"

configurations.compileClasspath {
    extendsFrom(configurations.annotationProcessor.get())
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // OpenAPI 3 / Swagger Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")

    // Database & Migrations
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // JWT (JJWT 0.12.6)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // MinIO AWS S3 Client
    implementation("io.minio:minio:8.6.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")

    // Cache & Rate Limiting (In-Memory Blacklist & Throttling)
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.bucket4j:bucket4j-core:8.10.1")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Devtools
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")

    testImplementation("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

import nu.studer.gradle.jooq.JooqGenerate
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Property
import org.jooq.meta.jaxb.Target
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0"
    id("nu.studer.jooq") version "10.1"
    id("org.springframework.boot") version "3.5.10"
    id("io.spring.dependency-management") version "1.1.7"
    id("dev.detekt") version "2.0.0-alpha.2"
}

group = "ru.kinoko.kinchat"
version = "1.0.0"

val detektVersion = "2.0.0-alpha.2"
val jooqVersion = "3.19.29"
val jwtVersion = "0.12.6"
val minioVersion = "8.6.0"

val jooqSchemaScript = "src/main/resources/db/migration/V1__init_schema.sql"

sourceSets.main {
    java.srcDirs("$buildDir/generated/jooq")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:$jwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jwtVersion")

    // Flyway
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // jOOQ
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.jooq:jooq:$jooqVersion")
    jooqGenerator("org.postgresql:postgresql")
    jooqGenerator("org.jooq:jooq-codegen:$jooqVersion")
    jooqGenerator("org.jooq:jooq-meta:$jooqVersion")
    jooqGenerator("org.jooq:jooq-meta-extensions:$jooqVersion")
    runtimeOnly("org.postgresql:postgresql")

    // MinIO
    implementation("io.minio:minio:$minioVersion")

    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Detekt
    detektPlugins("dev.detekt:detekt-rules:$detektVersion")
    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:$detektVersion")

    // Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.16")

    // Logging
    runtimeOnly("io.github.oshai:kotlin-logging-jvm:7.0.7")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}

configurations {
    all {
        exclude("org.springframework.boot", "spring-boot-starter-logging")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(file("$rootDir/detekt.yml"))
    autoCorrect = false
}

jooq {
    version.set(jooqVersion)

    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(true)

            jooqConfiguration.apply {
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"

                    database = Database().apply {
                        name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                        inputSchema = "PUBLIC"
                        isIncludeForeignKeys = false
                        properties = listOf(
                            Property().apply {
                                key = "scripts"
                                value = jooqSchemaScript
                            },
                            Property().apply {
                                key = "sort"
                                value = "semantic"
                            },
                            Property().apply {
                                key = "unqualifiedSchema"
                                value = "public"
                            },
                            Property().apply {
                                key = "defaultNameCase"
                                value = "lower"
                            },
                        )
                    }

                    generate = Generate().apply {
                        isRelations = false

                        isPojos = true
                        isRecords = true
                        isDaos = false
                        isJpaAnnotations = false

                        isJavaTimeTypes = true

                        // Генерировать Kotlin data class POJO
                        isPojosAsKotlinDataClasses = true
                        isImmutablePojos = true

                        // Сделать non-nullable свойства там, где столбец NOT NULL
                        isKotlinNotNullPojoAttributes = true
                        isKotlinNotNullInterfaceAttributes = true

                        // Отключить defaulted nullable (иначе все поля остаются nullable)
                        isKotlinDefaultedNullablePojoAttributes = false
                    }

                    target = Target().apply {
                        packageName = "ru.kinoko.kinchat.jooq"
                        directory = "build/generated/jooq/main"
                    }
                }
            }
        }
    }
}

tasks.named<JooqGenerate>("generateJooq") {
    allInputsDeclared.set(true)
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(tasks.named("generateJooq"))
}

tasks.withType<Jar> {
    enabled = false
}

tasks.withType<BootJar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    enabled = true
}

tasks.withType<Test> {
    useJUnitPlatform()
}

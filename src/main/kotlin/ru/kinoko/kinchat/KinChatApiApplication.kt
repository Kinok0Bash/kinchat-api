package ru.kinoko.kinchat

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import ru.kinoko.kinchat.properties.AppProperties
import ru.kinoko.kinchat.properties.MinioProperties

@SpringBootApplication
@EnableConfigurationProperties(
    AppProperties::class,
    MinioProperties::class
)
class KinChatApiApplication

fun main(args: Array<String>) {
    runApplication<KinChatApiApplication>(*args)
}

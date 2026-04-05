package ru.kinoko.kinchat.config

import io.minio.MinioClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.kinoko.kinchat.properties.MinioProperties

@Configuration
class MinioConfig(
    private val minioProperties: MinioProperties,
) {
    @Bean
    fun minioClient(): MinioClient = MinioClient
        .builder()
        .endpoint(minioProperties.urls.api)
        .credentials(minioProperties.login, minioProperties.password)
        .build()
}

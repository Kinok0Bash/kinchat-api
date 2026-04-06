package ru.kinoko.kinchat.config

import io.minio.MinioClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.kinoko.kinchat.properties.MinioProperties

@Configuration
class MinioConfig(
    private val minioProperties: MinioProperties,
) {
    @Bean
    fun minioClient(): MinioClient {
        logger.info("Creating MinIO client endpoint={}", minioProperties.urls.api)
        return MinioClient
            .builder()
            .endpoint(minioProperties.urls.api)
            .credentials(minioProperties.login, minioProperties.password)
            .build()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MinioConfig::class.java)
    }
}

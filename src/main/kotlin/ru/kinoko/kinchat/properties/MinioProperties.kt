package ru.kinoko.kinchat.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.minio")
data class MinioProperties(
    val login: String,
    val password: String,
    val urls: MinioUrls,
    val avatars: MinioBucketProperties,
    val messageFiles: MinioBucketProperties,
) {

    data class MinioUrls(
        val api: String,
    )

    data class MinioBucketProperties(
        val bucket: String,
        val publicUrlPrefix: String,
    )
}

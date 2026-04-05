package ru.kinoko.kinchat.service

import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import ru.kinoko.kinchat.dto.StoredObject
import ru.kinoko.kinchat.exception.UpstreamDependencyException
import ru.kinoko.kinchat.properties.MinioProperties.MinioBucketProperties
import java.util.concurrent.ConcurrentHashMap

@Service
class MinioStorageService(
    private val minioClient: MinioClient,
) {
    private val verifiedBuckets = ConcurrentHashMap.newKeySet<String>()

    fun upload(
        bucket: MinioBucketProperties,
        objectKey: String,
        file: MultipartFile,
        contentType: String,
    ): StoredObject {
        ensureBucket(bucket.bucket)

        try {
            file.inputStream.use { inputStream ->
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(bucket.bucket)
                        .`object`(objectKey)
                        .stream(inputStream, file.size, -1)
                        .contentType(contentType)
                        .build(),
                )
            }
        } catch (exception: Exception) {
            throw UpstreamDependencyException("Failed to upload file to object storage", exception)
        }

        return StoredObject(
            objectKey = objectKey,
            url = bucket.publicUrlPrefix + objectKey,
            fileName = file.originalFilename ?: objectKey.substringAfterLast('/'),
            contentType = contentType,
            sizeBytes = file.size,
        )
    }

    fun delete(bucket: MinioBucketProperties, objectKey: String) {
        runCatching {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucket.bucket)
                    .`object`(objectKey)
                    .build(),
            )
        }
    }

    private fun ensureBucket(bucketName: String) {
        if (verifiedBuckets.contains(bucketName)) {
            return
        }

        synchronized(this) {
            if (verifiedBuckets.contains(bucketName)) {
                return
            }

            try {
                val exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                        .bucket(bucketName)
                        .build(),
                )
                if (!exists) {
                    minioClient.makeBucket(
                        MakeBucketArgs.builder()
                            .bucket(bucketName)
                            .build(),
                    )
                }
                verifiedBuckets.add(bucketName)
            } catch (exception: Exception) {
                throw UpstreamDependencyException("Failed to prepare object storage bucket $bucketName", exception)
            }
        }
    }
}

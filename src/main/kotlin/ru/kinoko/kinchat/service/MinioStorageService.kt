package ru.kinoko.kinchat.service

import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import org.slf4j.LoggerFactory
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
        logger.info(
            "Uploading object to MinIO bucket={} objectKey={} fileName={} sizeBytes={} contentType={}",
            bucket.bucket,
            objectKey,
            file.originalFilename,
            file.size,
            contentType,
        )
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
            logger.error(
                "MinIO upload failed bucket={} objectKey={}",
                bucket.bucket,
                objectKey,
                exception,
            )
            throw UpstreamDependencyException("Failed to upload file to object storage", exception)
        }

        val storedObject = StoredObject(
            objectKey = objectKey,
            url = bucket.publicUrlPrefix + objectKey,
            fileName = file.originalFilename ?: objectKey.substringAfterLast('/'),
            contentType = contentType,
            sizeBytes = file.size,
        )
        logger.info("MinIO upload completed bucket={} objectKey={}", bucket.bucket, objectKey)
        return storedObject
    }

    fun delete(bucket: MinioBucketProperties, objectKey: String) {
        logger.info("Deleting object from MinIO bucket={} objectKey={}", bucket.bucket, objectKey)
        runCatching {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucket.bucket)
                    .`object`(objectKey)
                    .build(),
            )
            logger.info("Deleted object from MinIO bucket={} objectKey={}", bucket.bucket, objectKey)
        }.onFailure { exception ->
            logger.info(
                "Failed to delete object from MinIO bucket={} objectKey={} cause={}",
                bucket.bucket,
                objectKey,
                exception.javaClass.simpleName,
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
                logger.info("Verifying MinIO bucket={}", bucketName)
                val exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                        .bucket(bucketName)
                        .build(),
                )
                if (!exists) {
                    logger.info("Creating missing MinIO bucket={}", bucketName)
                    minioClient.makeBucket(
                        MakeBucketArgs.builder()
                            .bucket(bucketName)
                            .build(),
                    )
                }
                verifiedBuckets.add(bucketName)
                logger.info("MinIO bucket ready bucket={} created={}", bucketName, !exists)
            } catch (exception: Exception) {
                logger.error("Failed to prepare MinIO bucket={}", bucketName, exception)
                throw UpstreamDependencyException("Failed to prepare object storage bucket $bucketName", exception)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MinioStorageService::class.java)
    }
}

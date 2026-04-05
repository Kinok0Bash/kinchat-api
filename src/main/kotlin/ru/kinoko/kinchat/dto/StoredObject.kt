package ru.kinoko.kinchat.dto

data class StoredObject(
    val objectKey: String,
    val url: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
)

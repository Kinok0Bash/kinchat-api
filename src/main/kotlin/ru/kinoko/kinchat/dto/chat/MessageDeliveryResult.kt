package ru.kinoko.kinchat.dto.chat

data class MessageDeliveryResult(
    val message: MessageResponse,
    val created: Boolean,
)

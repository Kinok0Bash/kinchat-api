package ru.kinoko.kinchat.dto.user

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Ответ на замену аватарки")
data class AvatarUploadResponse(
    val user: PublicUserResponse,
)

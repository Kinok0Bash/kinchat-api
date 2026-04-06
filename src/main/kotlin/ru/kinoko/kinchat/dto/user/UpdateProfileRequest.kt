package ru.kinoko.kinchat.dto.user

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(description = "Частичное обновление профиля текущего пользователя")
data class UpdateProfileRequest(
    @field:Size(min = 3, max = 32)
    @field:Pattern(regexp = "^[A-Za-z0-9._-]+$")
    @field:Schema(
        example = "ivan.petrov",
        description = "Новый login пользователя. Если поле не передано, login не меняется.",
    )
    val login: String? = null,

    @field:Size(max = 60)
    @field:Schema(
        example = "Иван",
        description = "Новое имя пользователя. Если поле не передано, firstName не меняется.",
    )
    val firstName: String? = null,

    @field:Size(max = 60)
    @field:Schema(
        example = "Петров",
        description = "Новая фамилия пользователя. Если поле не передано, lastName не меняется.",
    )
    val lastName: String? = null,
)

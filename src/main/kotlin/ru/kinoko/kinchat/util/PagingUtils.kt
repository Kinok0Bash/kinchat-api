package ru.kinoko.kinchat.util

fun calculateTotalPages(totalElements: Long, size: Int): Int = when {
    size <= 0 -> 0
    totalElements == 0L -> 0
    else -> ((totalElements + size - 1) / size).toInt()
}

package ru.kinoko.kinchat.util

fun String?.isForwardedProto(protocol: String): Boolean = this?.equals(protocol, ignoreCase = true) == true

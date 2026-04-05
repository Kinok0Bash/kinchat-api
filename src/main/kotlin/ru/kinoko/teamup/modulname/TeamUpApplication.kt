package ru.kinoko.teamup.modulname

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TeamUpApplication

fun main(args: Array<String>) {
    runApplication<TeamUpApplication>(*args)
}

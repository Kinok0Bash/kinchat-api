package ru.kinoko.teamup.modulname.config

import org.jooq.conf.Settings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JooqConfig {

    @Bean
    fun jooqSettings(): Settings = Settings()
        .withRenderSchema(false)
}

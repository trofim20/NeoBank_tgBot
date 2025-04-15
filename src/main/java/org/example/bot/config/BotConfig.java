package org.example.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация телеграм бота
 */
@ConfigurationProperties(prefix = "telegram.bot")
public record BotConfig(
        String name,
        String token
) {
    public BotConfig {
        System.out.println("Config loaded. Name: " + name + ", Token: " + token);
    }

}
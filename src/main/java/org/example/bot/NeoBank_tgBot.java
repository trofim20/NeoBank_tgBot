package org.example.bot;

import org.example.bot.config.BotConfig;
import org.example.bot.fiegn.NeoFlexTelegramAPI;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableConfigurationProperties(BotConfig.class)
@EnableFeignClients(basePackageClasses = NeoFlexTelegramAPI.class)
public class NeoBank_tgBot {
    public static void main(String[] args) {
        SpringApplication.run(NeoBank_tgBot.class, args);
    }
}
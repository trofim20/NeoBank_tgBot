package org.example.bot.config;

import org.example.bot.service.TelegramBot;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Инициализация телеграм бота
 */
@Component
public class BotInit {
    /**
     * Телеграм бот
     */
    private final TelegramBot bot;

    public BotInit(TelegramBot bot) {
        this.bot = bot;
    }

    /**
     * Инициализирует телеграм бота
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            new TelegramBotsApi(DefaultBotSession.class).registerBot(bot);
            System.out.println("Бот @" + bot.getBotUsername() + " успешно запущен!");
        } catch (TelegramApiException e) {
            throw new RuntimeException("Ошибка регистрации бота", e);
        }
    }
}
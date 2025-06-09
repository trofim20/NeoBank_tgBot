package org.example.bot.service;

import lombok.RequiredArgsConstructor;
import org.example.bot.config.BotConfig;
import org.example.bot.handler.command.CommandsHandler;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Телеграм бот
 */
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Основной класс Telegram бота, наследующий TelegramLongPollingBot.
 * Обрабатывает входящие обновления и делегирует обработку команд.
 */
public class TelegramBot extends TelegramLongPollingBot {
    private final BotConfig config;
    private final CommandsHandler commandsHandler;

    /**
     * Возвращает имя бота из конфигурации.
     *
     * @return имя бота
     */
    @Override
    public String getBotUsername() {
        return config.name();
    }

    /**
     * Возвращает токен бота из конфигурации.
     *
     * @return токен бота
     */
    @Override
    public String getBotToken() {
        return config.token();
    }

    /**
     * Обрабатывает входящие обновления от Telegram API.
     *
     * @param update объект Update с данными от Telegram
     */
    @Override
    public void onUpdateReceived(Update update) {
        try {
            BotApiMethod<?> response = commandsHandler.handleCommands(update);
            if (response != null) {
                execute(response);
            }
        } catch (Exception e) {
            log.error("Error processing update", e);
        }
    }
}

package org.example.bot.handler;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

/**
 * Обработчик команд /start и /help
 */
@Service
public class HelpHandler {
    private static final String HELP_MESSAGE = """
        Доступные команды:
        /start - Начать работу
        /auth - Авторизация
        /help - Справка
        /accounts - Получени счетов
        /chatId - Показать ваш chatId""";

    public SendMessage handle(Long chatId) {
        return new SendMessage(chatId.toString(), HELP_MESSAGE);
    }
}
package org.example.bot.service;

import lombok.RequiredArgsConstructor;
import org.example.bot.config.BotConfig;
import org.example.bot.handler.CommandsHandler;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Телеграм бот
 */
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBot extends TelegramLongPollingBot {
    private final BotConfig config;
    private final CommandsHandler commandsHandler;
    private final AuthService authService;

    @Override
    public String getBotUsername() {
        return config.name();
    }

    @Override
    public String getBotToken() {
        return config.token();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update);
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            }
        } catch (Exception e) {
            log.error("Error processing update", e);
        }
    }

    private void handleTextMessage(Update update) throws TelegramApiException {
        SendMessage response = commandsHandler.handleCommands(update);
        if (response != null) {
            execute(response);
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) throws TelegramApiException {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        if (callbackData.startsWith("auth:")) {
            processAuthCallback(callbackData, chatId);
        }
    }

    private void processAuthCallback(String callbackData, Long chatId) throws TelegramApiException {
        String[] parts = callbackData.split(":");
        if (parts.length >= 3) {
            String token = parts[2];
            authService.saveUserToken(chatId, token);

            SendMessage response = new SendMessage();
            response.setChatId(chatId.toString());
            response.setText("✅ Авторизация успешно завершена!");
            execute(response);
        } else {
            SendMessage errorResponse = new SendMessage();
            errorResponse.setChatId(chatId.toString());
            errorResponse.setText("⚠️ Ошибка авторизации. Попробуйте снова.");
            execute(errorResponse);
        }
    }
}
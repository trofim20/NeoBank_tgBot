package org.example.bot.handler;

import lombok.RequiredArgsConstructor;
import org.example.bot.service.UserService;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

/**
 * Обработчик всех команд
 */

/**
 * Обработчик всех команд
 */
@Service
@RequiredArgsConstructor
public class CommandsHandler {
    private final HelpHandler helpHandler;
    private final AuthHandler authHandler;
    private final AccountsHandler accountsHandler;
    private final StartHandler startHandler;

    public SendMessage handleCommands(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return helpHandler.handle(update.getMessage().getChatId());
        }

        String command = update.getMessage().getText().split(" ")[0];
        Long chatId = update.getMessage().getChatId();
        User user = update.getMessage().getFrom();

        return switch (command) {
            case "/start" -> startHandler.handleStart(chatId, user);
            case "/auth" -> authHandler.handleAuthCommand(chatId, user);
            case "/help" -> helpHandler.handle(chatId);
            case "/chatId" -> new SendMessage(chatId.toString(), "Ваш chatId: " + chatId);
            case "/accounts" -> accountsHandler.handleAccountsCommand(chatId, user);
            default -> helpHandler.handle(chatId);
        };
    }
}
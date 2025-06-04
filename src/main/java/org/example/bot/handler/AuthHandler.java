package org.example.bot.handler;

import lombok.RequiredArgsConstructor;
import org.example.bot.service.AuthService;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;

@Service
@RequiredArgsConstructor
public class AuthHandler {
    private final AuthService authService;

    public SendMessage handleAuthCommand(Long chatId, User user) {
        AuthService.AuthResponse response = authService.handleAuthRequest(chatId, user);

        if (response.error() != null) {
            return new SendMessage(chatId.toString(), response.error());
        }

        return new SendMessage(chatId.toString(),
                "Для авторизации перейдите по ссылке:\n" + response.authUrl());
    }

    private SendMessage createMessage(Long chatId, String text) {
        return new SendMessage(chatId.toString(), text);
    }

}

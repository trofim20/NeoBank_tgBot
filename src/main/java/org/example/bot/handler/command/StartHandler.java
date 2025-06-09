package org.example.bot.handler.command;

import lombok.RequiredArgsConstructor;
import org.example.bot.service.AuthService;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Обработчик команды start.
 */
@Service
@RequiredArgsConstructor
public class StartHandler {
    private final AuthService authService;

    /**
     * Обрабатывает команду start.
     *
     * @param chatId ID чата.
     * @param user данные пользователя.
     * @return Приветственное сообщение с кнопкой авторизации.
     */
    public SendMessage handleStart(Long chatId, User user) {
        AuthService.AuthResponse verificationResponse = authService.verifyBot(chatId, user);

        String baseMessage;
        String authUrl = null;

        if (verificationResponse.error() == null) {
            baseMessage = "\uD83C\uDFE6 Добро пожаловать в NeoBank! ✨\n\n" +
                    "✅ Верификация пройдена успешно!\n\n" +
                    "Нажмите кнопку ниже для авторизации в вашем аккаунте\uD83D\uDC47";
            AuthService.AuthResponse authResponse = authService.handleAuthRequest(chatId, user);
            authUrl = authResponse.authUrl();

        } else if (verificationResponse.error().contains("Бот уже верифицирован")) {
            baseMessage = "\uD83C\uDFE6 Добро пожаловать в NeoBank! ✨\n\n" +
                    "✅ Вы уже верифицированы!\n\n" +
                    "Нажмите кнопку ниже для авторизации\uD83D\uDC47";

            AuthService.AuthResponse authResponse = authService.handleAuthRequest(chatId, user);
            authUrl = authResponse.authUrl();

        } else if (verificationResponse.error().contains("/token")) {
            authService.forceVerifyBot(chatId);
            baseMessage = "\uD83C\uDFE6 Добро пожаловать в NeoBank! ✨\n\n" +
                    "✅ Верификация пройдена успешно!\n\n" +
                    "Нажмите кнопку ниже для авторизации в вашем аккаунте\uD83D\uDC47";

            AuthService.AuthResponse authResponse = authService.handleAuthRequest(chatId, user);
            authUrl = authResponse.authUrl();

        } else {
            baseMessage = verificationResponse.error();
            System.err.println("Ошибка при обработке /start: " + verificationResponse.error());
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(baseMessage);

        if (authUrl != null) {
            message.setReplyMarkup(createAuthKeyboard(authUrl));
        }

        return message;
    }


    /**
     * Создает клавиатуру с кнопкой авторизации.
     *
     * @param authUrl URL для авторизации.
     * @return Клавиатура с кнопкой.
     */
    private InlineKeyboardMarkup createAuthKeyboard(String authUrl) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton authButton = new InlineKeyboardButton();
        authButton.setText("🔑 Авторизоваться в NeoBank");
        authButton.setUrl(authUrl);

        row.add(authButton);
        markup.setKeyboard(List.of(row));

        return markup;
    }
}
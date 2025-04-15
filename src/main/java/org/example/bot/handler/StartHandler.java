package org.example.bot.handler;

import lombok.RequiredArgsConstructor;
import org.example.bot.service.AuthService;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ForceReplyKeyboard;

@Service
@RequiredArgsConstructor
public class StartHandler {
    private final AuthService authService;
    private final HelpHandler helpHandler;

    public SendMessage handleStart(Long chatId, User user) {
        AuthService.AuthResponse response = authService.verifyBot(chatId, user);
        String baseMessage;
        if (response.error() == null) {
            baseMessage = "✅ Бот успешно верифицирован!\n\n" +
                    "Теперь вы можете авторизоваться с помощью команды /auth";
        } else {
            // Проверяем, не содержит ли ошибка "правильную" ссылку
            if (response.error().contains("/token")) {
                authService.forceVerifyBot(chatId); // Добавьте этот метод в AuthService
                baseMessage = "✅ Бот успешно верифицирован!\n\n" +
                        "Теперь вы можете авторизоваться с помощью команды /auth";
            } else {
                baseMessage = "❌ Ошибка верификации бота: " + response.error();
                System.err.println("Ошибка при обработке /start: " + response.error());
            }
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(baseMessage);

        SendMessage helpMessage = helpHandler.handle(chatId);
        message.setReplyMarkup(new ForceReplyKeyboard(true));
        message.setText(message.getText() + "\n\n" + helpMessage.getText());

        return message;
    }
}

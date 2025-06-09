package org.example.bot.handler.deposit;

import lombok.RequiredArgsConstructor;
import org.example.bot.utils.CommonUtilsService;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.telegram.telegrambots.meta.api.objects.User;
import org.example.bot.fiegn.NeoFlexTelegramAPI;
import org.example.bot.service.AuthService;
import org.example.bot.service.UserStateService;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик для закрытия вкладов.
 */
@Service
@RequiredArgsConstructor
public class CloseDepositHandler {
    private final AuthService authService;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;
    private final UserStateService userStateService;
    private final CommonUtilsService commonUtils;

    /**
     * Обрабатывает начальную команду закрытия вклада.
     *
     * @param chatId ID чата.
     * @return Сообщение с запросом ID вклада.
     */
    public SendMessage handleInitialCommand(Long chatId) {
        userStateService.setUserState(chatId, "AWAITING_CLOSE_DEPOSIT");
        return commonUtils.createMessage(chatId, "Введите идентификатор вклада для закрытия");
    }

    /**
     * Обрабатывает ввод ID вклада.
     *
     * @param chatId ID чата.
     * @param depositId ID вклада.
     * @return Сообщение с подтверждением или ошибкой.
     */
    public SendMessage handleCloseCommand(Long chatId, String depositId) {
        try {
            if (depositId.length() != 36) {
                throw new NumberFormatException();
            }
            userStateService.setUserData(chatId, "depositId", depositId);
            userStateService.setUserState(chatId, "AWAITING_DEPOSIT_CONFIRM_CLOSE");
            return createConfirmationMessage(chatId);
        } catch (NumberFormatException e) {
            return commonUtils.createMessage(chatId, "Неверный формат ID. Пример правильного формата:\n" +
                    "32dae4e3-d413-4b70-baae-8d7d3869c8ab");
        }
    }

    /**
     * Обрабатывает подтверждение закрытия вклада.
     *
     * @param chatId ID чата.
     * @param user данные пользователя.
     * @param confirmed флаг подтверждения.
     * @return Результат операции закрытия вклада.
     */
    public SendMessage handleConfirmation(Long chatId, User user, boolean confirmed) {
        if (!confirmed) {
            userStateService.clearUserState(chatId);
            return commonUtils.createMessage(chatId, "❌ Закрытие вклада отменено");
        }

        String token;
        try {
            token = authService.getValidUserToken(chatId, user);
        } catch (Exception e) {
            return commonUtils.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
        }

        try {
            String response = closeDeposit(token, chatId);
            if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
                return commonUtils.createMessage(chatId, "Сервер вернул ошибку. Попробуйте позже");
            }
            return formatCloseDepositResponce(chatId, response);
        } catch (Exception e) {
            e.printStackTrace();
            return commonUtils.createMessage(chatId, "❌ Ошибка при закрытии вклада. Попробуйте позже");
        } finally {
            userStateService.clearUserState(chatId);
        }
    }

    /**
     * Создает сообщение с подтверждением закрытия вклада.
     *
     * @param chatId ID чата.
     * @return Сообщение с подтверждением.
     */
    private SendMessage createConfirmationMessage(Long chatId) {
        String depositId = (String) userStateService.getUserData(chatId, "depositId");

        String messageText = String.format(
                "Подтвердите закрытие вклада:\n\n" +
                        "▸ ID вклада: %s\n\n" +
                        "Подтверждаете?",
                depositId
        );

        SendMessage message = commonUtils.createMessage(chatId, messageText);
        message.setReplyMarkup(createConfirmationKeyboard());
        return message;
    }

    /**
     * Создает клавиатуру для подтверждения.
     *
     * @return Клавиатура с кнопками подтверждения.
     */
    private InlineKeyboardMarkup createConfirmationKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createButton("✅ Да", "confirm_yes"));
        row.add(createButton("❌ Нет", "confirm_no"));

        rows.add(row);
        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Форматирует ответ о закрытии вклада.
     *
     * @param chatId ID чата.
     * @param jsonResponse ответ от API.
     * @return Форматированное сообщение.
     */
    private SendMessage formatCloseDepositResponce(Long chatId, String jsonResponse) {
        try {
            JSONObject response = new JSONObject(jsonResponse);
            StringBuilder sb = new StringBuilder();

            sb.append("💰 Вклад успешно закрыт!\n\n");

            sb.append("┌──────────────────────────┐\n");
            sb.append("│   Основные параметры   │\n");
            sb.append("├──────────────────────────┤\n");
            sb.append("│ • Номер: ").append(response.getString("depositNumber")).append("\n");
            sb.append("│ • Сумма: ").append(String.format("%,d", response.getInt("amount")))
                    .append(" ").append(commonUtils.getCurrencySymbol(response.getInt("currencyNumber"))).append("\n");
            sb.append("│ • Срок: ").append(response.getInt("period")).append(" мес.\n");
            sb.append("│ • Ставка: ").append(response.getDouble("depositRate")).append("%\n");
            sb.append("│ • Валюта: ").append(commonUtils.getCurrencyName(response.getInt("currencyNumber"))).append("\n");
            sb.append("│ • Дата открытия: ").append(response.getString("startDepositDate")).append("\n");
            sb.append("│ • Дата закрытия: ").append(response.getString("endDepositDate")).append("\n");
            sb.append("└──────────────────────────┘\n\n");

            sb.append("📋 Детали вклада\n");
            sb.append("▸ Имя продукта:").append(response.getString("depositName")).append("\n");
            sb.append("▸ ID продукта: ").append(response.getString("depositProductId")).append("\n");
            sb.append("▸ Статус: ").append(commonUtils.formatStatus(response.getString("depositStatus"))).append("\n\n");

            SendMessage message = new SendMessage(chatId.toString(), sb.toString());
            message.setParseMode("Markdown");
            return message;

        } catch (Exception e) {
            System.err.println("Error formatting deposit response: " + e.getMessage());
            e.printStackTrace();
            return new SendMessage(chatId.toString(),
                    "⚠ Вклад закрыт!\n\n" +
                            "Некоторые данные могут отображаться некорректно.\n" +
                            "Полный ответ сервера:\n" +
                            "`" + jsonResponse + "`");
        }
    }

    /**
     * Закрывает вклад через API.
     *
     * @param token токен авторизации.
     * @param chatId ID чата.
     * @return Ответ от API.
     */
    private String closeDeposit(String token, Long chatId) {
        String authToken = token.startsWith("Bearer ") ? token : "Bearer " + token;
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("depositId", userStateService.getUserData(chatId, "depositId"));

        return neoFlexTelegramAPI.closeDeposit(authToken, requestBody);
    }

    /**
     * Создает кнопку для клавиатуры.
     *
     * @param text текст кнопки.
     * @param callbackData данные callback.
     * @return Кнопка.
     */
    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
}
package org.example.bot.handler.credit;

import lombok.RequiredArgsConstructor;
import org.example.bot.fiegn.NeoFlexTelegramAPI;
import org.example.bot.service.AuthService;
import org.example.bot.utils.CommonUtilsService;
import org.example.bot.service.UserStateService;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик для погашения кредитов.
 */
@Service
@RequiredArgsConstructor
public class CloseCreditHandler {
    private final AuthService authService;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;
    private final UserStateService userStateService;
    private final CommonUtilsService commonUtils;

    /**
     * Обрабатывает начальную команду погашения кредита.
     *
     * @param chatId ID чата.
     * @return Сообщение с запросом ID кредита.
     */
    public SendMessage handleInitialCommand(Long chatId) {
        userStateService.setUserState(chatId, "AWAITING_CREDITS_CLOSE");
        return commonUtils.createMessage(chatId, "Введите идентификатор кредита для закрытия");
    }

    /**
     * Обрабатывает ввод ID кредита.
     *
     * @param chatId ID чата.
     * @param creditId ID кредита.
     * @return Сообщение с запросом суммы погашения.
     */
    public SendMessage handleAmountForCloseCredit(Long chatId, String creditId) {
        try {
            if (creditId.length() != 36) {
                throw new NumberFormatException();
            }
            userStateService.setUserData(chatId, "creditId", creditId);
            userStateService.setUserState(chatId, "AWAITING_CREDITS_AMOUNT");
            return commonUtils.createMessage(chatId, "💰 Введите сумму для погашения кредита");
        } catch (NumberFormatException e) {
            return commonUtils.createMessage(chatId, "Неверный формат ID. Пример правильного формата:\n" +
                    "32dae4e3-d413-4b70-baae-8d7d3869c8ab");
        }
    }

    /**
     * Обрабатывает ввод суммы погашения.
     *
     * @param chatId ID чата.
     * @param actionAmount сумма погашения.
     * @return Сообщение с подтверждением или ошибкой.
     */
    public SendMessage handleCloseCredit(Long chatId, String actionAmount) {
        try {
            int amount = Integer.parseInt(actionAmount);

            if (actionAmount.length() > 8) {
                return commonUtils.createMessage(chatId,"Сумма слишком большая, попробуйте заного");
            }

            if (amount <= 0) {
                return commonUtils.createMessage(chatId, "Сумма должна быть больше нуля");
            }
            userStateService.setUserData(chatId, "actionAmount", amount);
            userStateService.setUserState(chatId, "AWAITING_CREDIT_CLOSE_CONFIRM");
            return createConfirmationMessage(chatId);
        } catch (Exception e) {
            return commonUtils.handleApiError(chatId, e);
        }
    }

    /**
     * Обрабатывает подтверждение погашения кредита.
     *
     * @param chatId ID чата.
     * @param user данные пользователя.
     * @param confirmed флаг подтверждения.
     * @return Результат операции погашения.
     */
    public SendMessage handleConfirmation(Long chatId, User user, boolean confirmed) {
        if (!confirmed) {
            userStateService.clearUserState(chatId);
            return commonUtils.createMessage(chatId, "Погашение кредита отменено");
        }

        String token;
        try {
            token = authService.getValidUserToken(chatId, user);
        } catch (Exception e) {
            return commonUtils.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
        }

        try {
            String response = closeCredit(token, chatId);
            if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
                return commonUtils.createMessage(chatId, "Сервер вернул ошибку. Попробуйте позже");
            }

            return formatCloseCreditResponse(chatId, response);
        } catch (Exception e) {
            return commonUtils.handleApiError(chatId, e);
        } finally {
            userStateService.clearUserState(chatId);
        }
    }

    /**
     * Создает сообщение с подтверждением погашения.
     *
     * @param chatId ID чата.
     * @return Сообщение с деталями и кнопками подтверждения.
     */
    private SendMessage createConfirmationMessage(Long chatId) {
        String creditId = (String) userStateService.getUserData(chatId, "creditId");
        int amount = (int) userStateService.getUserData(chatId, "actionAmount");

        String messageText = String.format(
                "Подтвердите погашение кредита:\n\n" +
                        "▸ ID кредита: %s\n" +
                        "▸ Сумма погашения: %,d %s\n\n" +
                        "Подтверждаете?",
                creditId,
                amount,
                commonUtils.getCurrencySymbol(643)
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
     * Погашает кредит через API.
     *
     * @param token токен авторизации.
     * @param chatId ID чата.
     * @return Ответ от API.
     */
    private String closeCredit(String token, Long chatId) {
        String authToken = token.startsWith("Bearer ") ? token : "Bearer " + token;
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("creditId", userStateService.getUserData(chatId, "creditId"));
        requestBody.put("actionAmount", userStateService.getUserData(chatId, "actionAmount"));
        requestBody.put("currencyNumber", 643);
        return neoFlexTelegramAPI.closeCredit(authToken, requestBody);
    }


    /**
     * Форматирует ответ о погашении кредита.
     *
     * @param chatId ID чата.
     * @param jsonResponse ответ от API.
     * @return Форматированное сообщение.
     */
    private SendMessage formatCloseCreditResponse(Long chatId, String jsonResponse) {
        try {
            JSONObject response = new JSONObject(jsonResponse);
            StringBuilder sb = new StringBuilder();

            sb.append("✅ Кредит успешно погашен!\n\n");

            sb.append("┌──────────────────────────┐\n");
            sb.append("│   Основные параметры   │\n");
            sb.append("├──────────────────────────┤\n");
            sb.append("│ • Номер: `").append(response.getString("creditNumber")).append("`\n");
            sb.append("│ • Сумма: ").append(String.format("%,d", response.getInt("amount"))).append("\n");
            sb.append("│ • Срок: ").append(response.getInt("period")).append(" мес.\n");
            sb.append("│ • Ставка: ").append(response.getDouble("rate")).append("%\n");
            sb.append("│ • Дата открытия: ").append(response.getString("startCreditDate")).append("\n");
            sb.append("│ • Дата закрытия: ").append(response.getString("endCreditDate")).append("\n");
            sb.append("└──────────────────────────┘\n\n");
            sb.append("📋 Детали операции\n");

            sb.append("▸ Имя продукта: ").append(response.getString("creditName")).append("\n");
            sb.append("▸ Сумма погашения: ").append(response.get("actionAmount")).append("\n");
            sb.append("\n💡 ").append(response.getString("message")).append("\n");

            return createMarkdownMessage(chatId, sb.toString());
        } catch (Exception e) {
            System.err.println("Error formatting credit response: " + e.getMessage());
            return commonUtils.createMessage(chatId,
                    "⚠ Кредит погашен!\n\n" +
                            "Некоторые данные могут отображаться некорректно.\n" +
                            "Полный ответ сервера:\n" +
                            "`" + jsonResponse + "`");
        }
    }

    /**
     * Создает сообщение с разметкой Markdown.
     *
     * @param chatId ID чата.
     * @param text текст сообщения.
     * @return Сообщение с разметкой.
     */
    private SendMessage createMarkdownMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        return message;
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
package org.example.bot.handler.account;

import lombok.RequiredArgsConstructor;
import org.example.bot.fiegn.NeoFlexTelegramAPI;
import org.example.bot.handler.exception.AmountValidationException;
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
 * Обработчик для создания новых счетов.
 */
@Service
@RequiredArgsConstructor
public class NewAccountHandler {
    private static final String CURRENCY_CHOICE_TEXT = "💱 Выберите валюту счета:";
    private static final String INVALID_CURRENCY_MSG = "❌ Неподдерживаемый код валюты. Выберите валюту из предложенных.";

    private final AuthService authService;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;
    private final UserStateService userStateService;
    private final CommonUtilsService commonUtils;

    /**
     * Обрабатывает начальную команду создания счета.
     *
     * @param chatId ID чата.
     * @return Сообщение с запросом суммы для нового счета.
     */
    public SendMessage handleInitialCommand(Long chatId) {
        userStateService.setUserState(chatId, "AWAITING_ACCOUNT_AMOUNT");
        return commonUtils.createMessage(chatId, "💰 Введите сумму для нового счета (например: 1000)");
    }

    /**
     * Обрабатывает ввод суммы для нового счета.
     *
     * @param chatId ID чата.
     * @param amountInput введенная сумма.
     * @return Сообщение с выбором валюты или ошибкой.
     */
    public SendMessage handleAmountInput(Long chatId, String amountInput) {
        try {
            double amount = commonUtils.validateAndParseAmount(amountInput);
            userStateService.setUserData(chatId, "amount", amount);
            userStateService.setUserState(chatId, "AWAITING_CURRENCY");

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(CURRENCY_CHOICE_TEXT);
            message.setReplyMarkup(createCurrencyKeyboard());

            return message;
        } catch (NumberFormatException e) {
            return commonUtils.createMessage(chatId,
                    "Неверный формат суммы. Введите число в формате:\n" +
                            "• 123\n" +
                            "• 123.12\n" +
                            "• 123,12\n" +
                            "Сумма должна быть положительной");
        } catch (AmountValidationException e) {
            return commonUtils.createMessage(chatId, e.getMessage());
        }
    }

    /**
     * Создает клавиатуру для выбора валюты.
     *
     * @return Клавиатура с вариантами валют.
     */
    public InlineKeyboardMarkup createCurrencyKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createCurrencyButton("Доллар $", "840"));
        row1.add(createCurrencyButton("Евро €", "978"));

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createCurrencyButton("Юань ¥", "156"));
        row2.add(createCurrencyButton("Лира ₺", "949"));

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createCurrencyButton("Рубль ₽", "643"));

        List<InlineKeyboardButton> cancelRow = new ArrayList<>();
        cancelRow.add(createCurrencyButton("Отмена", "cancel"));

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        rows.add(cancelRow);

        markup.setKeyboard(rows);
        return markup;
    }


    private InlineKeyboardButton createCurrencyButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    /**
     * Создает сообщение с подтверждением создания счета.
     *
     * @param chatId ID чата.
     * @return Сообщение с деталями счета и кнопками подтверждения.
     */
    public SendMessage handleCurrencyInput(Long chatId, String currencyCode) {
        try {
            if ("cancel".equals(currencyCode)) {
                userStateService.clearUserState(chatId);
                return commonUtils.createMessage(chatId, "Создание счета отменено");
            }

            int currency = Integer.parseInt(currencyCode);

            if (!isValidCurrency(currency)) {
                return createMessageWithKeyboard(chatId, INVALID_CURRENCY_MSG, createCurrencyKeyboard());
            }

            userStateService.setUserData(chatId, "currency", currency);
            userStateService.setUserState(chatId, "AWAITING_ACCOUNT_CONFIRM");
            return createConfirmationMessage(chatId);
        } catch (NumberFormatException e) {
            return createMessageWithKeyboard(chatId,
                    "Неверный формат кода валюты. Выберите валюту из предложенных.",
                    createCurrencyKeyboard());
        }
    }

    /**
     * Создает сообщение с подтверждением создания счета.
     *
     * @param chatId ID чата.
     * @return Сообщение с деталями счета и кнопками подтверждения.
     */
    public SendMessage createConfirmationMessage(Long chatId) {
        double amount = (double) userStateService.getUserData(chatId, "amount");
        int currency = (int) userStateService.getUserData(chatId, "currency");

        String messageText = String.format(
                "Подтвердите создание счета:\n\n" +
                        "Сумма: %s %s\n" +
                        "Валюта: %s\n\n" +
                        "Подтверждаете?",
                commonUtils.formatAmount(amount),
                commonUtils.getCurrencySymbol(currency),
                commonUtils.getCurrencyName(currency)
        );

        SendMessage message = commonUtils.createMessage(chatId, messageText);
        message.setReplyMarkup(createConfirmationKeyboard());
        return message;
    }

    /**
     * Обрабатывает подтверждение создания счета.
     *
     * @param chatId ID чата.
     * @param user данные пользователя.
     * @param input ввод пользователя.
     * @return Результат операции создания счета.
     */
    public SendMessage handleConfirmation(Long chatId, User user, String input) {
        if (input.equals("back")) {
            userStateService.setUserState(chatId, "AWAITING_ACCOUNT_AMOUNT");
            return commonUtils.createMessage(chatId, "💰 Введите сумму для нового счета (например: 1000)");
        }

        if (input.equals("confirm_no")) {
            userStateService.clearUserState(chatId);
            return commonUtils.createMessage(chatId, "Создание счета отменено");
        }

        try {

            String token;
            try {
                token = authService.getValidUserToken(chatId, user);
            } catch (Exception e) {
                return commonUtils.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
            }

            double amount = (double) userStateService.getUserData(chatId, "amount");
            int currency = (int) userStateService.getUserData(chatId, "currency");
            String response = createAccount(token, currency, amount);

            if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
                return commonUtils.createMessage(chatId, "Сервер вернул ошибку. Попробуйте позже");
            }

            return formatAccountResponse(chatId, response);
        } catch (Exception e) {
            return commonUtils.handleApiError(chatId, e);
        } finally {
            userStateService.clearUserState(chatId);
        }
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
        row.add(createButton("↩ Назад", "back"));

        rows.add(row);
        markup.setKeyboard(rows);
        return markup;
    }


    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    /**
     * Создает новый счет через API.
     *
     * @param token токен авторизации.
     * @param currencyCode код валюты.
     * @param amount сумма.
     * @return Ответ от API.
     * @throws Exception если возникает ошибка.
     */
    private String createAccount(String token, int currencyCode, double amount) throws Exception {
        String authToken = token.startsWith("Bearer ") ? token : "Bearer " + token;
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("amount", amount);
        requestBody.put("currencyNumber", currencyCode);

        return neoFlexTelegramAPI.createAccount(authToken, requestBody);
    }

    /**
     * Создает сообщение с клавиатурой.
     *
     * @param chatId ID чата.
     * @param text текст сообщения.
     * @param keyboard клавиатура.
     * @return Сообщение с клавиатурой.
     */
    private SendMessage createMessageWithKeyboard(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setReplyMarkup(keyboard);
        return message;
    }

    /**
     * Проверяет валидность кода валюты.
     *
     * @param currencyCode код валюты.
     * @return true если валиден, иначе false.
     */
    private boolean isValidCurrency(int currencyCode) {
        return currencyCode == 840 || currencyCode == 978 ||
                currencyCode == 156 || currencyCode == 949 || currencyCode == 643;
    }


    /**
     * Форматирует ответ о создании счета.
     *
     * @param chatId ID чата.
     * @param jsonResponse ответ от API.
     * @return Форматированное сообщение.
     */
    private SendMessage formatAccountResponse(Long chatId, String jsonResponse) {
        try {
            JSONObject response = new JSONObject(jsonResponse);
            StringBuilder sb = new StringBuilder();

            sb.append("💳 Счет успешно создан!\n\n");

            sb.append("┌──────────────────────────┐\n");
            sb.append("│   Основные параметры     │\n");
            sb.append("├──────────────────────────┤\n");
            sb.append("│ • Номер: `").append(response.getString("accountNumber")).append("`\n");
            sb.append("│ • Баланс: ").append(commonUtils.formatAmount(response.getDouble("amount")))
                    .append(" ").append(commonUtils.getCurrencySymbol(response.getInt("currencyNumber"))).append("\n");
            sb.append("│ • Валюта: ").append(commonUtils.getCurrencyName(response.getInt("currencyNumber"))).append("\n");
            sb.append("│ • Тип: ").append(response.getString("accountType")).append("\n");
            sb.append("│ • Статус: ").append(commonUtils.formatStatus(response.getString("accountStatus"))).append("\n");
            sb.append("│ • Дата: ").append(response.getString("startDate")).append("\n");
            sb.append("└──────────────────────────┘\n\n");

            SendMessage message = new SendMessage(chatId.toString(), sb.toString());
            message.setParseMode("Markdown");
            return message;
        } catch (Exception e) {
            System.err.println("Error formatting account response: " + e.getMessage());
            return new SendMessage(chatId.toString(),
                    "⚠ *Счет создан!*\n\n" +
                            "Некоторые данные могут отображаться некорректно.\n" +
                            "Полный ответ сервера:\n" +
                            jsonResponse);
        }
    }
}
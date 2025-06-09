package org.example.bot.handler.account;

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
 * Обработчик для закрытия счетов.
 */
@Service
@RequiredArgsConstructor
public class CloseAccountHandler {
    private static final String CURRENCY_CHOICE_TEXT = "💱 Выберите валюту закрываемого счета:";
    private static final String INVALID_CURRENCY_MSG = "Неподдерживаемый код валюты. Выберите валюту из предложенных.";

    private final AuthService authService;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;
    private final UserStateService userStateService;
    private final CommonUtilsService commonUtils;

    /**
     * Обрабатывает начальную команду закрытия счета.
     *
     * @param chatId ID чата.
     * @return Сообщение с запросом ID счета.
     */
    public SendMessage handleInitialCommand(Long chatId) {
        userStateService.setUserState(chatId, "AWAITING_ACCOUNT_CLOSE");
        return commonUtils.createMessage(chatId, "Введите идентификатор счета для его закрытия");
    }

    /**
     * Обрабатывает ввод ID счета.
     *
     * @param chatId ID чата.
     * @param accountId ID счета.
     * @return Сообщение с выбором валюты или ошибкой.
     */
    public SendMessage handleAccountIdInput(Long chatId, String accountId) {
        try {
            if (accountId.length() != 36) {
                throw new NumberFormatException();
            }
            userStateService.setUserData(chatId, "accountId", accountId);
            userStateService.setUserState(chatId, "AWAITING_ACCOUNT_CURRENCY");

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(CURRENCY_CHOICE_TEXT);
            message.setReplyMarkup(createCurrencyKeyboard());

            return message;
        } catch (NumberFormatException e) {
            return commonUtils.createMessage(chatId, "Неверный формат ID. Пример правильного формата:\n" +
                    "32dae4e3-d413-4b70-baae-8d7d3869c8ab");
        }
    }

    /**
     * Создает сообщение с подтверждением закрытия счета.
     *
     * @param chatId ID чата.
     * @return Сообщение с деталями и кнопками подтверждения.
     */
    public SendMessage createConfirmationMessage(Long chatId) {
        String accountId = (String) userStateService.getUserData(chatId, "accountId");
        String messageText = "Вы действительно хотите закрыть счет " + accountId + "?\n\nПодтвердите действие";

        SendMessage message = commonUtils.createMessage(chatId, messageText);
        message.setReplyMarkup(commonUtils.createConfirmationKeyboard());
        return message;
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
        row1.add(createCurrencyButton("USD (Доллар)", "840"));
        row1.add(createCurrencyButton("EUR (Евро)", "978"));

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createCurrencyButton("CNY (Юань)", "156"));
        row2.add(createCurrencyButton("TRY (Лира)", "949"));

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createCurrencyButton("RUB (Рубль)", "643"));

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
     * Обрабатывает выбор валюты.
     *
     * @param chatId ID чата.
     * @param currencyCode код валюты.
     * @return Сообщение с подтверждением или ошибкой.
     */
    public SendMessage handleCurrencyInput(Long chatId, String currencyCode) {
        try {
            if (currencyCode == null || currencyCode.isEmpty()) {
                return commonUtils.createMessage(chatId, "Не получен код валюты");
            }

            if ("cancel".equals(currencyCode)) {
                userStateService.clearUserState(chatId);
                return commonUtils.createMessage(chatId, "Закрытие счета отменено");
            }

            int currency = Integer.parseInt(currencyCode);

            if (!isValidCurrency(currency)) {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText(INVALID_CURRENCY_MSG);
                message.setReplyMarkup(createCurrencyKeyboard());
                return message;
            }

            userStateService.setUserData(chatId, "currency", currency);
            userStateService.setUserState(chatId, "AWAITING_ACCOUNT_CONFIRM");
            return createConfirmationMessage(chatId);
        } catch (NumberFormatException e) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("Неверный формат кода валюты. Выберите валюту из предложенных.");
            message.setReplyMarkup(createCurrencyKeyboard());
            return message;
        }
    }

    /**
     * Обрабатывает подтверждение закрытия счета.
     *
     * @param chatId ID чата.
     * @param user данные пользователя.
     * @param confirmed флаг подтверждения.
     * @return Результат операции закрытия счета.
     */
    public SendMessage handleConfirmation(Long chatId, User user, boolean confirmed) {
        if (!confirmed) {
            userStateService.clearUserState(chatId);
            return commonUtils.createMessage(chatId, "Закрытие счета отменено");
        }

        String token;
        try {
            token = authService.getValidUserToken(chatId, user);
        } catch (Exception e) {
            return commonUtils.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
        }

        try {
            String accountId = (String) userStateService.getUserData(chatId, "accountId");
            int currency = (int) userStateService.getUserData(chatId, "currency");
            if (accountId == null || accountId.isEmpty()) {
                return commonUtils.createMessage(chatId, "Не найден идентификатор счета. Начните заново.");
            }

            String response = closeAccount(token, accountId, currency);

            if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
                return commonUtils.createMessage(chatId, "Сервер вернул ошибку. Попробуйте позже");
            }

            if (response.isEmpty()) {
                return commonUtils.createMessage(chatId, "Пустой ответ от сервера");
            }

            return formatCloseAccountResponse(chatId, response);
        } catch (Exception e) {
            return commonUtils.handleApiError(chatId, e);
        } finally {
            userStateService.clearUserState(chatId);
        }
    }

    /**
     * Закрывает счет через API.
     *
     * @param token токен авторизации.
     * @param accountId ID счета.
     * @param currencyCode код валюты.
     * @return Ответ от API.
     */
    private String closeAccount(String token, String accountId, int currencyCode) {
        String authToken = token.startsWith("Bearer ") ? token : "Bearer " + token;
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("accountId", accountId);
        requestBody.put("currencyNumber", currencyCode);
        return neoFlexTelegramAPI.closeAccount(authToken, requestBody);
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
     * Форматирует ответ о закрытии счета.
     *
     * @param chatId ID чата.
     * @param jsonResponse ответ от API.
     * @return Форматированное сообщение.
     */
    private SendMessage formatCloseAccountResponse(Long chatId, String jsonResponse) {
        try {
            JSONObject response = new JSONObject(jsonResponse);
            StringBuilder sb = new StringBuilder();

            sb.append("✅ Счет успешно закрыт!\n\n");

            sb.append("┌──────────────────────────┐\n");
            sb.append("│   Основные параметры     │\n");
            sb.append("├──────────────────────────┤\n");
            sb.append("│ • Идентификатор счета: `").append(response.getString("id")).append("`\n");
            sb.append("│ • Сумма: ").append(commonUtils.formatAmount(response.getDouble("amount")))
                    .append(" ").append(commonUtils.getCurrencySymbol(response.getInt("currencyNumber"))).append("\n");
            sb.append("│ • Валюта: ").append(commonUtils.getCurrencyName(response.getInt("currencyNumber"))).append("\n");
            sb.append("│ • Статус: ").append(commonUtils.formatStatus(response.getString("accountStatus"))).append("\n");
            sb.append("│ • Дата закрытия: ").append(response.getString("endDate")).append("\n");
            sb.append("└──────────────────────────┘\n\n");

            if (response.has("message") && !response.isNull("message")) {
                sb.append("💡 ").append(response.getString("message")).append("\n");
            }

            SendMessage message = new SendMessage(chatId.toString(), sb.toString());
            message.setParseMode("Markdown");
            return message;
        } catch (Exception e) {
            System.err.println("Error formatting account response: " + e.getMessage());
            return new SendMessage(chatId.toString(),
                    "⚠ Счет закрыт!\n\n" +
                            "Некоторые данные могут отображаться некорректно.\n" +
                            "Полный ответ сервера:\n" +
                            jsonResponse);
        }
    }
}

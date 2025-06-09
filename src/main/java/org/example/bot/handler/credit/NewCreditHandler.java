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
 * Обработчик для создания новых кредитов.
 */
@Service
@RequiredArgsConstructor
public class NewCreditHandler {
    private static final Map<String, Map<String, CreditOption>> CREDIT_OPTIONS = Map.of(
            "Ипотека", Map.of(
                    "12 месяцев: 5%", new CreditOption("2", 5, 12),
                    "60 месяцев: 12%", new CreditOption("2", 12, 60),
                    "120 месяцев: 11%", new CreditOption("2", 11, 120),
                    "180 месяцев: 10%", new CreditOption("2", 10, 180),
                    "240 месяцев: 5%", new CreditOption("2", 5, 240)
            ),
            "Автокредит", Map.of(
                    "12 месяцев: 14%", new CreditOption("3", 14, 12),
                    "24 месяцев: 13.5%", new CreditOption("3", 13.5, 24),
                    "36 месяцев: 13%", new CreditOption("3", 13, 36)
            ),
            "Потребительский", Map.of(
                    "12 месяцев: 15%", new CreditOption("1", 15, 12),
                    "24 месяцев: 15%", new CreditOption("1", 15, 24),
                    "36 месяцев: 15%", new CreditOption("1", 15, 36),
                    "60 месяцев: 15%", new CreditOption("1", 15, 60)
            )
    );

    private final AuthService authService;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;
    private final UserStateService userStateService;
    private final CommonUtilsService commonUtils;

    /**
     * Обрабатывает начальную команду создания кредита.
     *
     * @param chatId ID чата.
     * @return Сообщение с запросом ID счета.
     */
    public SendMessage handleInitialCommand(Long chatId) {
        userStateService.setUserState(chatId, "AWAITING_CREDIT_ACCOUNT");
        return commonUtils.createMessage(chatId, "Введите идентификатор счета для открытия кредита");
    }

    /**
     * Обрабатывает ввод ID счета.
     *
     * @param chatId ID чата.
     * @param accountId ID счета.
     * @return Сообщение с запросом суммы кредита или ошибкой.
     */
    public SendMessage handleAccountInput(Long chatId, String accountId) {
        try {
            if (accountId.length() != 36) {
                throw new NumberFormatException();
            }
            userStateService.setUserData(chatId, "accountId", accountId);
            userStateService.setUserState(chatId, "AWAITING_CREDIT_AMOUNT");
            return commonUtils.createMessage(chatId, "💰 Введите сумму кредита (от 30000)");
        } catch (NumberFormatException e) {
            return commonUtils.createMessage(chatId, "Неверный формат ID. Пример правильного формата:\n" +
                    "32dae4e3-d413-4b70-baae-8d7d3869c8ab");
        }
    }

    /**
     * Обрабатывает ввод суммы кредита.
     *
     * @param chatId ID чата.
     * @param amountInput введенная сумма.
     * @return Сообщение с выбором типа кредита или ошибкой.
     */
    public SendMessage handleAmountInput(Long chatId, String amountInput) {
        try {
            int amount = Integer.parseInt(amountInput);

            if (amountInput.length() > 8) {
                return commonUtils.createMessage(chatId,"Сумма слишком большая, попробуйте заного");
            }

            if (amount <= 0) {
                return commonUtils.createMessage(chatId, "Сумма должна быть больше нуля");
            }
            userStateService.setUserData(chatId, "amount", amount);
            userStateService.setUserState(chatId, "AWAITING_CREDIT_PRODUCT");
            return createMessageWithKeyboard(chatId, "Выберите тип кредита",
                    addBackButton(createProductKeyboard()));
        } catch (NumberFormatException e) {
            return commonUtils.createMessage(chatId,
                    "Неверный формат суммы. Введите число в формате:\n" +
                            "• 123\n" +
                            "• 123.12\n" +
                            "• 123,12\n" +
                            "Сумма должна быть положительной");
        }
    }

    /**
     * Обрабатывает выбор типа кредита.
     *
     * @param chatId ID чата.
     * @param productName название продукта.
     * @return Сообщение с выбором условий кредита или ошибкой.
     */
    public SendMessage handleProductInput(Long chatId, String productName) {
        if (productName.equals("back")) {
            userStateService.setUserState(chatId, "AWAITING_CREDIT_AMOUNT");
            return commonUtils.createMessage(chatId, "💰 Введите сумму кредита (от 30000)");
        }

        if (!CREDIT_OPTIONS.containsKey(productName)) {
            return createMessageWithKeyboard(chatId, "Неверный тип кредита. Выберите из предложенных:",
                    addBackButton(createProductKeyboard()));
        }
        userStateService.setUserData(chatId, "productName", productName);
        userStateService.setUserState(chatId, "AWAITING_CREDIT_TERM");

        Map<String, CreditOption> options = CREDIT_OPTIONS.get(productName);
        return createMessageWithKeyboard(chatId, "Выберите срок и процентную ставку для " + productName,
                addBackButton(createTermKeyboard(options)));
    }

    /**
     * Обрабатывает выбор условий кредита.
     *
     * @param chatId ID чата.
     * @param termKey выбранные условия.
     * @return Сообщение с подтверждением или ошибкой.
     */
    public SendMessage handleTermInput(Long chatId, String termKey) {
        if (termKey.equals("back")) {
            userStateService.setUserState(chatId, "AWAITING_CREDIT_PRODUCT");
            return createMessageWithKeyboard(chatId, "Выберите тип кредита",
                    addBackButton(createProductKeyboard()));
        }

        try {
            String productName = (String) userStateService.getUserData(chatId, "productName");
            Map<String, CreditOption> options = CREDIT_OPTIONS.get(productName);

            if (!options.containsKey(termKey)) {
                return createMessageWithKeyboard(chatId,
                        "❌ Неверный срок. Выберите из предложенных",
                        addBackButton(createTermKeyboard(options)));
            }

            CreditOption option = options.get(termKey);
            userStateService.setUserData(chatId, "creditProductId", option.id());
            userStateService.setUserData(chatId, "rate", option.rate());
            userStateService.setUserData(chatId, "period", option.period());
            userStateService.setUserState(chatId, "AWAITING_CREDIT_CONFIRM");

            return createConfirmationMessage(chatId);
        } catch (Exception e) {
            return commonUtils.createMessage(chatId, "Ошибка при выборе условий кредита");
        }
    }

    /**
     * Обрабатывает подтверждение создания кредита.
     *
     * @param chatId ID чата.
     * @param user данные пользователя.
     * @param input ввод пользователя.
     * @return Результат операции создания кредита.
     */
    public SendMessage handleConfirmation(Long chatId, User user, String input) {
        if (input.equals("back")) {
            userStateService.setUserState(chatId, "AWAITING_CREDIT_TERM");
            String productName = (String) userStateService.getUserData(chatId, "productName");
            Map<String, CreditOption> options = CREDIT_OPTIONS.get(productName);
            return createMessageWithKeyboard(chatId, "Выберите срок и процентную ставку для " + productName,
                    addBackButton(createTermKeyboard(options)));
        }

        if (input.equals("confirm_no")) {
            userStateService.clearUserState(chatId);
            return commonUtils.createMessage(chatId, "Создание кредита отменено");
        }

        String token;
        try {
            token = authService.getValidUserToken(chatId, user);
        } catch (Exception e) {
            return commonUtils.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
        }

        try {
            String response = createCredit(token, chatId);
            if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
                return commonUtils.createMessage(chatId, "Сервер вернул ошибку. Попробуйте позже");
            }
            return formatCreditResponse(chatId, response);
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

    /**
     * Создает сообщение с подтверждением создания кредита.
     *
     * @param chatId ID чата.
     * @return Сообщение с деталями кредита.
     */
    private SendMessage createConfirmationMessage(Long chatId) {
        int amount = (int) userStateService.getUserData(chatId, "amount");
        String productName = (String) userStateService.getUserData(chatId, "productName");
        double rate = (double) userStateService.getUserData(chatId, "rate");
        int period = (int) userStateService.getUserData(chatId, "period");

        String messageText = String.format(
                "Подтвердите создание кредита:\n\n" +
                        "▸ Тип: %s\n" +
                        "▸ Сумма: %s %s\n" +
                        "▸ Ставка: %.1f%%\n" +
                        "▸ Срок: %d мес.\n\n" +
                        "Подтверждаете?",
                productName,
                String.format("%,d", amount),
                commonUtils.getCurrencySymbol(643),
                rate,
                period
        );

        SendMessage message = commonUtils.createMessage(chatId, messageText);
        message.setReplyMarkup(createConfirmationKeyboard());
        return message;
    }

    /**
     * Создает кредит через API.
     *
     * @param token токен авторизации.
     * @param chatId ID чата.
     * @return Ответ от API.
     * @throws Exception если возникает ошибка.
     */
    private String createCredit(String token, Long chatId) throws Exception {
        String authToken = token.startsWith("Bearer ") ? token : "Bearer " + token;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("accountId", userStateService.getUserData(chatId, "accountId"));
        requestBody.put("amount", userStateService.getUserData(chatId, "amount"));
        requestBody.put("creditProductId", userStateService.getUserData(chatId, "creditProductId"));
        requestBody.put("rate", userStateService.getUserData(chatId, "rate"));
        requestBody.put("period", userStateService.getUserData(chatId, "period"));
        requestBody.put("currencyNumber", 643);

        return neoFlexTelegramAPI.createCredit(authToken, requestBody);
    }

    /**
     * Форматирует ответ о создании кредита.
     *
     * @param chatId ID чата.
     * @param jsonResponse ответ от API.
     * @return Форматированное сообщение.
     */
    private SendMessage formatCreditResponse(Long chatId, String jsonResponse) {
        try {
            JSONObject response = new JSONObject(jsonResponse);
            StringBuilder sb = new StringBuilder();

            sb.append("💰 Кредит успешно открыт!\n\n");

            sb.append("┌──────────────────────────┐\n");
            sb.append("│   Основные параметры   │\n");
            sb.append("├──────────────────────────┤\n");
            sb.append("│ • Номер: `").append(response.getString("creditNumber")).append("`\n");
            sb.append("│ • Сумма: ").append(String.format("%,d", response.getInt("amount")))
                    .append(" ").append(commonUtils.getCurrencySymbol(response.getInt("currencyNumber"))).append("\n");
            sb.append("│ • Срок: ").append(response.getInt("period")).append(" мес.\n");
            sb.append("│ • Ставка: ").append(response.getDouble("rate")).append("%\n");
            sb.append("│ • Валюта: ").append(commonUtils.getCurrencyName(response.getInt("currencyNumber"))).append("\n");
            sb.append("│ • Дата: ").append(response.getString("startDate")).append("\n");
            sb.append("└──────────────────────────┘\n\n");

            sb.append("📋 Детали кредита\n");
            sb.append("▸ ID продукта: ").append(response.getString("creditProductId")).append("`\n");
            sb.append("▸ Статус: ").append(commonUtils.formatStatus(response.getString("status"))).append("\n\n");

            SendMessage message = new SendMessage(chatId.toString(), sb.toString());
            message.setParseMode("Markdown");
            return message;
        } catch (Exception e) {
            try {
                return new SendMessage(chatId.toString(),
                        "⚠ Кредит открыт!\n\n" +
                                "Некоторые данные могут отображаться некорректно.\n" +
                                "Полный ответ сервера:\n" +
                                "`" + jsonResponse + "`");
            } catch (Exception ex) {
                return new SendMessage(chatId.toString(),
                        "Кредит успешно открыт, но возникла ошибка при форматировании ответа.");
            }
        }
    }

    /**
     * Создает клавиатуру с вариантами сроков кредита.
     *
     * @param options варианты условий.
     * @return Клавиатура с вариантами.
     */
    private InlineKeyboardMarkup createTermKeyboard(Map<String, CreditOption> options) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        for (String term : options.keySet()) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createButton(term, term));
            buttons.add(row);
        }

        markup.setKeyboard(buttons);
        return markup;
    }

    /**
     * Создает клавиатуру с вариантами типов кредитов.
     *
     * @return Клавиатура с вариантами.
     */
    private InlineKeyboardMarkup createProductKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        for (String product : CREDIT_OPTIONS.keySet()) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createButton(product, product));
            buttons.add(row);
        }

        markup.setKeyboard(buttons);
        return markup;
    }

    /**
     * Создает клавиатуру с вариантами типов кредитов.
     *
     * @return Клавиатура с вариантами.
     */
    private SendMessage createMessageWithKeyboard(Long chatId, String text, InlineKeyboardMarkup inlineKeyboardMarkup) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText(text);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);
        return sendMessage;
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

    /**
     * Добавляет кнопку "Назад" к клавиатуре.
     *
     * @param originalKeyboard исходная клавиатура.
     * @return Новая клавиатура с кнопкой "Назад".
     */
    private InlineKeyboardMarkup addBackButton(InlineKeyboardMarkup originalKeyboard) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>(originalKeyboard.getKeyboard());

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(createButton("↩ Назад", "back"));

        keyboard.add(backRow);

        InlineKeyboardMarkup newKeyboard = new InlineKeyboardMarkup();
        newKeyboard.setKeyboard(keyboard);
        return newKeyboard;
    }

    /**
     * Запись для хранения параметров кредита.
     *
     * @param id ID продукта.
     * @param rate процентная ставка.
     * @param period срок в месяцах.
     */
    public record CreditOption(String id, double rate, int period) {
    }
}
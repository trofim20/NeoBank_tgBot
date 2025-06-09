package org.example.bot.handler.deposit;

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
 * Обработчик для создания новых вкладов.
 */
@Service
@RequiredArgsConstructor
public class NewDepositHandler {
    private static final Map<String, Map<String, DepositOption>> DEPOSIT_OPTIONS = Map.of(
            "Идеальный старт", Map.of(
                    "3 месяца: 7%", new DepositOption("3422b448-2460-4fd2-9183-8000de6f8331", 7, 3, 643),
                    "6 месяцев: 7.5%", new DepositOption("3422b448-2460-4fd2-9183-8000de6f8332", 7.5, 6, 643),
                    "9 месяцев: 7.7%", new DepositOption("3422b448-2460-4fd2-9183-8000de6f8333", 7.7, 9, 643),
                    "12 месяцев: 8%", new DepositOption("3422b448-2460-4fd2-9183-8000de6f8334", 8, 12, 643)
            ),
            "Отличное начало", Map.of(
                    "3 месяца: 8%", new DepositOption("3422b448-2460-4fd2-9183-8000de6f8335", 8, 3, 643),
                    "5 месяцев: 8.5%", new DepositOption("3422b448-2460-4fd2-9183-8000de6f8336", 8.5, 5, 643),
                    "18 месяцев: 9%", new DepositOption("3422b448-2460-4fd2-9183-8000de6f8337", 9, 18, 643)
            ),
            "Блестящий запуск", Map.of(
                    "12 месяцев: 9%", new DepositOption("3422b448-2460-4fd2-9183-8000de6f8338", 9, 12, 643),
                    "24 месяца: 9.5%", new DepositOption("3422b448-2460-4fd2-9183-8000de6f8339", 9.5, 24, 643)
            ),
            "Турецкий старт", Map.of(
                    "1 месяц: 50%", new DepositOption("5358e35c-87aa-41dd-8470-8b848b4e81ba", 50, 1, 840)
            )
    );

    private final AuthService authService;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;
    private final UserStateService userStateService;
    private final CommonUtilsService commonUtils;

    /**
     * Обрабатывает начальную команду создания вклада.
     *
     * @param chatId ID чата.
     * @return Сообщение с запросом ID счета.
     */
    public SendMessage handleInitialCommand(Long chatId) {
        userStateService.setUserState(chatId, "AWAITING_DEPOSIT_ACCOUNT");
        return commonUtils.createMessage(chatId, "Введите идентификатор счета для создания вклада");
    }

    /**
     * Обрабатывает ввод ID счета.
     *
     * @param chatId ID чата.
     * @param accountId ID счета.
     * @return Сообщение с запросом суммы вклада или ошибкой.
     */
    public SendMessage handleAccountInput(Long chatId, String accountId) {
        try {
            if (accountId.length() != 36) {
                throw new NumberFormatException();
            }
            userStateService.setUserData(chatId, "accountId", accountId);
            userStateService.setUserState(chatId, "AWAITING_DEPOSIT_AMOUNT");
            return commonUtils.createMessage(chatId, "💰 Введите сумму вклада (от 30000)");
        } catch (NumberFormatException e) {
            return commonUtils.createMessage(chatId, "Неверный формат ID. Пример правильного формата:\n" +
                    "32dae4e3-d413-4b70-baae-8d7d3869c8ab");
        }
    }

    /**
     * Обрабатывает ввод суммы вклада.
     *
     * @param chatId ID чата.
     * @param amountInput введенная сумма.
     * @return Сообщение с выбором типа вклада или ошибкой.
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
            userStateService.setUserState(chatId, "AWAITING_DEPOSIT_PRODUCT");
            return createMessageWithKeyboard(chatId, "Выберите тип вклада:",
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
     * Обрабатывает выбор типа вклада.
     *
     * @param chatId ID чата.
     * @param productName название продукта.
     * @return Сообщение с выбором условий вклада или ошибкой.
     */
    public SendMessage handleProductInput(Long chatId, String productName) {
        if (productName.equals("back")) {
            userStateService.setUserState(chatId, "AWAITING_DEPOSIT_AMOUNT");
            return commonUtils.createMessage(chatId, "💰 Введите сумму вклада (от 30000)");
        }

        if (!DEPOSIT_OPTIONS.containsKey(productName)) {
            return createMessageWithKeyboard(chatId,
                    "Неверный тип вклада. Выберите из предложенных:",
                    addBackButton(createProductKeyboard()));
        }

        userStateService.setUserData(chatId, "productName", productName);
        userStateService.setUserState(chatId, "AWAITING_DEPOSIT_TERM");

        Map<String, DepositOption> options = DEPOSIT_OPTIONS.get(productName);
        return createMessageWithKeyboard(chatId, "Выберите срок и процентную ставку для " + productName,
                addBackButton(createTermKeyboard(options)));
    }

    /**
     * Обрабатывает выбор условий вклада.
     *
     * @param chatId ID чата.
     * @param termKey выбранные условия.
     * @return Сообщение с запросом автопродления или ошибкой.
     */
    public SendMessage handleTermInput(Long chatId, String termKey) {
        if (termKey.equals("back")) {
            userStateService.setUserState(chatId, "AWAITING_DEPOSIT_PRODUCT");
            return createMessageWithKeyboard(chatId, "Выберите тип вклада",
                    addBackButton(createProductKeyboard()));
        }

        try {
            String productName = (String) userStateService.getUserData(chatId, "productName");
            Map<String, DepositOption> options = DEPOSIT_OPTIONS.get(productName);

            if (!options.containsKey(termKey)) {
                return createMessageWithKeyboard(chatId,
                        "❌ Неверный срок. Выберите из предложенных",
                        addBackButton(createTermKeyboard(options)));
            }

            DepositOption option = options.get(termKey);
            userStateService.setUserData(chatId, "depositProductId", option.id());
            userStateService.setUserData(chatId, "depositRate", option.rate());
            userStateService.setUserData(chatId, "period", option.period());
            userStateService.setUserData(chatId, "currencyNumber", option.currency());
            userStateService.setUserState(chatId, "AWAITING_DEPOSIT_AUTOPROLONGATION");

            return createYesNoKeyboard(chatId, "Включить автоматическое продление?");
        } catch (Exception e) {
            return commonUtils.createMessage(chatId, "❌ Ошибка при выборе условий вклада");
        }
    }

    /**
     * Обрабатывает ввод автопродления.
     *
     * @param chatId ID чата.
     * @param input ввод пользователя.
     * @return Сообщение с подтверждением или ошибкой.
     */
    public SendMessage handleAutoprolongationInput(Long chatId, String input) {
        if (input.equals("back")) {
            String productName = (String) userStateService.getUserData(chatId, "productName");
            userStateService.setUserState(chatId, "AWAITING_DEPOSIT_TERM");
            Map<String, DepositOption> options = DEPOSIT_OPTIONS.get(productName);
            return createMessageWithKeyboard(chatId, "Выберите срок и процентную ставку для " + productName,
                    addBackButton(createTermKeyboard(options)));
        }

        try {
            boolean autoProlongation;
            if (input.equals("yes")) {
                autoProlongation = true;
            } else if (input.equals("no")) {
                autoProlongation = false;
            } else {
                autoProlongation = parseYesNo(input);
            }

            userStateService.setUserData(chatId, "autoProlongation", autoProlongation);
            userStateService.setUserState(chatId, "AWAITING_DEPOSIT_CONFIRM");
            return createConfirmationMessage(chatId);
        } catch (IllegalArgumentException e) {
            return createYesNoKeyboard(chatId,
                    "❌ Пожалуйста, выберите 'да' или 'нет'\n\nВключить автоматическое продление?");
        }
    }

    /**
     * Обрабатывает подтверждение создания вклада.
     *
     * @param chatId ID чата.
     * @param user данные пользователя.
     * @param input ввод пользователя.
     * @return Результат операции создания вклада.
     */
    public SendMessage handleConfirmation(Long chatId, User user, String input) {
        if (input.equals("back")) {
            userStateService.setUserState(chatId, "AWAITING_DEPOSIT_AUTOPROLONGATION");
            return createYesNoKeyboard(chatId, "Включить автоматическое продление?");
        }

        if (input.equals("confirm_no")) {
            userStateService.clearUserState(chatId);
            return commonUtils.createMessage(chatId, "❌ Создание вклада отменено");
        }

        String token;
        try {
            token = authService.getValidUserToken(chatId, user);
        } catch (Exception e) {
            return commonUtils.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
        }

        try {
            String response = createDeposit(token, chatId);
            if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
                return commonUtils.createMessage(chatId, "Сервер вернул ошибку. Попробуйте позже");
            }
            return formatDepositResponse(chatId, response);
        } catch (Exception e) {
            return commonUtils.handleApiError(chatId, e);
        } finally {
            userStateService.clearUserState(chatId);
        }
    }

    /**
     * Создает сообщение с подтверждением создания вклада.
     *
     * @param chatId ID чата.
     * @return Сообщение с деталями вклада.
     */
    private SendMessage createConfirmationMessage(Long chatId) {
        int amount = (int) userStateService.getUserData(chatId, "amount");
        String productName = (String) userStateService.getUserData(chatId, "productName");
        double rate = (double) userStateService.getUserData(chatId, "depositRate");
        int number = (int) userStateService.getUserData(chatId, "currencyNumber");

        int period = (int) userStateService.getUserData(chatId, "period");
        boolean autoProlongation = (boolean) userStateService.getUserData(chatId, "autoProlongation");

        String messageText = String.format(
                "Подтвердите создание вклада:\n\n" +
                        "▸ Тип: %s\n" +
                        "▸ Сумма: %s %s\n" +
                        "▸ Ставка: %.1f%%\n" +
                        "▸ Срок: %d мес.\n" +
                        "▸ Автопродление: %s\n\n" +
                        "Подтверждаете?",
                productName,
                String.format("%,d", amount),
                commonUtils.getCurrencySymbol(number),
                rate,
                period,
                autoProlongation ? "✅ Да" : "❌ Нет"
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
        row.add(createButton("↩ Назад", "back"));

        rows.add(row);
        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Создает клавиатуру с вариантами "Да/Нет".
     *
     * @param chatId ID чата.
     * @param text текст сообщения.
     * @return Сообщение с клавиатурой.
     */
    private SendMessage createYesNoKeyboard(Long chatId, String text) {
        SendMessage message = new SendMessage(chatId.toString(), text);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createButton("✅ Да", "yes"));
        row.add(createButton("❌ Нет", "no"));
        row.add(createButton("↩ Назад", "back"));

        rows.add(row);
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);

        return message;
    }

    /**
     * Создает клавиатуру с вариантами типов вкладов.
     *
     * @return Клавиатура с вариантами.
     */
    private InlineKeyboardMarkup createProductKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (String productName : DEPOSIT_OPTIONS.keySet()) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createButton(productName, productName));
            rows.add(row);
        }

        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Создает клавиатуру с вариантами условий вклада.
     *
     * @param options варианты условий.
     * @return Клавиатура с вариантами.
     */
    private InlineKeyboardMarkup createTermKeyboard(Map<String, DepositOption> options) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (String term : options.keySet()) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createButton(term, term));
            rows.add(row);
        }

        markup.setKeyboard(rows);
        return markup;
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
     * Создает вклад через API.
     *
     * @param token токен авторизации.
     * @param chatId ID чата.
     * @return Ответ от API.
     * @throws Exception если возникает ошибка.
     */
    private String createDeposit(String token, Long chatId) throws Exception {
        String authToken = token.startsWith("Bearer ") ? token : "Bearer " + token;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("accountId", userStateService.getUserData(chatId, "accountId"));
        requestBody.put("startAmount", userStateService.getUserData(chatId, "amount"));
        requestBody.put("depositProductId", userStateService.getUserData(chatId, "depositProductId"));
        requestBody.put("depositRate", userStateService.getUserData(chatId, "depositRate"));
        requestBody.put("period", userStateService.getUserData(chatId, "period"));
        requestBody.put("currencyNumber", userStateService.getUserData(chatId, "currencyNumber"));
        requestBody.put("autoProlongation", userStateService.getUserData(chatId, "autoProlongation"));

        return neoFlexTelegramAPI.createDeposit(authToken, requestBody);
    }

    /**
     * Форматирует ответ о создании вклада.
     *
     * @param chatId ID чата.
     * @param jsonResponse ответ от API.
     * @return Форматированное сообщение.
     */
    private SendMessage formatDepositResponse(Long chatId, String jsonResponse) {
        try {
            JSONObject response = new JSONObject(jsonResponse);
            StringBuilder sb = new StringBuilder();

            sb.append("💰 Вклад успешно открыт!\n\n");

            sb.append("┌──────────────────────────┐\n");
            sb.append("│   Основные параметры   │\n");
            sb.append("├──────────────────────────┤\n");
            sb.append("│ • Номер: ").append(response.getString("depositNumber")).append("\n");
            sb.append("│ • Сумма: ").append(String.format("%,d", response.getInt("startAmount")))
                    .append(" ").append(commonUtils.getCurrencySymbol(response.getInt("currencyNumber"))).append("\n");
            sb.append("│ • Срок: ").append(response.getInt("period")).append(" мес.\n");
            sb.append("│ • Ставка: ").append(response.getDouble("depositRate")).append("%\n");
            sb.append("│ • Валюта: ").append(commonUtils.getCurrencyName(response.getInt("currencyNumber"))).append("\n");
            sb.append("│ • Дата: ").append(response.getString("startDate")).append("\n");
            sb.append("└──────────────────────────┘\n\n");

            sb.append("📋 Детали вклада\n");
            sb.append("▸ ID продукта: ").append(response.getString("depositProductId")).append("\n");
            sb.append("▸ Статус: ").append(commonUtils.formatStatus(response.getString("status"))).append("\n\n");

            SendMessage message = new SendMessage(chatId.toString(), sb.toString());
            message.setParseMode("Markdown");
            return message;
        } catch (Exception e) {
            System.err.println("Error formatting deposit response: " + e.getMessage());
            e.printStackTrace();

            try {
                return new SendMessage(chatId.toString(),
                        "⚠ Вклад создан!\n\n" +
                                "Некоторые данные могут отображаться некорректно.\n" +
                                "Полный ответ сервера:\n" +
                                "`" + jsonResponse + "`");
            } catch (Exception ex) {
                return new SendMessage(chatId.toString(),
                        "Вклад успешно создан, но возникла ошибка при форматировании ответа.");
            }
        }
    }

    /**
     * Парсит ввод "Да/Нет".
     *
     * @param input ввод пользователя.
     * @return true если "Да", false если "Нет".
     * @throws IllegalArgumentException если ввод некорректен.
     */
    private boolean parseYesNo(String input) {
        String lowerInput = input.toLowerCase();
        if (lowerInput.equals("да") || lowerInput.equals("yes") || lowerInput.equals("y") || lowerInput.equals("д"))
            return true;
        if (lowerInput.equals("нет") || lowerInput.equals("no") || lowerInput.equals("n") || lowerInput.equals("н"))
            return false;
        throw new IllegalArgumentException("Некорректный ввод");
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
     * Запись для хранения параметров вклада.
     *
     * @param id ID продукта.
     * @param rate процентная ставка.
     * @param period срок в месяцах.
     * @param currency код валюты.
     */
    private record DepositOption(String id, double rate, int period, int currency) {
    }
}
package org.example.bot.handler.transfer;

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
 * Обработчик команд для выполнения переводов между счетами.
 * Управляет процессом перевода средств через последовательность шагов.
 */
@Service
@RequiredArgsConstructor
public class TransferHandler {
    private final AuthService authService;
    private final UserStateService userStateService;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;
    private final CommonUtilsService commonUtils;

    /**
     * Обрабатывает начальную команду перевода.
     *
     * @param chatId идентификатор чата
     * @return сообщение с запросом идентификатора счета отправителя
     */
    public SendMessage handleInitialCommand(Long chatId) {
        userStateService.setUserState(chatId, "FROM_ACCOUNT_ID");
        return commonUtils.createMessage(chatId, "Введите идентификатор счета с которого будет осуществляться перевод средств");
    }

    /**
     * Обрабатывает ввод идентификатора счета отправителя.
     *
     * @param chatId идентификатор чата
     * @param accountId идентификатор счета отправителя
     * @return сообщение с запросом идентификатора счета получателя или ошибкой
     */
    public SendMessage handleAccountIdInput(Long chatId, String accountId) {
        try {
            if (accountId.length() != 36) {
                throw new NumberFormatException();
            }
            userStateService.setUserData(chatId, "accountId", accountId);
            userStateService.setUserState(chatId, "TO_ACCOUNT_ID");

            return commonUtils.createMessage(chatId, "Введите идентификатор счета куда будут осуществляться перевод средств");
        } catch (NumberFormatException e) {
            return commonUtils.createMessage(chatId, "Неверный формат ID. Пример правильного формата:\n" +
                    "32dae4e3-d413-4b70-baae-8d7d3869c8ab");
        }
    }

    /**
     * Обрабатывает ввод идентификатора счета получателя.
     *
     * @param chatId идентификатор чата
     * @param accountIdTo идентификатор счета получателя
     * @return сообщение с запросом суммы перевода или ошибкой
     */
    public SendMessage handleAccountIdToInput(Long chatId, String accountIdTo) {
        try {
            if (accountIdTo.length() != 36) {
                throw new NumberFormatException();
            }
            userStateService.setUserData(chatId, "accountIdTo", accountIdTo);
            userStateService.setUserState(chatId, "AMOUNT_TRANSFERRED");

            return commonUtils.createMessage(chatId, "Введите сумму для перевода");
        } catch (NumberFormatException e) {
            return commonUtils.createMessage(chatId, "Неверный формат ID. Пример правильного формата:\n" +
                    "32dae4e3-d413-4b70-baae-8d7d3869c8ab");
        }
    }

    /**
     * Обрабатывает ввод суммы перевода.
     *
     * @param chatId идентификатор чата
     * @param amountInput введенная сумма
     * @return сообщение с запросом сообщения для пользователя или ошибкой
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
            userStateService.setUserState(chatId, "AWAITING_MESSAGE");

            return commonUtils.createMessage(chatId, "Введите сообщение для пользователя");

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
     * Обрабатывает ввод сообщения для пользователя.
     *
     * @param chatId идентификатор чата
     * @param messageText текст сообщения
     * @return сообщение с подтверждением перевода
     */
    public SendMessage handleMessage(Long chatId, String messageText) {
        try {
            userStateService.setUserData(chatId, "message", messageText);
            userStateService.setUserState(chatId, "AWAITING_TRANSFER_CONFIRM");
            return createConfirmationMessage(chatId);
        } catch (Exception e) {
            return commonUtils.handleApiError(chatId, e);
        }
    }

    /**
     * Обрабатывает подтверждение перевода.
     *
     * @param chatId идентификатор чата
     * @param user объект пользователя Telegram
     * @param confirmed флаг подтверждения
     * @return сообщение с результатом операции перевода
     */
    public SendMessage handleConfirmation(Long chatId, User user, boolean confirmed) {
        if (!confirmed) {
            userStateService.clearUserState(chatId);
            return commonUtils.createMessage(chatId, "Перевод отменен");
        }

        String token;
        try {
            token = authService.getValidUserToken(chatId, user);
        } catch (Exception e) {
            return commonUtils.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
        }

        try {
            String accountId = (String) userStateService.getUserData(chatId, "accountId");
            String accountIdTo = (String) userStateService.getUserData(chatId, "accountIdTo");
            int amount = (int) userStateService.getUserData(chatId, "amount");
            String message = (String) userStateService.getUserData(chatId, "message");

            if (accountId == null || accountId.isEmpty()) {
                return commonUtils.createMessage(chatId, "❌ Не найден идентификатор счета. Начните заново.");
            }

            String response = transfer(token, accountId, accountIdTo, amount, message);
            if (response == null || response.isEmpty()) {
                return commonUtils.createMessage(chatId, "❌ Пустой ответ от сервера");
            }

            if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
                return commonUtils.createMessage(chatId, "Сервер вернул ошибку. Попробуйте позже");
            }

            return formatTransferResponse(chatId, response);
        } catch (Exception e) {
            e.printStackTrace();
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
        String accountIdFrom = (String) userStateService.getUserData(chatId, "accountId");
        String accountIdTo = (String) userStateService.getUserData(chatId, "accountIdTo");
        int amount = (int) userStateService.getUserData(chatId, "amount");
        String messageText = (String) userStateService.getUserData(chatId, "message");

        String text = String.format(
                "Подтвердите перевод:\n\n" +
                        "▸ Счет отправителя: %s\n" +
                        "▸ Счет получателя: %s\n" +
                        "▸ Сумма: %s %s\n" +
                        "▸ Сообщение: %s\n\n" +
                        "Подтверждаете?",
                accountIdFrom,
                accountIdTo,
                String.format("%,d", amount),
                commonUtils.getCurrencySymbol(643),
                messageText
        );

        SendMessage sendMessage = new SendMessage(chatId.toString(), text);
        sendMessage.setReplyMarkup(createConfirmationKeyboard());
        return sendMessage;
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

    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    /**
     * Выполняет перевод средств через API.
     *
     * @param token токен авторизации
     * @param accountIdFrom идентификатор счета отправителя
     * @param accountIdTo идентификатор счета получателя
     * @param amount сумма перевода
     * @param message сообщение для пользователя
     * @return ответ от API
     */
    private String transfer(String token, String accountIdFrom, String accountIdTo, int amount, String message) {
        String authToken = token.startsWith("Bearer ") ? token : "Bearer " + token;
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("fromAccountId", accountIdFrom);
        requestBody.put("toAccountId", accountIdTo);
        requestBody.put("amount", amount);
        requestBody.put("message", message);

        System.out.println("transfer с параметрами: " + requestBody);
        return neoFlexTelegramAPI.transfer(authToken, requestBody);
    }

    /**
     * Форматирует ответ о совершении перевода.
     *
     * @param chatId ID чата.
     * @param jsonResponse ответ от API.
     * @return Форматированное сообщение.
     */
    private SendMessage formatTransferResponse(Long chatId, String jsonResponse) {
        try {
            JSONObject response = new JSONObject(jsonResponse);
            StringBuilder sb = new StringBuilder();

            sb.append("✅ Перевод успешно выполнен!\n\n");
            sb.append("📋 Детали операции\n");
            sb.append("▸ Статус: ").append(commonUtils.formatStatus(response.getString("status"))).append("\n\n");

            SendMessage message = new SendMessage(chatId.toString(), sb.toString());
            message.setParseMode("Markdown");
            return message;

        } catch (Exception e) {
            System.err.println("Error formatting account response: " + e.getMessage());
            return new SendMessage(chatId.toString(),
                    "⚠ Перевод выполнен!\n\n" +
                            "Некоторые данные могут отображаться некорректно.\n" +
                            "Полный ответ сервера:\n" +
                            "`" + jsonResponse + "`");
        }
    }
}
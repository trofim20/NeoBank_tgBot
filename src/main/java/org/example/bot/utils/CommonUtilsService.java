package org.example.bot.utils;

import lombok.RequiredArgsConstructor;
import org.example.bot.handler.exception.AmountValidationException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Сервис с общими утилитами для работы бота.
 */
@Service
@RequiredArgsConstructor
public class CommonUtilsService {

    /**
     * Возвращает символ валюты по коду.
     *
     * @param currencyCode код валюты
     * @return символ валюты
     */
    public String getCurrencySymbol(int currencyCode) {
        return switch (currencyCode) {
            case 840 -> "$";
            case 978 -> "€";
            case 156 -> "¥";
            case 949 -> "₺";
            case 643 -> "₽";
            default -> "";
        };
    }

    /**
     * Возвращает название валюты по коду.
     *
     * @param currencyCode код валюты
     * @return название валюты
     */
    public String getCurrencyName(int currencyCode) {
        return switch (currencyCode) {
            case 840 -> "USD (Доллар США)";
            case 978 -> "EUR (Евро)";
            case 156 -> "CNY (Юань)";
            case 949 -> "TRY (Турецких лир)";
            case 643 -> "RUB (Рубль)";
            default -> "Код: " + currencyCode;
        };
    }

    /**
     * Форматирует статус для отображения.
     *
     * @param status исходный статус
     * @return отформатированный статус
     */
    public String formatStatus(String status) {
        return "ACTIVE".equals(status) ? "Активен" : status;
    }

    /**
     * Форматирует сумму для отображения.
     *
     * @param amount сумма
     * @return отформатированная строка суммы
     */
    public String formatAmount(double amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }

    /**
     * Создает простое текстовое сообщение.
     *
     * @param chatId идентификатор чата
     * @param text текст сообщения
     * @return SendMessage с заданным текстом
     */
    public SendMessage createMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        return message;
    }

    /**
     * Обрабатывает ошибку API и создает сообщение об ошибке.
     *
     * @param chatId идентификатор чата
     * @param e исключение
     * @return SendMessage с информацией об ошибке
     */
    public SendMessage handleApiError(Long chatId, Exception e) {
        try {
            String errorMessage = e.getMessage();
            int jsonStart = errorMessage.indexOf("{");
            int jsonEnd = errorMessage.lastIndexOf("}") + 1;

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonStr = errorMessage.substring(jsonStart, jsonEnd);
                JSONObject errorJson = new JSONObject(jsonStr);

                StringBuilder sb = new StringBuilder();

                if (errorJson.has("errorTitle")) {
                    sb.append("▸ *").append(errorJson.getString("errorTitle")).append("*\n");
                }

                if (errorJson.has("errorDetail")) {
                    sb.append("\n").append(errorJson.getString("errorDetail")).append("\n");
                } else if (errorJson.has("message")) {
                    sb.append("\n").append(errorJson.getString("message")).append("\n");
                }

                return createMarkdownMessage(chatId, sb.toString());
            }
        } catch (Exception jsonEx) {
            System.err.println("Error parsing error response: " + jsonEx.getMessage());
        }

        return createMessage(chatId, "Произошла ошибка. Попробуйте позже");
    }

    /**
     * Создает сообщение с поддержкой Markdown.
     *
     * @param chatId идентификатор чата
     * @param text текст сообщения
     * @return SendMessage с поддержкой Markdown
     */
    public SendMessage createMarkdownMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        return message;
    }

    /**
     * Проверяет и преобразует введенную сумму.
     *
     * @param amountInput введенная сумма
     * @return преобразованную сумму
     * @throws AmountValidationException при невалидной сумме
     */
    public double validateAndParseAmount(String amountInput) throws AmountValidationException {
        String normalizedInput = amountInput.replace(',', '.');
        if (!normalizedInput.matches("^\\d+(\\.\\d{1,2})?$")) {
            throw new NumberFormatException();
        }

        if (normalizedInput.length() > 8) {
            throw new AmountValidationException("Сумма слишком большая, попробуйте заного");
        }

        double amount = Double.parseDouble(normalizedInput);
        if (amount <= 0) {
            throw new AmountValidationException("Сумма должна быть больше нуля, попробуйте заного");
        }
        return amount;
    }

    /**
     * Создает клавиатуру для подтверждения действия.
     *
     * @return InlineKeyboardMarkup с кнопками подтверждения
     */
    public InlineKeyboardMarkup createConfirmationKeyboard() {
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
     * Создает кнопку встроенной клавиатуры.
     *
     * @param text текст кнопки
     * @param callbackData данные обратного вызова
     * @return InlineKeyboardButton с заданными параметрами
     */
    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
}
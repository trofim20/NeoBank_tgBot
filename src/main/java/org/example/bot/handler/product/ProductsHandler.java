package org.example.bot.handler.product;

import lombok.RequiredArgsConstructor;
import org.example.bot.fiegn.NeoFlexTelegramAPI;
import org.example.bot.service.AuthService;
import org.example.bot.utils.CommonUtilsService;
import org.example.bot.service.UserStateService;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Обработчик команд для работы с банковскими продуктами.
 * Предоставляет информацию о счетах, вкладах и кредитах пользователя.
 */
@Service
@RequiredArgsConstructor
public class ProductsHandler {
    private static final String PRODUCT_TYPE_PROMPT = "📋 Выберите тип банковского продукта:";
    private static final String INVALID_PRODUCT_MSG = "❌ Пожалуйста, выберите тип продукта из предложенных вариантов";
    private static final String NO_PRODUCTS_MSG = "Нет доступных продуктов этого типа";
    private static final String PRODUCTS_TITLE = "🏦 Доступные продукты:\n\n";

    private final AuthService authService;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;
    private final UserStateService userStateService;
    private final CommonUtilsService commonUtilsService;

    /**
     * Обрабатывает начальную команду запроса продуктов.
     *
     * @param chatId идентификатор чата
     * @return сообщение с клавиатурой выбора типа продукта
     */
    public SendMessage handleInitialCommand(Long chatId) {
        userStateService.setUserState(chatId, "AWAITING_PRODUCT_TYPE");

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(PRODUCT_TYPE_PROMPT);
        message.setReplyMarkup(createProductTypeKeyboard());

        return message;
    }




    /**
     * Обрабатывает выбор типа продукта.
     *
     * @param chatId идентификатор чата
     * @param user объект пользователя Telegram
     * @param productType выбранный тип продукта
     * @return сообщение со списком продуктов или ошибкой
     */
    public SendMessage handleProductTypeInput(Long chatId, User user, String productType) {
        String token;
        try {
            token = authService.getValidUserToken(chatId, user);
        } catch (Exception e) {
            return commonUtilsService.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
        }

        try {
            if (productType == null ||
                    !(productType.equals("account") || productType.equals("deposit") || productType.equals("credit"))) {
                return createMessageWithKeyboard(chatId, INVALID_PRODUCT_MSG, createProductTypeKeyboard());
            }

            String response = neoFlexTelegramAPI.getProducts(token, productType);

            if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
                return commonUtilsService.createMessage(chatId, "Сервер вернул ошибку. Попробуйте позже");
            }
            return commonUtilsService.createMessage(chatId, formatProducts(response, productType));
        } catch (Exception e) {
            return commonUtilsService.handleApiError(chatId, e);
        } finally {
            userStateService.clearUserState(chatId);
        }
    }

    private SendMessage createMessageWithKeyboard(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setReplyMarkup(keyboard);
        return message;
    }

    /**
     * Форматирует список продуктов в читаемый вид.
     *
     * @param jsonProducts JSON-строка с продуктами
     * @param productType тип продукта
     * @return отформатированную строку с информацией о продуктах
     */
    private String formatProducts(String jsonProducts, String productType) {
        try {
            JSONObject response = new JSONObject(jsonProducts);
            JSONArray products = response.getJSONObject("products")
                    .getJSONArray(productType + "Products");

            if (products.length() == 0) {
                return NO_PRODUCTS_MSG;
            }

            StringBuilder builder = new StringBuilder(PRODUCTS_TITLE);
            for (int i = 0; i < products.length(); i++) {
                builder.append(formatProduct(i + 1, products.getJSONObject(i), productType));
            }
            return builder.toString();
        } catch (Exception e) {
            return "⚠ Не удалось загрузить информацию о продуктах";
        }
    }

    /**
     * Создает клавиатуру для выбора типа продукта.
     *
     * @return InlineKeyboardMarkup с кнопками выбора типа продукта
     */
    private InlineKeyboardMarkup createProductTypeKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineButton("Счет", "account"));

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createInlineButton("Вклад", "deposit"));

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createInlineButton("Кредит", "credit"));

        List<InlineKeyboardButton> cancelRow = new ArrayList<>();
        cancelRow.add(createInlineButton("❌ Отмена", "cancel"));

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        rows.add(cancelRow);

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
    private InlineKeyboardButton createInlineButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    /**
     * Форматирует информацию об отдельном продукте.
     *
     * @param index порядковый номер продукта
     * @param product JSON-объект с данными продукта
     * @param productType тип продукта
     * @return отформатированную строку с информацией о продукте
     * @throws JSONException при ошибке обработки JSON
     */
    private String formatProduct(int index, JSONObject product, String productType) throws JSONException {
        String statusKey = productType.equals("deposit") ? "depositProductStatus" : "status";

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("📌 ").append(index).append(". ").append(product.getString("name")).append("\n");
        sb.append("┌──────────────────────┐\n");
        sb.append("│   Характеристики   \n");
        sb.append("├──────────────────────┤\n");
        sb.append("│ • Валюта: ").append(commonUtilsService.getCurrencyName(product.getInt("currencyNumber"))).append("\n");
        sb.append("│ • Статус: ").append(formatStatus(product.getString(statusKey))).append("\n");

        if (product.has("rate")) {
            sb.append("│ • Ставка: ").append(product.getDouble("rate")).append("%\n");
        }
        if (product.has("period")) {
            sb.append("│ • Срок: ").append(product.getInt("period")).append(" мес.\n");
        }

        sb.append("└──────────────────────┘\n");
        return sb.toString();
    }

    /**
     * Форматирует статус продукта для отображения.
     *
     * @param status исходный статус
     * @return отформатированный статус
     */
    private String formatStatus(String status) {
        return switch (status) {
            case "ACTIVE" -> "Активен";
            case "INACTIVE" -> "Неактивен";
            default -> status;
        };
    }
}
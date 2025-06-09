package org.example.bot.handler.currencies;

import lombok.RequiredArgsConstructor;
import org.example.bot.fiegn.NeoFlexTelegramAPI;
import org.example.bot.service.AuthService;
import org.example.bot.utils.CommonUtilsService;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;

/**
 * Обработчик для работы с валютами.
 */
@Service
@RequiredArgsConstructor
public class CurrenciesHandler {
    private final AuthService authService;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;
    private final CommonUtilsService commonUtils;

    /**
     * Обрабатывает команду просмотра валют.
     *
     * @param chatId ID чата.
     * @param user данные пользователя.
     * @return Сообщение со списком валют.
     */
    public SendMessage handleCurrenciesCommand(Long chatId, User user) {
        String token;
        try {
            token = authService.getValidUserToken(chatId, user);
        } catch (Exception e) {
            return commonUtils.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
        }

        try {
            try {
                String response = neoFlexTelegramAPI.getCurrencies(token);

                if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
                    return commonUtils.createMessage(chatId, "Сервер вернул ошибку. Попробуйте позже");
                }

                return commonUtils.createMessage(chatId, formatCurrencies(response));
            } catch (Exception e) {
                return commonUtils.createMessage(chatId,
                        "Ошибка при получении списка валют");
            }
        } catch (Exception e) {
            return commonUtils.handleApiError(chatId, e);
        }
    }

    /**
     * Форматирует ответ со списком валют.
     *
     * @param jsonCurrencies JSON со списком валют.
     * @return Отформатированное сообщение.
     */
    private String formatCurrencies(String jsonCurrencies) {
        try {
            JSONArray currencies = new JSONArray(jsonCurrencies);
            return formatCurrenciesArray(currencies);
        } catch (JSONException e) {
            try {
                JSONObject response = new JSONObject(jsonCurrencies);
                if (response.has("currencies")) {
                    return formatCurrenciesArray(response.getJSONArray("currencies"));
                }
                throw new JSONException("Invalid currencies format");
            } catch (JSONException ex) {
                return "⚠ Не удалось загрузить информацию о валютах";
            }
        } catch (Exception e) {
            return "⚠ Не удалось загрузить информацию о валютах";
        }
    }

    /**
     * Форматирует массив валют.
     *
     * @param currencies массив валют.
     * @return Отформатированное сообщение.
     * @throws JSONException если возникает ошибка при обработке JSON.
     */
    private String formatCurrenciesArray(JSONArray currencies) throws JSONException {
        if (currencies.length() == 0) {
            return "🌐 Нет доступных валют";
        }

        StringBuilder sb = new StringBuilder("🌐 Доступные валюты\n\n");

        for (int i = 0; i < currencies.length(); i++) {
            JSONObject currency = currencies.getJSONObject(i);
            sb.append("💱 ").append(currency.getString("currencyName")).append("\n");
            sb.append("┌──────────────────────┐\n");
            sb.append("│   Параметры валюты \n");
            sb.append("├──────────────────────┤\n");
            sb.append("│ • Код: ").append(currency.getString("currencyCode")).append("\n");
            sb.append("│ • Номер: ").append(currency.getString("currencyNumber")).append("\n");
            sb.append("│ • Символ: ").append(commonUtils.getCurrencySymbol(currency.getInt("currencyNumber"))).append("\n");
            sb.append("└──────────────────────┘\n");
        }

        return sb.toString();
    }
}
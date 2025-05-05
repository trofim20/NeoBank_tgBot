package org.example.bot.handler;

import lombok.RequiredArgsConstructor;
import org.example.bot.fiegn.NeoFlexTelegramAPI;
import org.example.bot.service.AuthService;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AccountsHandler {
    private final AuthService authService;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;

    public SendMessage handleAccountsCommand(Long chatId, User user) {
        try {
            if (!authService.isAuthorized(chatId)) {
                String token = authService.fetchUserToken(chatId, user);
                authService.saveUserToken(chatId, token);
            }

            String token = authService.getUserToken(chatId);
            if (token == null) {
                return new SendMessage(chatId.toString(),
                        "❌ Требуется авторизация. Выполните /auth");
            }

            String authToken = token.startsWith("Bearer ") ? token : "Bearer " + token;

            try {
                String response = neoFlexTelegramAPI.getAccounts(authToken);

                if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
                    return new SendMessage(chatId.toString(),
                            "❌ Сервер вернул ошибку. Попробуйте позже или выполните /auth снова");
                }

                return new SendMessage(chatId.toString(), formatAccounts(response));
            } catch (Exception e) {
                // Логируем ошибку для диагностики
                System.err.println("Error fetching accounts: " + e.getMessage());
                return new SendMessage(chatId.toString(),
                        "❌ Ошибка при получении счетов. Попробуйте /auth снова");
            }

        } catch (Exception e) {
            return new SendMessage(chatId.toString(),
                    "❌ Ошибка: " + e.getMessage() + "\nПопробуйте /auth снова");
        }
    }

    private String formatAccounts(String jsonAccounts) {
        try {
            try {
                JSONArray accounts = new JSONArray(jsonAccounts);
                return formatAccountsArray(accounts);
            } catch (JSONException e) {
                // Если не получилось как массив, пробуем как объект с полем accounts
                JSONObject response = new JSONObject(jsonAccounts);
                if (response.has("accounts")) {
                    return formatAccountsArray(response.getJSONArray("accounts"));
                }
                throw new JSONException("Invalid accounts format");
            }
        } catch (Exception e) {
            return "⚠ Не удалось загрузить информацию о счетах";
        }
    }

    private String formatAccountsArray(JSONArray accounts) throws JSONException {
        if (accounts.length() == 0) {
            return "У вас пока нет открытых счетов";
        }

        StringBuilder sb = new StringBuilder("Ваши счета\n\n");

        for (int i = 0; i < accounts.length(); i++) {
            JSONObject account = accounts.getJSONObject(i);

            sb.append("Счет #").append(i+1).append("*\n");
            sb.append("┌ Номер: `").append(account.getString("accountNumber")).append("`\n");
            sb.append("├ Баланс: *").append(formatAmount(account.getDouble("amount"))).append("*\n");
            sb.append("├ Доступно: *").append(formatAmount(account.getDouble("availableAmount"))).append("*\n");
            sb.append("├ Валюта: ").append(getCurrencyName(account.getInt("currencyNumber"))).append("\n");
            sb.append("├ Статус: ").append(formatStatus(account.getString("accountStatus"))).append("\n");
            sb.append("└ Дата открытия: ").append(account.getString("startDate")).append("\n\n");
        }

        return sb.toString();
    }

    private String formatAmount(double amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }

    private String formatStatus(String status) {
        if ("ACTIVE".equals(status)) {
            return "Активен";
        }
        return status;
    }

    private String getCurrencyName(int currencyCode) {
        switch (currencyCode) {
            case 156: return "CNY (юань)";
            case 643: return "RUB (рубль)";
            case 840: return "USD (доллар)";
            case 978: return "EUR (евро)";
            default: return "Код: " + currencyCode;
        }
    }
}

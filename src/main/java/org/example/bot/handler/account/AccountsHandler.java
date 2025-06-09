package org.example.bot.handler.account;

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
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Обработчик команд для управления счетами пользователя.
 */
@Service
@RequiredArgsConstructor
public class AccountsHandler {
    private static final int ACCOUNTS_PER_PAGE = 3;

    private final AuthService authService;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;
    private final UserStateService userStateService;
    private final CommonUtilsService commonUtils;

    /**
     * Обрабатывает команду просмотра счетов.
     *
     * @param chatId ID чата.
     * @param user данные пользователя.
     * @return Сообщение с информацией о счетах.
     */
    public SendMessage handleAccountsCommand(Long chatId, User user) {
        try {
            String token;
            try {
                token = authService.getValidUserToken(chatId, user);
            } catch (Exception e) {
                return commonUtils.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
            }

            try {
                String response = neoFlexTelegramAPI.getAccounts(token);

                if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
                    return commonUtils.createMessage(chatId, "Сервер вернул ошибку. Попробуйте позже или авторизуйтесь нажав на кнопку START");
                }

                JSONArray accounts = parseAccountsResponse(response);
                if (accounts.length() == 0) {
                    return commonUtils.createMessage(chatId, "📭 У вас пока нет открытых счетов");
                }

                userStateService.setUserData(chatId, "accounts", accounts.toString());
                userStateService.setUserData(chatId, "currentPage", 0);
                userStateService.setUserState(chatId, "VIEWING_ACCOUNTS");

                return showAccountsPage(chatId, 0, accounts);
            } catch (Exception e) {
                System.err.println("Error fetching accounts: " + e.getMessage());
                return commonUtils.createMessage(chatId, "Ошибка при получении счетов. Aвторизуйтесь нажав на кнопку START");
            }

        } catch (Exception e) {
            return commonUtils.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
        }
    }

    /**
     * Обрабатывает навигацию по страницам счетов.
     *
     * @param chatId ID чата.
     * @param action действие (prev/next).
     * @param messageId ID сообщения.
     * @return Сообщение с обновленной страницей счетов.
     */
    public EditMessageText handlePageNavigation(Long chatId, String action, Integer messageId) {
        try {
            String accountsJson = userStateService.getUserData(chatId, "accounts").toString();
            if (accountsJson == null) {
                return createEditMessage(chatId, messageId, "Данные счетов не найдены. Начните заново.");
            }

            JSONArray accounts = new JSONArray(accountsJson);
            Integer currentPage = (Integer) userStateService.getUserData(chatId, "currentPage");
            if (currentPage == null) currentPage = 0;

            int totalPages = (int) Math.ceil((double) accounts.length() / ACCOUNTS_PER_PAGE);

            if ("next".equals(action) && currentPage < totalPages - 1) {
                currentPage++;
            } else if ("prev".equals(action) && currentPage > 0) {
                currentPage--;
            }

            userStateService.setUserData(chatId, "currentPage", currentPage);
            return showAccountsPage(chatId, messageId, currentPage, accounts);
        } catch (Exception e) {
            return createEditMessage(chatId, messageId, "Для просмотра счетов, нажмите на кнопку \"Мои счета\"");
        }
    }

    /**
     * Отображает страницу со счетами.
     *
     * @param chatId ID чата.
     * @param page номер страницы.
     * @param accounts массив счетов.
     * @return Сообщение с информацией о счетах.
     * @throws JSONException если возникает ошибка при обработке JSON.
     */
    private SendMessage showAccountsPage(Long chatId, int page, JSONArray accounts) throws JSONException {
        String messageText = buildAccountsPage(page, accounts);
        InlineKeyboardMarkup keyboard = createPaginationKeyboard(page, accounts.length());

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(messageText);
        message.setParseMode("Markdown");
        message.setReplyMarkup(keyboard);
        return message;
    }

    /**
     * Отображает страницу со счетами (редактирование сообщения).
     *
     * @param chatId ID чата.
     * @param messageId ID сообщения.
     * @param page номер страницы.
     * @param accounts массив счетов.
     * @return Сообщение с информацией о счетах.
     * @throws JSONException если возникает ошибка при обработке JSON.
     */
    private EditMessageText showAccountsPage(Long chatId, Integer messageId, int page, JSONArray accounts) throws JSONException {
        String messageText = buildAccountsPage(page, accounts);
        InlineKeyboardMarkup keyboard = createPaginationKeyboard(page, accounts.length());

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(messageText);
        message.setParseMode("Markdown");
        message.setReplyMarkup(keyboard);
        return message;
    }

    /**
     * Формирует текст страницы со счетами.
     *
     * @param page номер страницы.
     * @param accounts массив счетов.
     * @return Текст сообщения.
     * @throws JSONException если возникает ошибка при обработке JSON.
     */
    private String buildAccountsPage(int page, JSONArray accounts) throws JSONException {
        StringBuilder sb = new StringBuilder("📋 Ваши счета\n\n");

        int start = page * ACCOUNTS_PER_PAGE;
        int end = Math.min(start + ACCOUNTS_PER_PAGE, accounts.length());

        for (int i = start; i < end; i++) {
            JSONObject account = accounts.getJSONObject(i);

            sb.append("\n");
            sb.append("💳 Счет №").append(i + 1).append("*\n");
            sb.append("┌──────────────────────────┐\n");
            sb.append("│   Основные параметры*   │\n");
            sb.append("├──────────────────────────┤\n");
            sb.append("│ • Номер: `").append(account.getString("accountNumber")).append("`\n");
            sb.append("│ • ID: `").append(account.getString("id")).append("`\n");
            sb.append("│ • Баланс: ").append(commonUtils.formatAmount(account.getDouble("amount")))
                    .append(" ").append(commonUtils.getCurrencySymbol(account.getInt("currencyNumber"))).append("\n");
            sb.append("│ • Доступно: ").append(commonUtils.formatAmount(account.getDouble("availableAmount")))
                    .append(" ").append(commonUtils.getCurrencySymbol(account.getInt("currencyNumber"))).append("\n");
            sb.append("│ • Валюта: ").append(commonUtils.getCurrencyName(account.getInt("currencyNumber"))).append("\n");
            sb.append("│ • Статус: ").append(commonUtils.formatStatus(account.getString("accountStatus"))).append("\n");
            sb.append("│ • Дата: ").append(account.getString("startDate")).append("\n");
            sb.append("└──────────────────────────┘\n\n");
        }

        sb.append("Страница ").append(page + 1).append(" из ")
                .append((int) Math.ceil((double) accounts.length() / ACCOUNTS_PER_PAGE));

        return sb.toString();
    }

    /**
     * Создает клавиатуру для навигации по страницам.
     *
     * @param currentPage текущая страница.
     * @param totalAccounts общее количество счетов.
     * @return Клавиатура для навигации.
     */
    private InlineKeyboardMarkup createPaginationKeyboard(int currentPage, int totalAccounts) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        int totalPages = (int) Math.ceil((double) totalAccounts / ACCOUNTS_PER_PAGE);

        if (currentPage > 0) {
            row.add(createButton("⬅️ Назад", "prev_accounts_page"));
        }

        if (currentPage < totalPages - 1) {
            row.add(createButton("Вперед ➡️", "next_accounts_page"));
        }

        if (!row.isEmpty()) {
            rows.add(row);
        }

        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Парсит ответ со списком счетов.
     *
     * @param response ответ от API.
     * @return Массив счетов.
     * @throws JSONException если возникает ошибка при обработке JSON.
     */
    private JSONArray parseAccountsResponse(String response) throws JSONException {
        try {
            return new JSONArray(response);
        } catch (JSONException e) {
            JSONObject responseObj = new JSONObject(response);
            if (responseObj.has("accounts")) {
                return responseObj.getJSONArray("accounts");
            }
            throw new JSONException("Invalid accounts format");
        }
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
     * Создает сообщение для редактирования.
     *
     * @param chatId ID чата.
     * @param messageId ID сообщения.
     * @param text текст сообщения.
     * @return Сообщение для редактирования.
     */
    private EditMessageText createEditMessage(Long chatId, Integer messageId, String text) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(text);
        message.setParseMode("Markdown");
        return message;
    }
}
package org.example.bot.handler.deposit;

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

import java.text.NumberFormat;
import java.util.*;

/**
 * Обработчик для работы с вкладами.
 */
@Service
@RequiredArgsConstructor
public class DepositsHandler {
    private static final int DEPOSITS_PER_PAGE = 3;
    private static final String NO_DEPOSITS_MSG = "У вас пока нет вкладов выбранного типа";
    private static final String DEPOSITS_TITLE = "📋 Ваши вклады\n\n";

    private final AuthService authService;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;
    private final CommonUtilsService commonUtilsService;
    private final UserStateService userStateService;

    /**
     * Обрабатывает команду просмотра вкладов.
     *
     * @param chatId ID чата.
     * @param user данные пользователя.
     * @return Сообщение с выбором типа вкладов.
     */
    public SendMessage handleDepositsCommand(Long chatId, User user) {
        try {
            if (!authService.isAuthorized(chatId)) {
                String token = authService.fetchUserToken(chatId, user);
                authService.saveUserToken(chatId, token);
            }

            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            List<InlineKeyboardButton> row1 = new ArrayList<>();
            row1.add(createButton("Открытые", "active_deposits"));
            row1.add(createButton("Закрытые", "closed_deposits"));
            rows.add(row1);

            keyboard.setKeyboard(rows);

            return createMessageWithKeyboard(chatId, "Выберите тип вкладов для просмотра:", keyboard);
        } catch (Exception e) {
            return commonUtilsService.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
        }
    }

    /**
     * Обрабатывает выбор типа вкладов.
     *
     * @param chatId ID чата.
     * @param depositStatus статус вкладов.
     * @param user данные пользователя.
     * @return Сообщение со списком вкладов.
     */
    public SendMessage handleDepositsTypeSelection(Long chatId, String depositStatus, User user) {
        String token;
        try {
            token = authService.getValidUserToken(chatId, user);
        } catch (Exception e) {
            return commonUtilsService.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("depositStatus", depositStatus);

            String response = neoFlexTelegramAPI.getDeposits(token, requestBody);
            if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
                return commonUtilsService.createMessage(chatId, "Сервер вернул ошибку. Попробуйте позже");
            }
            JSONObject jsonResponse = new JSONObject(response);

            if (!jsonResponse.has("deposit")) {
                return commonUtilsService.createMessage(chatId, NO_DEPOSITS_MSG);
            }

            JSONArray deposits = jsonResponse.getJSONArray("deposit");
            if (deposits.length() == 0) {
                return commonUtilsService.createMessage(chatId, NO_DEPOSITS_MSG);
            }

            userStateService.setUserData(chatId, "deposits", deposits.toString());
            userStateService.setUserData(chatId, "currentPage", 0);
            userStateService.setUserState(chatId, "VIEWING_DEPOSITS");

            return showDepositsPage(chatId, 0, deposits);
        } catch (Exception e) {

            return commonUtilsService.handleApiError(chatId, e);
        }
    }

    /**
     * Обрабатывает навигацию по страницам вкладов.
     *
     * @param chatId ID чата.
     * @param action действие (prev/next).
     * @param messageId ID сообщения.
     * @return Обновленное сообщение со списком вкладов.
     */
    public EditMessageText handlePageNavigation(Long chatId, String action, Integer messageId) {
        try {
            String depositsJson = userStateService.getUserData(chatId, "deposits").toString();
            JSONArray deposits = new JSONArray(depositsJson);

            Integer currentPage = (Integer) userStateService.getUserData(chatId, "currentPage");
            if (currentPage == null) currentPage = 0;

            int totalPages = (int) Math.ceil((double) deposits.length() / DEPOSITS_PER_PAGE);

            if ("next".equals(action) && currentPage < totalPages - 1) {
                currentPage++;
            } else if ("prev".equals(action) && currentPage > 0) {
                currentPage--;
            }

            userStateService.setUserData(chatId, "currentPage", currentPage);
            return showDepositsPage(chatId, messageId, currentPage, deposits);
        } catch (Exception e) {
            return createEditMessage(chatId, messageId, "Для просмотра вкладов, нажмите на кнопку \"Мои вклады\"");
        }
    }

    /**
     * Отображает страницу со списком вкладов.
     *
     * @param chatId ID чата.
     * @param page номер страницы.
     * @param deposits массив вкладов.
     * @return Сообщение со списком вкладов.
     * @throws JSONException если возникает ошибка при обработке JSON.
     */
    private SendMessage showDepositsPage(Long chatId, int page, JSONArray deposits) throws JSONException {
        String messageText = buildDepositsPage(page, deposits);
        InlineKeyboardMarkup keyboard = createPaginationKeyboard(page, deposits.length());

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(messageText);
        message.setParseMode("Markdown");
        message.setReplyMarkup(keyboard);
        return message;
    }


    /**
     * Отображает страницу со списком вкладов (редактирование сообщения).
     *
     * @param chatId ID чата.
     * @param messageId ID сообщения.
     * @param page номер страницы.
     * @param deposits массив вкладов.
     * @return Сообщение со списком вкладов.
     * @throws JSONException если возникает ошибка при обработке JSON.
     */
    private EditMessageText showDepositsPage(Long chatId, Integer messageId, int page, JSONArray deposits) throws JSONException {
        String messageText = buildDepositsPage(page, deposits);
        InlineKeyboardMarkup keyboard = createPaginationKeyboard(page, deposits.length());

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(messageText);
        message.setParseMode("Markdown");
        message.setReplyMarkup(keyboard);
        return message;
    }

    /**
     * Формирует текст страницы со списком вкладов.
     *
     * @param page номер страницы.
     * @param deposits массив вкладов.
     * @return Текст сообщения.
     * @throws JSONException если возникает ошибка при обработке JSON.
     */
    private String buildDepositsPage(int page, JSONArray deposits) throws JSONException {
        StringBuilder sb = new StringBuilder(DEPOSITS_TITLE);

        int start = page * DEPOSITS_PER_PAGE;
        int end = Math.min(start + DEPOSITS_PER_PAGE, deposits.length());

        NumberFormat amountFormat = NumberFormat.getNumberInstance(Locale.US);
        amountFormat.setMinimumFractionDigits(2);
        amountFormat.setMaximumFractionDigits(2);

        for (int i = start; i < end; i++) {
            JSONObject deposit = deposits.getJSONObject(i);

            sb.append("\n");
            sb.append("💰 Вклад №").append(i + 1).append("\n"); // Убрал *
            sb.append("┌──────────────────────────┐\n");
            sb.append("│   Основные параметры   │\n");
            sb.append("├──────────────────────────┤\n");
            sb.append("│ • Название: ").append(deposit.getString("depositName")).append("\n");
            sb.append("│ • Номер: `").append(deposit.getString("depositNumber")).append("`\n");
            sb.append("│ • ID: `").append(deposit.getString("id")).append("`\n");
            sb.append("│ • Сумма: ").append(amountFormat.format(deposit.getDouble("startAmount")))
                    .append(" ").append(commonUtilsService.getCurrencyName(deposit.getInt("currencyNumber"))).append("\n");
            sb.append("│ • Ставка: ").append(deposit.getDouble("depositRate")).append("%\n"); // Убрал лишний %
            sb.append("│ • Статус: ").append(formatStatus(deposit.getString("depositStatus"))).append("\n");
            sb.append("│ • Дата открытия: ").append(deposit.getString("startDepositDate")).append("\n");
            sb.append("│ • Дата окончания: ").append(deposit.getString("planEndDate")).append("\n");
            sb.append("│ • Продление: ").append(deposit.getBoolean("prolongation") ? "Да" : "Нет").append("\n");
            sb.append("└──────────────────────────┘\n\n");
        }

        sb.append("Страница ").append(page + 1).append(" из ")
                .append((int) Math.ceil((double) deposits.length() / DEPOSITS_PER_PAGE));

        return sb.toString();
    }

    /**
     * Создает клавиатуру для навигации по страницам.
     *
     * @param currentPage текущая страница.
     * @param totalDeposits общее количество вкладов.
     * @return Клавиатура для навигации.
     */
    private InlineKeyboardMarkup createPaginationKeyboard(int currentPage, int totalDeposits) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        int totalPages = (int) Math.ceil((double) totalDeposits / DEPOSITS_PER_PAGE);
        if (currentPage > 0) {
            row.add(createButton("⬅️ Назад", "prev_deposits_page"));
        }
        if (currentPage < totalPages - 1) {
            row.add(createButton("Вперед ➡️", "next_deposits_page"));
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Форматирует статус вклада.
     *
     * @param status статус вклада.
     * @return Форматированный статус.
     */
    private String formatStatus(String status) {
        return "ACTIVE".equals(status) ? "Активен" : "Закрыт";
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
}
package org.example.bot.handler.credit;

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
 * Обработчик для работы с кредитами.
 */
@Service
@RequiredArgsConstructor
public class CreditsHandler {
    private static final int CREDITS_PER_PAGE = 3;
    private static final String NO_creditS_MSG = "У вас пока нет кредитов выбранного типа";
    private static final String creditS_TITLE = "📋 Ваши кредиты\n\n";

    private final AuthService authService;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;
    private final CommonUtilsService commonUtilsService;
    private final UserStateService userStateService;

    /**
     * Обрабатывает команду просмотра кредитов.
     *
     * @param chatId ID чата.
     * @param user данные пользователя.
     * @return Сообщение с выбором типа кредитов.
     */
    public SendMessage handleCreditsCommand(Long chatId, User user) {
        try {
            if (!authService.isAuthorized(chatId)) {
                String token = authService.fetchUserToken(chatId, user);
                authService.saveUserToken(chatId, token);
            }

            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            List<InlineKeyboardButton> row1 = new ArrayList<>();
            row1.add(createButton("Открытые", "active_credits"));
            row1.add(createButton("Закрытые", "closed_credits"));
            rows.add(row1);

            keyboard.setKeyboard(rows);

            return createMessageWithKeyboard(chatId, "Выберите тип кредитов для просмотра", keyboard);
        } catch (Exception e) {
            return commonUtilsService.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
        }
    }

    /**
     * Обрабатывает выбор типа кредитов.
     *
     * @param chatId ID чата.
     * @param creditStatus статус кредитов.
     * @param user данные пользователя.
     * @return Сообщение со списком кредитов.
     */
    public SendMessage handleCreditsTypeSelection(Long chatId, String creditStatus, User user) {
        String token;
        try {
            token = authService.getValidUserToken(chatId, user);
        } catch (Exception e) {
            return commonUtilsService.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("creditStatus", creditStatus);

            String response = neoFlexTelegramAPI.getCredits(token, requestBody);

            if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
                return commonUtilsService.createMessage(chatId, "Сервер вернул ошибку. Попробуйте позже");
            }

            JSONObject jsonResponse = new JSONObject(response);

            if (!jsonResponse.has("credit")) {
                return commonUtilsService.createMessage(chatId, NO_creditS_MSG);
            }

            JSONArray credits = jsonResponse.getJSONArray("credit");
            if (credits.length() == 0) {
                return commonUtilsService.createMessage(chatId, NO_creditS_MSG);
            }

            userStateService.setUserData(chatId, "credits", credits.toString());
            userStateService.setUserData(chatId, "currentPage", 0);
            userStateService.setUserState(chatId, "VIEWING_creditS");

            return showCreditsPage(chatId, 0, credits);
        } catch (Exception e) {
            return commonUtilsService.handleApiError(chatId, e);
        }
    }

    /**
     * Обрабатывает навигацию по страницам кредитов.
     *
     * @param chatId ID чата.
     * @param action действие (prev/next).
     * @param messageId ID сообщения.
     * @return Сообщение с обновленной страницей кредитов.
     */
    public EditMessageText handlePageNavigation(Long chatId, String action, Integer messageId) {
        try {
            String creditsJson = userStateService.getUserData(chatId, "credits").toString();
            JSONArray credits = new JSONArray(creditsJson);

            Integer currentPage = (Integer) userStateService.getUserData(chatId, "currentPage");
            if (currentPage == null) currentPage = 0;

            int totalPages = (int) Math.ceil((double) credits.length() / CREDITS_PER_PAGE);

            if ("next".equals(action) && currentPage < totalPages - 1) {
                currentPage++;
            } else if ("prev".equals(action) && currentPage > 0) {
                currentPage--;
            }

            userStateService.setUserData(chatId, "currentPage", currentPage);
            return showCreditsPage(chatId, messageId, currentPage, credits);
        } catch (Exception e) {
            return createEditMessage(chatId, messageId, "Для просмотра кредитов, нажмите на кнопку \"Мои кредиты\"");
        }
    }

    /**
     * Отображает страницу с кредитами.
     *
     * @param chatId ID чата.
     * @param page номер страницы.
     * @param credits массив кредитов.
     * @return Сообщение с информацией о кредитах.
     * @throws JSONException если возникает ошибка при обработке JSON.
     */
    private SendMessage showCreditsPage(Long chatId, int page, JSONArray credits) throws JSONException {
        String messageText = buildCreditsPage(page, credits);
        InlineKeyboardMarkup keyboard = createPaginationKeyboard(page, credits.length());

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(messageText);
        message.setParseMode("Markdown");
        message.setReplyMarkup(keyboard);
        return message;
    }

    /**
     * Отображает страницу с кредитами (редактирование сообщения).
     *
     * @param chatId ID чата.
     * @param messageId ID сообщения.
     * @param page номер страницы.
     * @param credits массив кредитов.
     * @return Сообщение с информацией о кредитах.
     * @throws JSONException если возникает ошибка при обработке JSON.
     */
    private EditMessageText showCreditsPage(Long chatId, Integer messageId, int page, JSONArray credits) throws JSONException {
        String messageText = buildCreditsPage(page, credits);
        InlineKeyboardMarkup keyboard = createPaginationKeyboard(page, credits.length());

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(messageText);
        message.setParseMode("Markdown");
        message.setReplyMarkup(keyboard);
        return message;
    }

    /**
     * Формирует текст страницы с кредитами.
     *
     * @param page номер страницы.
     * @param credits массив кредитов.
     * @return Текст сообщения.
     * @throws JSONException если возникает ошибка при обработке JSON.
     */
    private String buildCreditsPage(int page, JSONArray credits) throws JSONException {
        StringBuilder sb = new StringBuilder(creditS_TITLE);

        int start = page * CREDITS_PER_PAGE;
        int end = Math.min(start + CREDITS_PER_PAGE, credits.length());

        NumberFormat amountFormat = NumberFormat.getNumberInstance(Locale.US);
        amountFormat.setMinimumFractionDigits(2);
        amountFormat.setMaximumFractionDigits(2);

        for (int i = start; i < end; i++) {
            JSONObject credit = credits.getJSONObject(i);

            sb.append("\n");
            sb.append("💰 Кредит №").append(i + 1).append("\n");
            sb.append("┌──────────────────────────┐\n");
            sb.append("│   Основные параметры   │\n");
            sb.append("├──────────────────────────┤\n");
            sb.append("│ • Название: ").append(credit.getString("creditName")).append("\n");
            sb.append("│ • Номер: `").append(credit.getString("creditNumber")).append("`\n");
            sb.append("│ • ID: `").append(credit.getString("id")).append("`\n");
            sb.append("│ • Сумма: ").append(amountFormat.format(credit.getDouble("amount")))
                    .append(" ").append(commonUtilsService.getCurrencyName(credit.getInt("currencyNumber"))).append("\n");
            sb.append("│ • Ставка: ").append(credit.getDouble("rate")).append("%\n");
            sb.append("│ • Срок: ").append(credit.getString("period")).append("\n");
            sb.append("│ • Ежемесячный платеж: ").append(amountFormat.format(credit.getDouble("monthPayment")))
                    .append(" ").append(commonUtilsService.getCurrencyName(credit.getInt("currencyNumber"))).append("\n");
            sb.append("│ • Статус: ").append(formatStatus(credit.getString("creditStatus"))).append("\n");
            sb.append("└──────────────────────────┘\n\n");
        }

        sb.append("Страница ").append(page + 1).append(" из ")
                .append((int) Math.ceil((double) credits.length() / CREDITS_PER_PAGE));

        return sb.toString();
    }

    /**
     * Создает клавиатуру для навигации по страницам.
     *
     * @param currentPage текущая страница.
     * @param totalcredits общее количество кредитов.
     * @return Клавиатура для навигации.
     */
    private InlineKeyboardMarkup createPaginationKeyboard(int currentPage, int totalcredits) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        int totalPages = (int) Math.ceil((double) totalcredits / CREDITS_PER_PAGE);
        if (currentPage > 0) {
            row.add(createButton("⬅️ Назад", "prev_credits_page"));
        }
        if (currentPage < totalPages - 1) {
            row.add(createButton("Вперед ➡️", "next_credits_page"));
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Форматирует статус кредита.
     *
     * @param status статус кредита.
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
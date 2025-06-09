package org.example.bot.handler.transfer;

import lombok.RequiredArgsConstructor;
import org.example.bot.fiegn.NeoFlexTelegramAPI;
import org.example.bot.service.AuthService;
import org.example.bot.utils.CommonUtilsService;
import org.example.bot.service.UserStateService;
import org.example.bot.utils.CalendarUtils;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команд для работы с историей переводов.
 * Позволяет просматривать историю входящих и исходящих переводов по счету.
 */
@Service
@RequiredArgsConstructor
public class TransferHistoryHandler {
    private final AuthService authService;
    private final UserStateService userStateService;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;
    private final CommonUtilsService commonUtils;

    private static final int TRANSFERS_PER_PAGE = 3;

    /**
     * Обрабатывает начальную команду запроса истории переводов.
     *
     * @param chatId идентификатор чата
     * @return сообщение с запросом идентификатора счета
     */
    public SendMessage handleInitialCommand(Long chatId) {
        userStateService.setUserState(chatId, "ACCOUNT_ID_TRANSFER");
        return commonUtils.createMessage(chatId, "Введите идентификатор счета для просмотра истории переводов");
    }

    /**
     * Обрабатывает ввод идентификатора счета.
     *
     * @param chatId идентификатор чата
     * @param accountId идентификатор счета
     * @return сообщение с клавиатурой выбора типа операции
     */
    public SendMessage handleAccountIdInput(Long chatId, String accountId) {
        try {
            if (accountId.length() != 36) {
                throw new NumberFormatException();
            }

            userStateService.setUserData(chatId, "accountId", accountId);
            userStateService.setUserState(chatId, "AWAITING_OPERATION");

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("Выберите тип операции:");
            message.setReplyMarkup(createOperationTypeKeyboard());

            return message;

        } catch (NumberFormatException e) {
            return commonUtils.createMessage(chatId, "Неверный формат ID. Пример правильного формата:\n" +
                    "32dae4e3-d413-4b70-baae-8d7d3869c8ab");
        }
    }

    /**
     * Обрабатывает запрос даты начала периода с возможностью редактирования сообщения.
     *
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @return EditMessageText с календарем для выбора даты
     */
    public EditMessageText handleOperation(Long chatId, Integer messageId, String operation) {
        if ("cancel".equals(operation)) {
            userStateService.clearUserState(chatId);
            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText("Операция отменена");
            return message;
        }

        if (!"INCOMING".equals(operation) && !"OUTGOING".equals(operation)) {
            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText("❌ Неверный тип операции. Выберите из предложенных вариантов.");
            return message;
        }

        userStateService.setUserData(chatId, "operation", operation);
        return handleDateFrom(chatId, messageId);
    }

    /**
     * Обрабатывает запрос даты начала периода с возможностью редактирования сообщения.
     *
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @return EditMessageText с календарем для выбора даты
     */
    public EditMessageText handleDateFrom(Long chatId, Integer messageId) {
        userStateService.setUserState(chatId, "AWAITING_FROM_DATE");
        return createCalendarEditMessage(chatId, messageId, "📅 Выберите начало периода:");
    }

    /**
     * Обрабатывает запрос даты окончания периода с возможностью редактирования сообщения.
     *
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @return EditMessageText с календарем для выбора даты
     */
    public EditMessageText handleDateTo(Long chatId, Integer messageId) {
        userStateService.setUserState(chatId, "AWAITING_TO_DATE");
        return createCalendarEditMessage(chatId, messageId, "📅 Выберите конец периода:");
    }

    /**
     * Обрабатывает ввод даты окончания периода с возможностью редактирования сообщения.
     *
     * @param chatId идентификатор чата
     * @param user объект пользователя Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param dateTo дата окончания периода
     * @return EditMessageText с историей переводов или сообщением об ошибке
     */
    public EditMessageText handleDateTo(Long chatId, User user, Integer messageId, String dateTo) {
        String token;
        try {
            token = authService.getValidUserToken(chatId, user);
        } catch (Exception e) {
            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText("Ошибка авторизации, авторизуйтесь нажав на кнопку START");
            return message;
        }

        try {
            userStateService.setUserData(chatId, "toDate", dateTo);
            String accountId = (String) userStateService.getUserData(chatId, "accountId");
            String operation = (String) userStateService.getUserData(chatId, "operation");
            String fromDate = (String) userStateService.getUserData(chatId, "fromDate");
            String toDate = (String) userStateService.getUserData(chatId, "toDate");

            if (accountId == null || accountId.isEmpty()) {
                EditMessageText message = new EditMessageText();
                message.setChatId(chatId.toString());
                message.setMessageId(messageId);
                message.setText("❌ Не найден идентификатор счета. Начните заново.");
                return message;
            }

            String response = transferHistory(token, accountId, operation, fromDate, toDate);

            if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
                EditMessageText message = new EditMessageText();
                message.setChatId(chatId.toString());
                message.setMessageId(messageId);
                message.setText("Сервер вернул ошибку. Попробуйте позже");
                return message;
            }

            if (response.isEmpty()) {
                EditMessageText message = new EditMessageText();
                message.setChatId(chatId.toString());
                message.setMessageId(messageId);
                message.setText("Пустой ответ от сервера");
                return message;
            }

            userStateService.setUserData(chatId, "transfersResponse", response);
            userStateService.setUserData(chatId, "currentTransfersPage", 0);
            userStateService.setUserState(chatId, "VIEWING_TRANSFERS");

            return formatTransferHistoryResponse(chatId, messageId, response, 0);
        } catch (Exception e) {
            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(commonUtils.handleApiError(chatId, e).getText());
            return message;
        }
    }

    /**
     * Обрабатывает навигацию по страницам истории переводов.
     *
     * @param chatId идентификатор чата
     * @param action действие ("prev" или "next")
     * @param messageId идентификатор сообщения для редактирования
     * @return EditMessageText с обновленной страницей истории переводов
     */
    public EditMessageText handleTransfersPageNavigation(Long chatId, String action, Integer messageId) {
        try {
            String response = userStateService.getUserData(chatId, "transfersResponse").toString();
            if (response == null) {
                return createEditMessage(chatId, messageId, "Данные переводов не найдены. Начните заново.");
            }

            Integer currentPage = (Integer) userStateService.getUserData(chatId, "currentTransfersPage");
            if (currentPage == null) currentPage = 0;

            JSONObject jsonResponse = new JSONObject(response);
            JSONArray transfers = jsonResponse.getJSONArray("transfers");
            int totalPages = (int) Math.ceil((double) transfers.length() / TRANSFERS_PER_PAGE);

            if ("next".equals(action) && currentPage < totalPages - 1) {
                currentPage++;
            } else if ("prev".equals(action) && currentPage > 0) {
                currentPage--;
            }

            userStateService.setUserData(chatId, "currentTransfersPage", currentPage);
            return formatTransferHistoryResponse(chatId, messageId, response, currentPage);
        } catch (Exception e) {
            e.printStackTrace();
            return createEditMessage(chatId, messageId, "Ошибка при навигации по переводам");
        }
    }

    /**
     * Создает клавиатуру для выбора типа операции.
     *
     * @return InlineKeyboardMarkup с кнопками выбора типа операции
     */
    public InlineKeyboardMarkup createOperationTypeKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createOperationButton("Входящие", "INCOMING"));
        row1.add(createOperationButton("Исходящие", "OUTGOING"));

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createOperationButton("❌ Отмена", "cancel"));

        rows.add(row1);
        rows.add(row2);

        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Создает кнопку для выбора типа операции.
     *
     * @param text текст кнопки
     * @param callbackData данные обратного вызова
     * @return InlineKeyboardButton с заданными параметрами
     */
    private InlineKeyboardButton createOperationButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    /**
     * Создает редактируемое сообщение.
     *
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param text текст сообщения
     * @return EditMessageText с заданными параметрами
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
     * Запрашивает историю переводов через API.
     *
     * @param token токен авторизации
     * @param accountId идентификатор счета
     * @param operation тип операции
     * @param fromDate дата начала периода
     * @param toDate дата окончания периода
     * @return ответ API в виде строки
     */
    private String transferHistory(String token, String accountId, String operation, String fromDate, String toDate) {
        String authToken = token.startsWith("Bearer ") ? token : "Bearer " + token;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("accountId", accountId);
        requestBody.put("operation", operation);
        requestBody.put("fromDate", fromDate);
        requestBody.put("toDate", toDate);

        return neoFlexTelegramAPI.transferHistory(authToken, requestBody);
    }

    /**
     * Форматирует историю переводов для отображения с возможностью редактирования сообщения.
     *
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param jsonResponse JSON-строка с историей переводов
     * @param page номер текущей страницы
     * @return EditMessageText с отформатированной историей переводов
     */
    private EditMessageText formatTransferHistoryResponse(Long chatId, Integer messageId, String jsonResponse, int page) {
        try {
            JSONObject response = new JSONObject(jsonResponse);
            StringBuilder sb = new StringBuilder();

            sb.append("📊 История переводов\n\n");

            JSONArray transfers = response.getJSONArray("transfers");
            if (transfers.length() == 0) {
                sb.append("ℹ️ Переводы не найдены\n");
            } else {
                int start = page * TRANSFERS_PER_PAGE;
                int end = Math.min(start + TRANSFERS_PER_PAGE, transfers.length());

                for (int i = start; i < end; i++) {
                    JSONObject transfer = transfers.getJSONObject(i);
                    sb.append(formatSingleTransfer(transfer, i));
                }

                sb.append("\nСтраница ").append(page + 1).append(" из ")
                        .append((int) Math.ceil((double) transfers.length() / TRANSFERS_PER_PAGE));
            }

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(sb.toString());
            message.setParseMode("Markdown");
            message.setReplyMarkup(createPaginationKeyboard(page, transfers.length()));
            return message;
        } catch (Exception e) {
            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText("⚠ Ошибка форматирования истории переводов\n\n" +
                    "Некоторые данные могут отображаться некорректно.\n" +
                    "Полный ответ сервера:\n" +
                    "`" + jsonResponse + "`");
            return message;
        }
    }

    /**
     * Обрабатывает выбор типа операции (входящие/исходящие).
     *
     * @param chatId идентификатор чата
     * @param operation тип операции
     * @return сообщение с запросом даты начала периода
     */
    public SendMessage handleOperation(Long chatId, String operation) {
        if ("cancel".equals(operation)) {
            userStateService.clearUserState(chatId);
            return commonUtils.createMessage(chatId, "Операция отменена");
        }

        if (!"INCOMING".equals(operation) && !"OUTGOING".equals(operation)) {
            return commonUtils.createMessage(chatId, "❌ Неверный тип операции. Выберите из предложенных вариантов.");
        }

        userStateService.setUserData(chatId, "operation", operation);
        return handleDateFrom(chatId);
    }

    public SendMessage handleDateFrom(Long chatId) {
        userStateService.setUserState(chatId, "AWAITING_FROM_DATE");
        return createCalendarMessage(chatId, "📅 Выберите начало периода:");
    }

    /**
     * Обрабатывает ввод даты окончания периода.
     *
     * @param chatId идентификатор чата
     * @param user объект пользователя Telegram
     * @param dateTo дата окончания периода
     * @return сообщение с историей переводов или ошибкой
     */
    public SendMessage handleDateTo(Long chatId, User user, String dateTo) {
        String token;
        try {
            token = authService.getValidUserToken(chatId, user);
        } catch (Exception e) {
            return commonUtils.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
        }

        try {
            userStateService.setUserData(chatId, "toDate", dateTo);
            String accountId = (String) userStateService.getUserData(chatId, "accountId");
            String operation = (String) userStateService.getUserData(chatId, "operation");
            String fromDate = (String) userStateService.getUserData(chatId, "fromDate");
            String toDate = (String) userStateService.getUserData(chatId, "toDate");

            if (accountId == null || accountId.isEmpty()) {
                return commonUtils.createMessage(chatId, "❌ Не найден идентификатор счета. Начните заново.");
            }

            String response = transferHistory(token, accountId, operation, fromDate, toDate);

            if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
                return commonUtils.createMessage(chatId, "Сервер вернул ошибку. Попробуйте позже");
            }

            if (response.isEmpty()) {
                return commonUtils.createMessage(chatId, "Пустой ответ от сервера");
            }

            return formatTransferHistoryResponse(chatId, response);
        } catch (Exception e) {
            return commonUtils.handleApiError(chatId, e);
        } finally {
            userStateService.clearUserState(chatId);
        }
    }

    /**
     * Создает клавиатуру для навигации по страницам.
     *
     * @param currentPage текущая страница
     * @param totalTransfers общее количество переводов
     * @return InlineKeyboardMarkup с кнопками навигации
     */
    private InlineKeyboardMarkup createPaginationKeyboard(int currentPage, int totalTransfers) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        int totalPages = (int) Math.ceil((double) totalTransfers / TRANSFERS_PER_PAGE);

        if (currentPage > 0) {
            row.add(createButton("⬅️ Назад", "prev_transfers_page"));
        }

        if (currentPage < totalPages - 1) {
            row.add(createButton("Вперед ➡️", "next_transfers_page"));
        }

        if (!row.isEmpty()) {
            rows.add(row);
        }

        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Форматирует историю переводов для отображения.
     *
     * @param chatId идентификатор чата
     * @param jsonResponse JSON-строка с историей переводов
     * @return SendMessage с отформатированной историей переводов
     */
    private SendMessage formatTransferHistoryResponse(Long chatId, String jsonResponse) {
        try {
            JSONObject response = new JSONObject(jsonResponse);
            StringBuilder sb = new StringBuilder();

            sb.append("📊 История переводов\n\n");

            JSONArray transfers = response.getJSONArray("transfers");
            if (transfers.length() == 0) {
                sb.append("ℹ️ Переводы не найдены\n");
            } else {
                for (int i = 0; i < transfers.length(); i++) {
                    JSONObject transfer = transfers.getJSONObject(i);
                    sb.append(formatSingleTransfer(transfer, i));
                }
            }

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(sb.toString());
            message.setParseMode("Markdown");
            return message;
        } catch (Exception e) {
            return new SendMessage(chatId.toString(),
                    "⚠ Ошибка форматирования истории переводов\n\n" +
                            "Некоторые данные могут отображаться некорректно.\n" +
                            "Полный ответ сервера:\n" +
                            "`" + jsonResponse + "`");
        }
    }

    /**
     * Форматирует информацию об отдельном переводе.
     *
     * @param transfer JSON-объект с данными перевода
     * @param index порядковый номер перевода
     * @return отформатированную строку с информацией о переводе
     * @throws Exception при ошибке обработки данных
     */
    private String formatSingleTransfer(JSONObject transfer, int index) throws Exception {
        StringBuilder sb = new StringBuilder();

        sb.append("┌──────────────────────────┐\n");
        sb.append("│ Перевод №").append(index + 1).append("\n");
        sb.append("├──────────────────────────┤\n");
        sb.append("│ • Тип: ").append(transfer.getString("type")).append("\n");
        sb.append("│ • Дата: ").append(transfer.getString("datetime")).append("\n");
        sb.append("│ • Статус: ").append(formatStatus(transfer.getString("status"))).append("\n");

        JSONObject fromTransfer = transfer.getJSONObject("fromTransfer");
        sb.append("│\n│ Списание со счета:\n");
        sb.append("│   ▸ Номер: ").append(fromTransfer.getString("accountNumber")).append("\n");
        sb.append("│   ▸ Сумма: ").append(fromTransfer.getInt("amount"))
                .append(" ").append(commonUtils.getCurrencySymbol(fromTransfer.getInt("currencyNumber"))).append("\n");

        JSONObject toTransfer = transfer.getJSONObject("toTransfer");
        sb.append("│\n│ Зачисление на счет:\n");
        sb.append("│   ▸ Номер: ").append(toTransfer.getString("accountNumber")).append("\n");
        sb.append("│   ▸ Сумма: ").append(toTransfer.getInt("amount"))
                .append(" ").append(commonUtils.getCurrencySymbol(toTransfer.getInt("currencyNumber"))).append("\n");

        sb.append("└──────────────────────────┘\n\n");

        return sb.toString();
    }

    /**
     * Форматирует статус перевода для отображения.
     *
     * @param status исходный статус
     * @return отформатированный статус
     */
    private String formatStatus(String status) {
        return "Исполнено".equals(status) ? "Перевод средств выполнен" : status;
    }

    /**
     * Создает редактируемое сообщение с календарем.
     *
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param text текст сообщения
     * @return EditMessageText с календарем
     */
    private EditMessageText createCalendarEditMessage(Long chatId, Integer messageId, String text) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(text);
        message.setReplyMarkup(CalendarUtils.createCalendar(LocalDate.now()));
        return message;
    }

    /**
     * Создает сообщение с календарем.
     *
     * @param chatId идентификатор чата
     * @param text текст сообщения
     * @return SendMessage с календарем
     */
    private SendMessage createCalendarMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setReplyMarkup(CalendarUtils.createCalendar(LocalDate.now()));
        return message;
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
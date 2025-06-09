package org.example.bot.handler.credit;

import lombok.RequiredArgsConstructor;
import org.example.bot.fiegn.NeoFlexTelegramAPI;
import org.example.bot.service.AuthService;
import org.example.bot.utils.CommonUtilsService;
import org.example.bot.service.UserStateService;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Обработчик для работы с графиком платежей по кредиту.
 */
@Service
@RequiredArgsConstructor
public class PaymentPlanHandler {
    private static final int PAYMENTS_PER_PAGE = 5;

    private final AuthService authService;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;
    private final UserStateService userStateService;
    private final CommonUtilsService commonUtilsService;

    /**
     * Обрабатывает начальную команду просмотра графика платежей.
     *
     * @param chatId ID чата.
     * @return Сообщение с запросом ID кредита.
     */
    public SendMessage handleInitialCommand(Long chatId) {
        userStateService.setUserState(chatId, "AWAITING_CREDIT_ID_FOR_PLAN");
        return commonUtilsService.createMessage(chatId, "Введите ID кредита для просмотра графика платежей");
    }

    /**
     * Обрабатывает ввод ID кредита.
     *
     * @param chatId ID чата.
     * @param user данные пользователя.
     * @param creditId ID кредита.
     * @return Сообщение с графиком платежей или ошибкой.
     */
    public SendMessage handleCreditIdInput(Long chatId, User user, String creditId) {
        try {
            if (creditId.length() != 36) {
                throw new NumberFormatException();
            }

            String token;
            try {
                token = authService.getValidUserToken(chatId, user);
            } catch (Exception e) {
                return commonUtilsService.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
            }

            try {
                String response = neoFlexTelegramAPI.getPaymentPlan(token, creditId);

                if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
                    return commonUtilsService.createMessage(chatId, "Сервер вернул ошибку. Попробуйте позже");
                }

                JSONObject creditInfo = new JSONObject(response);
                JSONArray paymentPlan = creditInfo.getJSONArray("paymentPlan");

                userStateService.setUserData(chatId, "paymentPlan", paymentPlan.toString());
                userStateService.setUserData(chatId, "currentPage", 0);
                userStateService.setUserData(chatId, "creditId", creditId);
                userStateService.setUserState(chatId, "VIEWING_PAYMENT_PLAN");

                return showPaymentPlanPage(chatId, 0, paymentPlan);
            } catch (Exception e) {
                return commonUtilsService.createMessage(chatId, "❌ Ошибка при получении графика платежей: " + e.getMessage());
            }
        }catch (NumberFormatException e) {
            return commonUtilsService.createMessage(chatId, "Неверный формат ID. Пример правильного формата:\n" +
                    "32dae4e3-d413-4b70-baae-8d7d3869c8ab");
        }
    }

    /**
     * Обрабатывает навигацию по страницам графика платежей.
     *
     * @param chatId ID чата.
     * @param action действие (prev/next).
     * @param messageId ID сообщения.
     * @return Обновленное сообщение с графиком платежей.
     */
    public BotApiMethod<?> handlePageNavigation(Long chatId, String action, Integer messageId) {
        try {
            String paymentPlanJson = userStateService.getUserData(chatId, "paymentPlan").toString();
            if (paymentPlanJson == null) {
                return commonUtilsService.createMessage(chatId, "❌ Данные платежного плана не найдены. Начните заново.");
            }

            JSONArray paymentPlan = new JSONArray(paymentPlanJson);
            Integer currentPage = (Integer) userStateService.getUserData(chatId, "currentPage");
            if (currentPage == null) currentPage = 0;

            int totalPages = (int) Math.ceil((double) paymentPlan.length() / PAYMENTS_PER_PAGE);

            if ("next".equals(action) && currentPage < totalPages - 1) {
                currentPage++;
            } else if ("prev".equals(action) && currentPage > 0) {
                currentPage--;
            }

            userStateService.setUserData(chatId, "currentPage", currentPage);

            String messageText = buildPaymentPlanPage(currentPage, paymentPlan);
            InlineKeyboardMarkup keyboard = createPaginationKeyboard(currentPage, paymentPlan.length());

            EditMessageText editMessage = createEditMessage(chatId, messageId, messageText);
            editMessage.setReplyMarkup(keyboard);
            return editMessage;
        } catch (Exception e) {
            return createEditMessage(chatId, messageId, "Для просмотра графика платежей, нажмите на кнопку \"График платежей\"");
        }
    }

    /**
     * Формирует текст страницы с графиком платежей.
     *
     * @param page номер страницы.
     * @param paymentPlan массив платежей.
     * @return Текст сообщения.
     * @throws Exception если возникает ошибка.
     */
    private String buildPaymentPlanPage(int page, JSONArray paymentPlan) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("📅 График платежей\n\n");

        int start = page * PAYMENTS_PER_PAGE;
        int end = Math.min(start + PAYMENTS_PER_PAGE, paymentPlan.length());

        for (int i = start; i < end; i++) {
            JSONObject payment = paymentPlan.getJSONObject(i);
            sb.append("┌──────────────────────────┐\n");
            sb.append("│ Платеж №").append(payment.getInt("paymentNumber")).append("*\n");
            sb.append("├──────────────────────────┤\n");
            sb.append("│ Дата: ").append(payment.getString("paymentDate")).append("\n");
            sb.append("│ Платеж: ").append(formatAmount(payment.getDouble("monthPayment"))).append("*\n");
            sb.append("│ Основной долг: ").append(formatAmount(payment.getDouble("repaymentDept"))).append("\n");
            sb.append("│ Проценты: ").append(formatAmount(payment.getDouble("paymentPercent"))).append("\n");
            sb.append("│ Остаток: ").append(formatAmount(payment.getDouble("balanceAmount"))).append("\n");
            sb.append("└──────────────────────────┘\n\n");
        }

        sb.append("Страница ").append(page + 1).append(" из ")
                .append((int) Math.ceil((double) paymentPlan.length() / PAYMENTS_PER_PAGE));

        return sb.toString();
    }

    /**
     * Отображает страницу с графиком платежей.
     *
     * @param chatId ID чата.
     * @param page номер страницы.
     * @param paymentPlan массив платежей.
     * @return Сообщение с графиком платежей.
     * @throws Exception если возникает ошибка.
     */
    private SendMessage showPaymentPlanPage(Long chatId, int page, JSONArray paymentPlan) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("📅 График платежей\n\n");

        int start = page * PAYMENTS_PER_PAGE;
        int end = Math.min(start + PAYMENTS_PER_PAGE, paymentPlan.length());

        for (int i = start; i < end; i++) {
            JSONObject payment = paymentPlan.getJSONObject(i);
            sb.append("┌──────────────────────────┐\n");
            sb.append("│ Платеж №").append(payment.getInt("paymentNumber")).append("*\n");
            sb.append("├──────────────────────────┤\n");
            sb.append("│ Дата: ").append(payment.getString("paymentDate")).append("\n");
            sb.append("│ Платеж: ").append(formatAmount(payment.getDouble("monthPayment"))).append("*\n");
            sb.append("│ Основной долг: ").append(formatAmount(payment.getDouble("repaymentDept"))).append("\n");
            sb.append("│ Проценты: ").append(formatAmount(payment.getDouble("paymentPercent"))).append("\n");
            sb.append("│ Остаток: ").append(formatAmount(payment.getDouble("balanceAmount"))).append("\n");
            sb.append("└──────────────────────────┘\n\n");
        }

        sb.append("Страница ").append(page + 1).append(" из ")
                .append((int) Math.ceil((double) paymentPlan.length() / PAYMENTS_PER_PAGE));

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(sb.toString());
        message.setParseMode("Markdown");
        message.setReplyMarkup(createPaginationKeyboard(page, paymentPlan.length()));

        return message;
    }

    /**
     * Создает клавиатуру для навигации по страницам.
     *
     * @param currentPage текущая страница.
     * @param totalPayments общее количество платежей.
     * @return Клавиатура для навигации.
     */
    private InlineKeyboardMarkup createPaginationKeyboard(int currentPage, int totalPayments) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        int totalPages = (int) Math.ceil((double) totalPayments / PAYMENTS_PER_PAGE);

        if (currentPage > 0) {
            row.add(createButton("⬅️ Назад", "prev_payment_page"));
        }

        if (currentPage < totalPages - 1) {
            row.add(createButton("Вперед ➡️", "next_payment_page"));
        }

        if (!row.isEmpty()) {
            rows.add(row);
        }

        markup.setKeyboard(rows);
        return markup;
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
     * Форматирует сумму платежа.
     *
     * @param amount сумма.
     * @return Отформатированная строка.
     */
    private String formatAmount(double amount) {
        return String.format("%,.2f ₽", amount).replace(",", " ");
    }

}

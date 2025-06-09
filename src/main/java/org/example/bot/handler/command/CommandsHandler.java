package org.example.bot.handler.command;

import lombok.RequiredArgsConstructor;
import org.example.bot.handler.currencies.CurrenciesHandler;
import org.example.bot.handler.exception.TokenExpiredException;
import org.example.bot.handler.facades.FacadeAccountHandlers;
import org.example.bot.handler.facades.FacadeCreditHandlers;
import org.example.bot.handler.facades.FacadeDepositHandlers;
import org.example.bot.handler.facades.FacadeTransferHandlers;
import org.example.bot.handler.product.ProductsHandler;
import org.example.bot.service.AuthService;
import org.example.bot.utils.CommonUtilsService;
import org.example.bot.service.UserStateService;
import org.example.bot.utils.CalendarUtils;
import org.example.bot.utils.ReplyKeyboardMenu;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;

import java.time.LocalDate;

/**
 * Главный обработчик команд бота.
 */
@Service
@RequiredArgsConstructor
public class CommandsHandler {
    private final StartHandler startHandler;
    private final AuthService authService;
    private final ReplyKeyboardMenu replyKeyboardMenu;
    private final CommonUtilsService commonUtilsService;
    private final FacadeCreditHandlers facadeCreditHandlers;
    private final FacadeAccountHandlers facadeAccountHandlers;
    private final FacadeDepositHandlers facadeDepositHandlers;
    private final FacadeTransferHandlers facadeTransferHandlers;
    private final CurrenciesHandler currenciesHandler;
    private final ProductsHandler productsHandler;
    private final UserStateService userStateService;

    /**
     * Обрабатывает входящие команды.
     *
     * @param update входящее обновление.
     * @return Ответное действие бота.
     * @throws Exception если возникает ошибка.
     */
    public BotApiMethod<?> handleCommands(Update update) throws Exception {
        if (update.hasCallbackQuery()) {
            return handleCallbackQuery(update);
        }

        Long chatId = update.getMessage().getChatId();
        String userState = userStateService.getUserState(chatId);

        if (userState != null) {
            return handleStatefulCommand(chatId, userState, update);
        }

        return handleRegularCommand(update, chatId);
    }

    /**
     * Обрабатывает callback-запросы.
     *
     * @param update входящее обновление.
     * @return Ответное действие бота.
     * @throws Exception если возникает ошибка.
     */
    private BotApiMethod<?> handleCallbackQuery(Update update) throws Exception {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();
        User user = callbackQuery.getFrom();
        String callbackData = callbackQuery.getData();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String userState = userStateService.getUserState(chatId);

        if (callbackData.equals("back")) {
            return switch (userState) {
                case "AWAITING_DEPOSIT_PRODUCT" ->
                        facadeDepositHandlers.getNewDepositHandler().handleProductInput(chatId, "back");
                case "AWAITING_DEPOSIT_TERM" ->
                        facadeDepositHandlers.getNewDepositHandler().handleTermInput(chatId, "back");
                case "AWAITING_DEPOSIT_AUTOPROLONGATION" ->
                        facadeDepositHandlers.getNewDepositHandler().handleAutoprolongationInput(chatId, "back");
                case "AWAITING_DEPOSIT_CONFIRM" ->
                        facadeDepositHandlers.getNewDepositHandler().handleConfirmation(chatId, user, "back");
                case "AWAITING_CREDIT_CONFIRM" ->
                        facadeCreditHandlers.getNewCreditHandler().handleConfirmation(chatId, user, "back");
                case "AWAITING_ACCOUNT_CONFIRM" ->
                        facadeAccountHandlers.getNewAccountHandler().handleConfirmation(chatId, user, "back");
                case "AWAITING_CREDIT_PRODUCT" ->
                        facadeCreditHandlers.getNewCreditHandler().handleProductInput(chatId, "back");
                case "AWAITING_CREDIT_TERM" ->
                        facadeCreditHandlers.getNewCreditHandler().handleTermInput(chatId, "back");
                default -> createMessage(chatId, " Неизвестное состояние: " + userState);
            };
        }

        if (callbackData.startsWith("confirm_")) {
            boolean confirmed = "confirm_yes".equals(callbackData);
            return switch (userState) {
                case "AWAITING_ACCOUNT_CONFIRM" -> {
                    if (userStateService.getUserData(chatId, "accountId") != null) {
                        yield facadeAccountHandlers.getCloseAccountHandler().handleConfirmation(chatId, user, confirmed);
                    } else {
                        yield facadeAccountHandlers.getNewAccountHandler().handleConfirmation(chatId, user, callbackData);
                    }
                }
                case "AWAITING_DEPOSIT_CONFIRM_CLOSE" ->
                        facadeDepositHandlers.getCloseDepositHandler().handleConfirmation(chatId, user, confirmed);
                case "AWAITING_DEPOSIT_CONFIRM" ->
                        facadeDepositHandlers.getNewDepositHandler().handleConfirmation(chatId, user, callbackData);
                case "AWAITING_CREDIT_CONFIRM" ->
                        facadeCreditHandlers.getNewCreditHandler().handleConfirmation(chatId, user, callbackData);
                case "AWAITING_CLOSE_DEPOSIT_CONFIRM" ->
                        facadeDepositHandlers.getCloseDepositHandler().handleConfirmation(chatId, user, confirmed);
                case "AWAITING_CREDIT_CLOSE_CONFIRM" ->
                        facadeCreditHandlers.getCloseCreditHandler().handleConfirmation(chatId, user, confirmed);

                case "AWAITING_TRANSFER_CONFIRM" ->
                        facadeTransferHandlers.getTransferHandler().handleConfirmation(chatId, user, confirmed);
                default -> createMessage(chatId, "Неизвестное состояние подтверждения");
            };
        }

        if (callbackData.startsWith("date_")) {
            String date = callbackData.split("_")[1];

            if ("AWAITING_FROM_DATE".equals(userState)) {
                userStateService.setUserData(chatId, "fromDate", date);
                return facadeTransferHandlers.getTransferHistoryHandler().handleDateTo(chatId, messageId);
            } else if ("AWAITING_TO_DATE".equals(userState)) {
                userStateService.setUserData(chatId, "toDate", date);
                return facadeTransferHandlers.getTransferHistoryHandler().handleDateTo(chatId, user, messageId, date);
            }
        }

        if (callbackData.startsWith("prev_month_") || callbackData.startsWith("next_month_")) {
            String[] parts = callbackData.split("_");
            int month = Integer.parseInt(parts[2]);
            int year = Integer.parseInt(parts[3]);
            LocalDate newDate = LocalDate.of(year, month, 1);

            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId.toString());
            editMessage.setMessageId(messageId);
            editMessage.setText("📅 Выберите дату:");
            editMessage.setReplyMarkup(CalendarUtils.createCalendar(newDate));

            return editMessage;
        }


        if ("cancel".equals(callbackData)) {
            userStateService.clearUserState(chatId);
            return createMessage(chatId, "Операция отменена");
        }

        if ("prev_payment_page".equals(callbackData) || "next_payment_page".equals(callbackData)) {
            String action = callbackData.equals("prev_payment_page") ? "prev" : "next";
            return facadeCreditHandlers.getPaymentPlanHandler().handlePageNavigation(chatId, action, messageId);
        }

        if ("prev_accounts_page".equals(callbackData) || "next_accounts_page".equals(callbackData)) {
            String action = callbackData.equals("prev_accounts_page") ? "prev" : "next";
            return facadeAccountHandlers.getAccountsHandler().handlePageNavigation(chatId, action, messageId);
        }

        if (callbackData.equals("active_deposits") || callbackData.equals("closed_deposits")) {
            String status = callbackData.equals("active_deposits") ? "ACTIVE" : "CLOSED";
            return facadeDepositHandlers.getDepositsHandler().handleDepositsTypeSelection(chatId, status, user);
        }

        if ("prev_deposits_page".equals(callbackData) || "next_deposits_page".equals(callbackData)) {
            String action = callbackData.equals("prev_deposits_page") ? "prev" : "next";
            return facadeDepositHandlers.getDepositsHandler().handlePageNavigation(chatId, action, messageId);

        }

        if (callbackData.equals("active_credits") || callbackData.equals("closed_credits")) {
            String status = callbackData.equals("active_credits") ? "ACTIVE" : "CLOSED";
            return facadeCreditHandlers.getCreditsHandler().handleCreditsTypeSelection(chatId, status, user);
        }

        if ("prev_credits_page".equals(callbackData) || "next_credits_page".equals(callbackData)) {
            String action = callbackData.equals("prev_credits_page") ? "prev" : "next";
            return facadeCreditHandlers.getCreditsHandler().handlePageNavigation(chatId, action, messageId);
        }

        if ("prev_transfers_page".equals(callbackData) || "next_transfers_page".equals(callbackData)) {
            String action = callbackData.equals("prev_transfers_page") ? "prev" : "next";
            return facadeTransferHandlers.getTransferHistoryHandler().handleTransfersPageNavigation(chatId, action, messageId);
        }

        return switch (userState) {
            case "AWAITING_PRODUCT_TYPE" -> productsHandler.handleProductTypeInput(chatId, user, callbackData);
            case "AWAITING_CURRENCY" ->
                    facadeAccountHandlers.getNewAccountHandler().handleCurrencyInput(chatId, callbackData);
            case "AWAITING_ACCOUNT_CURRENCY" ->
                    facadeAccountHandlers.getCloseAccountHandler().handleCurrencyInput(chatId, callbackData);
            case "AWAITING_DEPOSIT_PRODUCT" ->
                    facadeDepositHandlers.getNewDepositHandler().handleProductInput(chatId, callbackData);
            case "AWAITING_DEPOSIT_TERM" ->
                    facadeDepositHandlers.getNewDepositHandler().handleTermInput(chatId, callbackData);
            case "AWAITING_DEPOSIT_AUTOPROLONGATION" ->
                    facadeDepositHandlers.getNewDepositHandler().handleAutoprolongationInput(chatId, callbackData);
            case "AWAITING_CREDIT_PRODUCT" ->
                    facadeCreditHandlers.getNewCreditHandler().handleProductInput(chatId, callbackData);
            case "AWAITING_CREDIT_TERM" ->
                    facadeCreditHandlers.getNewCreditHandler().handleTermInput(chatId, callbackData);
            case "ACCOUNT_ID_TRANSFER" ->
                    facadeTransferHandlers.getTransferHistoryHandler().handleAccountIdInput(chatId, callbackData);
            case "AWAITING_OPERATION" ->
                    facadeTransferHandlers.getTransferHistoryHandler().handleOperation(chatId, messageId, callbackData);
            default -> createMessage(chatId, "Неизвестное состояние: " + userState);
        };
    }

    /**
     * Обрабатывает команды с учетом состояния пользователя.
     *
     * @param chatId ID чата.
     * @param userState состояние пользователя.
     * @param update входящее обновление.
     * @return Ответное действие бота.
     * @throws Exception если возникает ошибка.
     */
    private BotApiMethod<?> handleStatefulCommand(Long chatId, String userState, Update update) throws Exception {
        User user = update.getMessage().getFrom();
        String text = update.getMessage().getText();

        if ("❌ Отмена операции".equals(text)) {
            userStateService.clearUserState(chatId);
            return createMessage(chatId, "Операция отменена");
        } else if ("↩️ Назад".equals(text)) {
            userStateService.clearUserState(chatId);
            return createMainMenuMessage(chatId);
        }

        return switch (userState) {
            case "AWAITING_PRODUCT_TYPE" -> productsHandler.handleProductTypeInput(chatId, user, text);
            case "AWAITING_ACCOUNT_AMOUNT" ->
                    facadeAccountHandlers.getNewAccountHandler().handleAmountInput(chatId, text);
            case "AWAITING_CURRENCY" -> {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText("Пожалуйста, выберите валюту из предложенных кнопок");
                message.setReplyMarkup(facadeAccountHandlers.getNewAccountHandler().createCurrencyKeyboard());
                yield message;
            }

            case "AWAITING_DEPOSIT_ACCOUNT" ->
                    facadeDepositHandlers.getNewDepositHandler().handleAccountInput(chatId, text);
            case "AWAITING_DEPOSIT_AMOUNT" ->
                    facadeDepositHandlers.getNewDepositHandler().handleAmountInput(chatId, text);
            case "AWAITING_CREDIT_ACCOUNT" ->
                    facadeCreditHandlers.getNewCreditHandler().handleAccountInput(chatId, text);
            case "AWAITING_CREDIT_AMOUNT" -> facadeCreditHandlers.getNewCreditHandler().handleAmountInput(chatId, text);
            case "AWAITING_DEPOSIT_AUTOPROLONGATION" ->
                    facadeDepositHandlers.getNewDepositHandler().handleAutoprolongationInput(chatId, text);
            case "AWAITING_CLOSE_DEPOSIT" ->
                    facadeDepositHandlers.getCloseDepositHandler().handleCloseCommand(chatId, text);
            case "AWAITING_CREDITS_CLOSE" ->
                    facadeCreditHandlers.getCloseCreditHandler().handleAmountForCloseCredit(chatId, text);
            case "AWAITING_CREDITS_AMOUNT" ->
                    facadeCreditHandlers.getCloseCreditHandler().handleCloseCredit(chatId, text);
            case "AWAITING_ACCOUNT_CLOSE" ->
                    facadeAccountHandlers.getCloseAccountHandler().handleAccountIdInput(chatId, text);
            case "AWAITING_CREDIT_ID_FOR_PLAN" ->
                    facadeCreditHandlers.getPaymentPlanHandler().handleCreditIdInput(chatId, user, text);
            case "FROM_ACCOUNT_ID" -> facadeTransferHandlers.getTransferHandler().handleAccountIdInput(chatId, text);
            case "TO_ACCOUNT_ID" -> facadeTransferHandlers.getTransferHandler().handleAccountIdToInput(chatId, text);
            case "AMOUNT_TRANSFERRED" -> facadeTransferHandlers.getTransferHandler().handleAmountInput(chatId, text);
            case "AWAITING_MESSAGE" -> facadeTransferHandlers.getTransferHandler().handleMessage(chatId, text);
            case "ACCOUNT_ID_TRANSFER" ->
                    facadeTransferHandlers.getTransferHistoryHandler().handleAccountIdInput(chatId, text);
            case "AWAITING_OPERATION" ->
                    facadeTransferHandlers.getTransferHistoryHandler().handleOperation(chatId, text);
            case "AWAITING_FROM_DATE" ->
                    facadeTransferHandlers.getTransferHistoryHandler().handleDateFrom(chatId);
            case "AWAITING_TO_DATE" ->
                    facadeTransferHandlers.getTransferHistoryHandler().handleDateTo(chatId, user, text);
            case "AWAITING_ACCOUNT_CURRENCY" -> {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText("Пожалуйста, выберите валюту из предложенных кнопок");
                message.setReplyMarkup(facadeAccountHandlers.getCloseAccountHandler().createCurrencyKeyboard());
                yield message;
            }
            default -> createMessage(chatId, "Для завершения нажмите Отменить операцию");
        };
    }

    /**
     * Обрабатывает обычные команды.
     *
     * @param update входящее обновление.
     * @param chatId ID чата.
     * @return Ответное действие бота.
     */

    private BotApiMethod<?> handleRegularCommand(Update update, Long chatId) {
        String text = update.getMessage().getText();
        User user = update.getMessage().getFrom();

        switch (text) {
            case "❌ Отмена операции":
                userStateService.clearUserState(chatId);
                return createMessage(chatId, "Операция отменена");
            case "↩️ Назад":
                userStateService.clearUserState(chatId);
                return createMainMenuMessage(chatId);
            case "🚀 START":
                return startHandler.handleStart(chatId, user);
        }

        try {
            authService.getValidUserToken(chatId, user);
            try {
                return switch (text) {
                    case "📊 Счета" ->
                            createReplyMenuMessage(chatId, "Управление счетами:", replyKeyboardMenu.createAccountsMenu());
                    case "💰 Вклады" ->
                            createReplyMenuMessage(chatId, "Управление вкладами:", replyKeyboardMenu.createDepositsMenu());
                    case "💳 Кредиты" ->
                            createReplyMenuMessage(chatId, "Управление кредитами:", replyKeyboardMenu.createCreditsMenu());
                    case "💸 Переводы" ->
                            createReplyMenuMessage(chatId, "Переводы", replyKeyboardMenu.createTransferMenu());
                    case "Мои счета" -> facadeAccountHandlers.getAccountsHandler().handleAccountsCommand(chatId, user);
                    case "Новый счет" -> facadeAccountHandlers.getNewAccountHandler().handleInitialCommand(chatId);
                    case "Закрыть счет" -> facadeAccountHandlers.getCloseAccountHandler().handleInitialCommand(chatId);
                    case "Мои вклады" -> facadeDepositHandlers.getDepositsHandler().handleDepositsCommand(chatId, user);
                    case "Новый вклад" -> facadeDepositHandlers.getNewDepositHandler().handleInitialCommand(chatId);
                    case "Закрыть вклад" -> facadeDepositHandlers.getCloseDepositHandler().handleInitialCommand(chatId);
                    case "Мои кредиты" -> facadeCreditHandlers.getCreditsHandler().handleCreditsCommand(chatId, user);
                    case "Новый кредит" -> facadeCreditHandlers.getNewCreditHandler().handleInitialCommand(chatId);
                    case "Погасить кредит" -> facadeCreditHandlers.getCloseCreditHandler().handleInitialCommand(chatId);
                    case "График платежей" -> facadeCreditHandlers.getPaymentPlanHandler().handleInitialCommand(chatId);
                    case "📦 Продукты" -> productsHandler.handleInitialCommand(chatId);
                    case "\uD83D\uDCB5 Валюты" -> currenciesHandler.handleCurrenciesCommand(chatId, user);
                    case "Совершить перевод" ->
                            facadeTransferHandlers.getTransferHandler().handleInitialCommand(chatId);
                    case "История переводов" ->
                            facadeTransferHandlers.getTransferHistoryHandler().handleInitialCommand(chatId);
                    default -> createMainMenuMessage(chatId);
                };
            } catch (TokenExpiredException e) {
                return commonUtilsService.createMessage(chatId, "Сессия истекла. Пожалуйста, авторизуйтесь заново");
            }
        } catch (Exception e) {
            return commonUtilsService.createMessage(chatId, "Ошибка авторизации, авторизуйтесь нажав на кнопку START");
        }
    }

    /**
     * Создает сообщение с клавиатурой меню.
     *
     * @param chatId ID чата.
     * @param text текст сообщения.
     * @param keyboard клавиатура.
     * @return Сообщение с клавиатурой.
     */
    private SendMessage createReplyMenuMessage(Long chatId, String text, ReplyKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setReplyMarkup(keyboard);
        return message;
    }

    /**
     * Создает сообщение с главным меню.
     *
     * @param chatId ID чата.
     * @return Сообщение с главным меню.
     */
    private SendMessage createMainMenuMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Главное меню");
        message.setReplyMarkup(replyKeyboardMenu.createMainReplyKeyboard());
        return message;
    }

    /**
     * Создает простое текстовое сообщение.
     *
     * @param chatId ID чата.
     * @param text текст сообщения.
     * @return Текстовое сообщение.
     */
    private SendMessage createMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        return message;
    }
}
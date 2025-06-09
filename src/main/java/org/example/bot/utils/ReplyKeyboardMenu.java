package org.example.bot.utils;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для создания клавиатур меню.
 */
@Service
public class ReplyKeyboardMenu {

    /**
     * Создает главное меню.
     *
     * @return Клавиатура главного меню.
     */
    public ReplyKeyboardMarkup createMainReplyKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("📊 Счета"));
        row1.add(new KeyboardButton("💰 Вклады"));
        row1.add(new KeyboardButton("💳 Кредиты"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("\uD83D\uDCB5 Валюты"));
        row2.add(new KeyboardButton("📦 Продукты"));
        row2.add(new KeyboardButton("💸 Переводы"));

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("\uD83D\uDE80 START"));

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }


    /**
     * Создает меню для работы со счетами.
     *
     * @return Клавиатура меню счетов.
     */
    public ReplyKeyboardMarkup createAccountsMenu() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Мои счета"));
        row1.add(new KeyboardButton("Новый счет"));
        row1.add(new KeyboardButton("Закрыть счет"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("↩️ Назад"));
        row2.add(new KeyboardButton("❌ Отмена операции"));

        keyboard.add(row1);
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }


    /**
     * Создает меню для работы с вкладами.
     *
     * @return Клавиатура меню вкладов.
     */
    public ReplyKeyboardMarkup createDepositsMenu() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Мои вклады"));
        row1.add(new KeyboardButton("Новый вклад"));
        row1.add(new KeyboardButton("Закрыть вклад"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("↩️ Назад"));
        row2.add(new KeyboardButton("❌ Отмена операции"));

        keyboard.add(row1);
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    /**
     * Создает меню для работы с кредитами.
     *
     * @return Клавиатура меню кредитов.
     */
    public ReplyKeyboardMarkup createCreditsMenu() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Мои кредиты"));
        row1.add(new KeyboardButton("Новый кредит"));
        row1.add(new KeyboardButton("Погасить кредит"));
        row1.add(new KeyboardButton("График платежей"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("↩️ Назад"));
        row2.add(new KeyboardButton("❌ Отмена операции"));

        keyboard.add(row1);
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    /**
     * Создает меню для работы с переводами.
     *
     * @return Клавиатура меню переводов.
     */
    public ReplyKeyboardMarkup createTransferMenu() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Совершить перевод"));
        row1.add(new KeyboardButton("История переводов"));


        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("↩️ Назад"));
        row2.add(new KeyboardButton("❌ Отмена операции"));
        keyboard.add(row1);
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
}
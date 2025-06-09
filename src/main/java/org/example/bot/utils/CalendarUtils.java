package org.example.bot.utils;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Утилиты для работы с календарем в Telegram боте.
 */
public class CalendarUtils {
    private static final DateTimeFormatter MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /**
     * Создает календарь для выбора даты.
     *
     * @param date базовая дата для отображения календаря
     * @return InlineKeyboardMarkup с календарем
     */
    public static InlineKeyboardMarkup createCalendar(LocalDate date) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        LocalDate prevMonth = date.minusMonths(1);
        LocalDate nextMonth = date.plusMonths(1);

        List<InlineKeyboardButton> headerRow = new ArrayList<>();
        headerRow.add(createButton("◀", "prev_month_" + prevMonth.getMonthValue() + "_" + prevMonth.getYear()));
        headerRow.add(createButton(date.format(MONTH_YEAR_FORMAT), "ignore"));
        headerRow.add(createButton("▶", "next_month_" + nextMonth.getMonthValue() + "_" + nextMonth.getYear()));
        rows.add(headerRow);

        List<InlineKeyboardButton> daysOfWeekRow = new ArrayList<>();
        String[] days = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        for (String day : days) {
            daysOfWeekRow.add(createButton(day, "ignore"));
        }
        rows.add(daysOfWeekRow);

        YearMonth yearMonth = YearMonth.from(date);
        LocalDate firstOfMonth = yearMonth.atDay(1);
        int dayOfWeek = firstOfMonth.getDayOfWeek().getValue() - 1; // Пн=0, Вс=6
        int daysInMonth = yearMonth.lengthOfMonth();

        int day = 1;
        for (int week = 0; week < 6; week++) {
            List<InlineKeyboardButton> weekRow = new ArrayList<>();
            for (int d = 0; d < 7; d++) {
                if ((week == 0 && d < dayOfWeek) || day > daysInMonth) {
                    weekRow.add(createButton(" ", "ignore"));
                } else {
                    LocalDate currentDate = LocalDate.of(date.getYear(), date.getMonth(), day);
                    weekRow.add(createButton(
                            String.valueOf(day),
                            "date_" + currentDate.format(DATE_FORMAT)
                    ));
                    day++;
                }
            }
            rows.add(weekRow);
        }

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
    private static InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
}
package ru.urfu.echobot.service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;

/// Собирает сообщения и клавиатуры для диалогов владельца.
final class OwnerMessaging {
    /// Подпись кнопки возврата.
    static final String BACK_TEXT = "Назад";
    /// Callback возврата владельца.
    static final String BACK_CALLBACK = "owner:back";
    /// Сообщение о недоступном действии.
    static final String UNAVAILABLE = "Действие недоступно.";
    /// Максимальное число кнопок в строке.
    private static final int BUTTONS_PER_ROW = 6;
    /// Максимальное число кнопок срока бронирования в строке.
    private static final int LEAD_BUTTONS_PER_ROW = 8;
    /// Максимальный срок бронирования в часах.
    private static final int MAX_LEAD_HOURS = 48;

    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards = new BookingKeyboardFactory();

    /// Отправляет сообщение с клавиатурой.
    ///
    /// @param bot      бот для отправки
    /// @param chatId   идентификатор чата
    /// @param text     текст сообщения
    /// @param keyboard строки кнопок
    void send(Bot bot, long chatId, String text, List<List<Button>> keyboard) {
        bot.sendMessage(new Message(text, null, keyboard), chatId);
    }

    /// Отправляет сообщение без клавиатуры.
    ///
    /// @param bot    бот для отправки
    /// @param chatId идентификатор чата
    /// @param text   текст сообщения
    void send(Bot bot, long chatId, String text) {
        send(bot, chatId, text, List.of());
    }

    /// Создаёт клавиатуру с кнопкой возврата.
    ///
    /// @return клавиатура возврата
    List<List<Button>> backKeyboard() {
        return choices(button(BACK_TEXT, BACK_CALLBACK));
    }

    /// Создаёт клавиатуру из отдельных кнопок.
    ///
    /// @param buttons кнопки клавиатуры
    /// @return строки клавиатуры
    List<List<Button>> choices(Button... buttons) {
        return keyboards.choices(buttons);
    }

    /// Создаёт кнопку.
    ///
    /// @param text     подпись кнопки
    /// @param callback callback-данные
    /// @return кнопка
    Button button(String text, String callback) {
        return keyboards.button(text, callback);
    }

    /// Добавляет кнопку в строку клавиатуры.
    ///
    /// @param rows   строки клавиатуры
    /// @param button кнопка
    /// @param size   максимальное число кнопок в строке
    void addButton(List<List<Button>> rows, Button button, int size) {
        keyboards.addButton(rows, button, size);
    }

    /// Возвращает строку клавиатуры.
    ///
    /// @param buttons кнопки строки
    /// @return строка кнопок
    List<Button> row(Button... buttons) {
        return keyboards.row(buttons);
    }

    /// Возвращает сокращённое название дня недели.
    ///
    /// @param day день недели
    /// @return сокращение на русском
    String dayName(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Пн";
            case TUESDAY -> "Вт";
            case WEDNESDAY -> "Ср";
            case THURSDAY -> "Чт";
            case FRIDAY -> "Пт";
            case SATURDAY -> "Сб";
            case SUNDAY -> "Вс";
        };
    }

    /// Показывает кнопки выбора срока бронирования.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void showLeadHours(long userId, Bot bot) {
        List<List<Button>> rows = showLeadRows();
        send(bot, userId, "За сколько часов до начала клиент ещё может бронировать?", rows);
    }

    /// Создаёт строки кнопок срока бронирования.
    ///
    /// @return клавиатура срока бронирования
    List<List<Button>> showLeadRows() {
        List<List<Button>> rows = new ArrayList<>();
        for (int hour = 1; hour <= MAX_LEAD_HOURS; hour++) {
            addButton(rows, button(String.valueOf(hour), "owner:lead:" + hour), LEAD_BUTTONS_PER_ROW);
        }
        rows.add(row(button(BACK_TEXT, BACK_CALLBACK)));
        return rows;
    }

    /// Возвращает число кнопок в строке часов.
    ///
    /// @return максимальное число кнопок
    int buttonsPerRow() {
        return BUTTONS_PER_ROW;
    }
}

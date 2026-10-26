package ru.urfu.echobot.service;

import ru.urfu.echobot.service.Button;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// Создаёт клавиатуры и кнопки, используемые в диалогах бронирования.
public final class BookingKeyboardFactory {
    /// Подпись кнопки возврата на предыдущий шаг.
    private static final String BACK = "Назад";
    /// Код callback для возврата на предыдущий шаг.
    private static final String CALLBACK_BACK = "back";

    /// Создаёт клавиатуру, размещая каждую кнопку в отдельной строке.
    ///
    /// @param buttons кнопки клавиатуры
    /// @return строки с одной кнопкой в каждой
    public List<List<Button>> choices(Button... buttons) {
        List<List<Button>> rows = new ArrayList<>();
        for (Button button : buttons) {
            rows.add(row(button));
        }
        return markup(rows);
    }

    /// Создаёт клавиатуру комментария с вариантами пропуска и возврата.
    ///
    /// @return клавиатура комментария
    public List<List<Button>> commentKeyboard() {
        return choices(button("Пропустить", "book:skip"), button(BACK, CALLBACK_BACK));
    }

    /// Создаёт клавиатуру с кнопкой возврата.
    ///
    /// @return клавиатура возврата
    public List<List<Button>> backKeyboard() {
        return choices(button(BACK, CALLBACK_BACK));
    }

    /// Создаёт кнопку с подписью и callback-данными.
    ///
    /// @param text подпись кнопки
    /// @param callback callback-данные кнопки
    /// @return созданная кнопка
    public Button button(String text, String callback) {
        return new Button(text, callback);
    }

    /// Создаёт строку клавиатуры из переданных кнопок.
    ///
    /// @param buttons кнопки строки
    /// @return строка клавиатуры
    public List<Button> row(Button... buttons) {
        return new ArrayList<>(Arrays.asList(buttons));
    }

    /// Добавляет кнопку в последнюю строку или создаёт для неё новую.
    ///
    /// @param rows строки клавиатуры
    /// @param button добавляемая кнопка
    /// @param size максимальное число кнопок в строке
    public void addButton(List<List<Button>> rows, Button button, int size) {
        if (rows.isEmpty() || rows.get(rows.size() - 1).size() >= size) {
            rows.add(new ArrayList<>());
        }
        rows.get(rows.size() - 1).add(button);
    }

    /// Возвращает строки кнопок в виде клавиатуры.
    ///
    /// @param rows строки кнопок
    /// @return клавиатура для сообщения
    public List<List<Button>> markup(List<List<Button>> rows) {
        return rows;
    }
}

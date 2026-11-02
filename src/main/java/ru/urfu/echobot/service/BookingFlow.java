package ru.urfu.echobot.service;

import java.time.LocalDate;
import java.time.YearMonth;

/// Хранит шаг и введённые данные одного пользовательского диалога бронирования.
final class BookingFlow {
    /// Шаги диалога бронирования.
    enum Step {
        MENU, BOOK_BASES, BOOK_ROOMS, BOOK_MONTH, BOOK_WEEK, BOOK_DAY, BOOK_TIME, BOOK_DURATION,
        BOOK_NAME, BOOK_PHONE, BOOK_COMMENT, BOOK_CONFIRM
    }

    /// Текущий шаг диалога.
    Step step = Step.MENU;
    /// Идентификатор выбранной базы.
    long baseId;
    /// Идентификатор выбранного зала.
    long roomId;
    /// Имя клиента.
    String name;
    /// Нормализованный номер телефона клиента.
    String phone;
    /// Комментарий к бронированию.
    String comment;
    /// Выбранный месяц.
    YearMonth selectedMonth;
    /// Первый день выбранной недели.
    LocalDate selectedWeek;
    /// Выбранная дата бронирования.
    LocalDate bookingDate;
    /// Час начала бронирования.
    int startHour;
    /// Длительность бронирования в часах.
    int hours;
    /// Смещение страницы списка месяцев.
    int monthOffset;

    /// Создаёт состояние диалога, установленное на главное меню.
    BookingFlow() {
    }

    /// Сбрасывает параметры бронирования, сохраняя текущий шаг диалога.
    void clearBooking() {
        baseId = 0;
        roomId = 0;
        bookingDate = null;
        startHour = 0;
        hours = 0;
        name = null;
        phone = null;
        comment = null;
        selectedMonth = null;
        selectedWeek = null;
        monthOffset = 0;
    }
}

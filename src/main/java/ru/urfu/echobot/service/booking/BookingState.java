package ru.urfu.echobot.service.booking;

/// Шаги диалога бронирования.
public enum BookingState {
    /// Главное меню.
    MENU,
    /// Выбор базы.
    BOOK_BASES,
    /// Выбор зала.
    BOOK_ROOMS,
    /// Выбор месяца.
    BOOK_MONTH,
    /// Выбор недели.
    BOOK_WEEK,
    /// Выбор дня.
    BOOK_DAY,
    /// Выбор времени начала.
    BOOK_TIME,
    /// Выбор длительности.
    BOOK_DURATION,
    /// Ввод имени клиента.
    BOOK_NAME,
    /// Ввод телефона клиента.
    BOOK_PHONE,
    /// Ввод комментария.
    BOOK_COMMENT,
    /// Подтверждение бронирования.
    BOOK_CONFIRM
}

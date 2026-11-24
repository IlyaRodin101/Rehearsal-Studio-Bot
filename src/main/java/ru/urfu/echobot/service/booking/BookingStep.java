package ru.urfu.echobot.service.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import ru.urfu.echobot.service.State;

/// Хранит шаг и введённые данные одного пользовательского диалога бронирования.
final class BookingStep implements State<BookingState> {
    /// время последней активности пользователя
    private LocalDateTime lastActivityAt;
    /// Текущий шаг диалога.
    BookingState step = BookingState.MENU;
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
    BookingStep() {
        lastActivityAt = LocalDateTime.now();
    }

    /// Возвращает текущий шаг диалога.
    ///
    /// @return текущий шаг
    @Override
    public BookingState state() {
        return step;
    }

    /// Устанавливает текущий шаг диалога.
    ///
    /// @param value новый шаг
    @Override
    public void state(BookingState value) {
        step = value;
    }

    /// Обновляет время последней активности пользователя
    void updateLastActivity() {

        lastActivityAt = LocalDateTime.now();
    }

    /// Геттер последней активности пользователя
    LocalDateTime getLastActivityAt() {

        return lastActivityAt;
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

    /// Начинает бронирование выбранного «горячего» слота с шага длительности.
    ///
    /// @param baseId база слота
    /// @param roomId зал слота
    /// @param start  время начала слота
    void startHotSlot(long baseId, long roomId, LocalDateTime start) {
        clearBooking();
        this.baseId = baseId;
        this.roomId = roomId;
        bookingDate = start.toLocalDate();
        startHour = start.getHour();
        selectedMonth = YearMonth.from(start);
        selectedWeek = start.toLocalDate();
        step = BookingState.BOOK_DURATION;
    }
}

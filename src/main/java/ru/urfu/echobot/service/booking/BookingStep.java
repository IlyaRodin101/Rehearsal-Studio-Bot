package ru.urfu.echobot.service.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import ru.urfu.echobot.service.GenericStateMachine;

/// Хранит шаг и введённые данные одного пользовательского диалога бронирования.
final class BookingStep implements GenericStateMachine.State<BookingStep.Step> {
    /// Шаги диалога бронирования.
    enum Step {
        MENU, BOOK_BASES, BOOK_ROOMS, BOOK_MONTH, BOOK_WEEK, BOOK_DAY, BOOK_TIME, BOOK_DURATION,
        BOOK_NAME, BOOK_PHONE, BOOK_COMMENT, BOOK_CONFIRM
    }

    /// События, вызывающие переход между шагами бронирования.
    enum Event {
        /// Клиент открыл сценарий бронирования.
        BEGIN_BOOKING,
        /// Клиент выбрал базу.
        SELECT_BASE,
        /// Клиент выбрал зал.
        SELECT_ROOM,
        /// Клиент выбрал месяц.
        SELECT_MONTH,
        /// Клиент переключил страницу месяцев.
        CHANGE_MONTH_PAGE,
        /// Клиент выбрал неделю.
        SELECT_WEEK,
        /// Клиент выбрал день.
        SELECT_DAY,
        /// Клиент выбрал время начала.
        SELECT_TIME,
        /// Клиент выбрал длительность.
        SELECT_DURATION,
        /// Клиент ввёл имя.
        ENTER_NAME,
        /// Клиент ввёл корректный телефон.
        VALID_PHONE,
        /// Клиент ввёл комментарий.
        ENTER_COMMENT,
        /// Клиент пропустил необязательный комментарий.
        SKIP_COMMENT,
        /// Клиент подтвердил бронирование.
        CONFIRM_BOOKING,
        /// Клиент вернулся на предыдущий шаг.
        BACK,
        /// Клиент отменил бронирование.
        CANCEL
    }

    /// время последней активности пользователя
    private LocalDateTime lastActivityAt;
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
    BookingStep() {
        lastActivityAt = LocalDateTime.now();
    }

    /// Возвращает текущий шаг диалога.
    ///
    /// @return текущий шаг
    @Override
    public Step state() {
        return step;
    }

    /// Устанавливает текущий шаг диалога.
    ///
    /// @param value новый шаг
    @Override
    public void state(Step value) {
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
}

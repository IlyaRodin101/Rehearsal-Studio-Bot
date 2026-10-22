package ru.urfu.echobot.service;

import org.jetbrains.annotations.NotNull;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.OpeningHours;

import java.time.*;
import java.util.Optional;

/// Правила бронирования: часы работы базы и срок, за который ещё можно бронировать.
/// Текущее время берётся из переданных часов, поэтому правила легко проверять в тестах.
public final class BookingRules {
    /// Часы, по которым определяется текущее время.
    private final Clock clock;

    /// Создаёт правила, работающие по заданным часам.
    ///
    /// @param clock источник текущего времени
    public BookingRules(@NotNull Clock clock) {
        this.clock = clock;
    }

    /// Проверяет, можно ли сейчас забронировать зал базы на указанное время.
    ///
    /// @param base  база, в которой находится зал
    /// @param start время начала по часовому поясу базы
    /// @param hours длительность в часах
    /// @return true, если бронь укладывается в часы работы и создаётся не позже допустимого срока
    public boolean canBook(@NotNull Base base, @NotNull LocalDateTime start, int hours) {
        Optional<OpeningHours> opening = base.hoursOn(start.getDayOfWeek());
        if (opening.isEmpty() || hours < 1) {
            return false;
        }
        boolean insideWorkingHours = start.getHour() >= opening.get().openHour()
                && start.getHour() + hours <= opening.get().closeHour();
        return insideWorkingHours && !start.atZone(zone(base)).isBefore(earliestStart(base));
    }

    /// Возвращает самый ранний момент, на который сейчас ещё можно забронировать.
    ///
    /// @param base база
    /// @return текущее время базы плюс срок бронирования
    public ZonedDateTime earliestStart(@NotNull Base base) {
        return ZonedDateTime.now(clock.withZone(zone(base))).plusHours(base.bookingLeadHours());
    }

    /// Возвращает сегодняшнюю дату по часовому поясу базы.
    ///
    /// @param base база
    /// @return текущая дата в городе базы
    public LocalDate today(@NotNull Base base) {
        return LocalDate.now(clock.withZone(zone(base)));
    }

    /// Проверяет, что бронь ещё не началась.
    ///
    /// @param base    база, в которой находится зал брони
    /// @param booking бронь
    /// @return true, если бронь начинается в будущем
    public boolean isUpcoming(@NotNull Base base, @NotNull Booking booking) {
        return booking.start().atZone(zone(base)).isAfter(ZonedDateTime.now(clock.withZone(zone(base))));
    }

    /// Возвращает часовой пояс базы.
    ///
    /// @param base база
    /// @return часовой пояс
    private ZoneId zone(Base base) {
        return ZoneId.of(base.timezone());
    }
}

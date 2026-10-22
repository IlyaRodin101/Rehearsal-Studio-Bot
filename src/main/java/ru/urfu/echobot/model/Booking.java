package ru.urfu.echobot.model;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;

/// Бронь зала на целое число часов. Время указано по часовому поясу базы.
///
/// @param roomId       идентификатор зала
/// @param userId       идентификатор клиента, создавшего бронь
/// @param start        время начала
/// @param hours        длительность в часах
/// @param customerName имя клиента для связи
/// @param phone        телефон клиента в виде +7XXXXXXXXXX
/// @param comment      комментарий клиента, может быть пустым
public record Booking(long roomId, long userId, @NotNull LocalDateTime start, int hours,
                      @NotNull String customerName, @NotNull String phone, @NotNull String comment) {

    /// Возвращает время окончания брони.
    ///
    /// @return момент, когда бронь заканчивается
    public LocalDateTime end() {
        return start.plusHours(hours);
    }
}

package ru.urfu.echobot.service.booking;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.Room;

/// Зал и его записи на выбранный день.
///
/// @param room     зал базы
/// @param bookings записи зала, отсортированные по времени начала
public record RoomSchedule(@NotNull Room room, @NotNull List<Booking> bookings) {

    /// Копирует список записей, чтобы его нельзя было изменить снаружи.
    public RoomSchedule {
        bookings = List.copyOf(bookings);
    }
}

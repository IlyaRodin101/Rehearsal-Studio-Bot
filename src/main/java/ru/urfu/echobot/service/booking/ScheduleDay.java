package ru.urfu.echobot.service.booking;

import java.time.LocalDate;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import ru.urfu.echobot.model.Base;

/// Расписание одной базы на выбранный день.
///
/// @param base  база
/// @param date  день расписания
/// @param rooms залы с записями; пустые залы не включаются
public record ScheduleDay(@NotNull Base base, @NotNull LocalDate date, @NotNull List<RoomSchedule> rooms) {

    /// Копирует список залов, чтобы его нельзя было изменить снаружи.
    public ScheduleDay {
        rooms = List.copyOf(rooms);
    }

    /// Проверяет, что на день нет ни одной записи.
    ///
    /// @return true, если записей нет
    public boolean isEmpty() {
        return rooms.isEmpty();
    }
}

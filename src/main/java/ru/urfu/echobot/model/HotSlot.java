package ru.urfu.echobot.model;

import java.time.LocalDateTime;
import org.jetbrains.annotations.NotNull;

/// Свободный «горячий» слот зала.
///
/// @param room  зал, в котором освободилось время
/// @param start время начала слота
/// @param hours длительность слота в часах
public record HotSlot(@NotNull Room room, @NotNull LocalDateTime start, int hours) {

    /// Возвращает время окончания слота.
    ///
    /// @return момент, когда слот заканчивается
    public LocalDateTime end() {
        return start.plusHours(hours);
    }
}

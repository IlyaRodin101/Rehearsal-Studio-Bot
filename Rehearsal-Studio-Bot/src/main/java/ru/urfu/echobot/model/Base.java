package ru.urfu.echobot.model;

import java.time.DayOfWeek;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/// Репетиционная база.
///
/// @param id               идентификатор базы
/// @param ownerId          Telegram ID владельца базы
/// @param name             название базы
/// @param address          адрес базы
/// @param timezone         часовой пояс базы, например Europe/Moscow
/// @param bookingLeadHours за сколько часов до начала ещё можно бронировать
/// @param equipment        описание оборудования, по строке на позицию
/// @param openingHours     часы работы по дням недели; дня нет в карте, если база в этот день закрыта
public record Base(long id, long ownerId, @NotNull String name, @NotNull String address, @NotNull String timezone,
                   int bookingLeadHours, @NotNull String equipment,
                   @NotNull Map<DayOfWeek, OpeningHours> openingHours) {

    /// Совместимость с начальными данными и клиентским сценарием Задачи 1.
    public Base(long id, String name, String address, String timezone, int bookingLeadHours, String equipment,
                Map<DayOfWeek, OpeningHours> openingHours) {
        this(id, 0, name, address, timezone, bookingLeadHours, equipment, openingHours);
    }

    /// Копирует часы работы, чтобы базу нельзя было изменить снаружи.
    public Base {
        openingHours = Map.copyOf(openingHours);
    }

    /// Возвращает часы работы базы в указанный день недели.
    ///
    /// @param day день недели
    /// @return часы работы или пустой результат, если в этот день база закрыта
    public Optional<OpeningHours> hoursOn(@NotNull DayOfWeek day) {
        return Optional.ofNullable(openingHours.get(day));
    }
}

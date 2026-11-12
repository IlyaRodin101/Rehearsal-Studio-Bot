package ru.urfu.echobot.model;

import java.time.DayOfWeek;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/// Репетиционная база.
///
/// @param id                    идентификатор базы
/// @param ownerId               Telegram ID владельца базы
/// @param name                  название базы
/// @param address               адрес базы
/// @param timezone              часовой пояс базы, например Europe/Moscow
/// @param bookingLeadHours      за сколько часов до начала ещё можно бронировать
/// @param equipment             описание оборудования, по строке на позицию
/// @param openingHours          часы работы по дням недели; дня нет в карте, если база в этот день закрыта
/// @param freeCancellationHours срок бесплатной отмены брони в часах
/// @param penaltyPercent        размер штрафа за позднюю отмену в процентах
public record Base(long id, long ownerId, @NotNull String name, @NotNull String address, @NotNull String timezone,
                   int bookingLeadHours, @NotNull String equipment,
                   @NotNull Map<DayOfWeek, OpeningHours> openingHours,
                   int freeCancellationHours, int penaltyPercent) {
    /// Срок бесплатной отмены по умолчанию.
    public static final int DEFAULT_FREE_CANCELLATION_HOURS = 48;
    /// Размер штрафа за позднюю отмену по умолчанию.
    public static final int DEFAULT_PENALTY_PERCENT = 100;

    /// Создаёт базу со стандартными правилами отмены.
    ///
    /// @param id               идентификатор базы
    /// @param ownerId          Telegram ID владельца базы
    /// @param name             название базы
    /// @param address          адрес базы
    /// @param timezone         часовой пояс базы
    /// @param bookingLeadHours за сколько часов до начала ещё можно бронировать
    /// @param equipment        описание оборудования
    /// @param openingHours     часы работы по дням недели
    @SuppressWarnings("checkstyle:ParameterNumber")
    public Base(long id, long ownerId, @NotNull String name, @NotNull String address, @NotNull String timezone,
                int bookingLeadHours, @NotNull String equipment,
                @NotNull Map<DayOfWeek, OpeningHours> openingHours) {
        this(id, ownerId, name, address, timezone, bookingLeadHours, equipment, openingHours,
                DEFAULT_FREE_CANCELLATION_HOURS, DEFAULT_PENALTY_PERCENT);
    }

    /// Совместимость с начальными данными и клиентским сценарием Задачи 1.
    ///
    /// @param id               идентификатор базы
    /// @param name             название базы
    /// @param address          адрес базы
    /// @param timezone         часовой пояс базы
    /// @param bookingLeadHours за сколько часов до начала ещё можно бронировать
    /// @param equipment        описание оборудования
    /// @param openingHours     часы работы по дням недели
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

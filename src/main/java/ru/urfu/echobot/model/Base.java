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
/// @param freeCancellationHours срок бесплатной отмены в часах до начала записи
/// @param penaltyPercent        размер неустойки при поздней отмене в процентах от стоимости
public record Base(long id, long ownerId, @NotNull String name, @NotNull String address, @NotNull String timezone,
                   int bookingLeadHours, @NotNull String equipment,
                   @NotNull Map<DayOfWeek, OpeningHours> openingHours, int freeCancellationHours,
                   int penaltyPercent) {

    /// Срок бесплатной отмены по умолчанию.
    public static final int DEFAULT_FREE_CANCELLATION_HOURS = 48;
    /// Размер неустойки по умолчанию.
    public static final int DEFAULT_PENALTY_PERCENT = 100;

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
        this(id, 0, name, address, timezone, bookingLeadHours, equipment, openingHours,
                DEFAULT_FREE_CANCELLATION_HOURS, DEFAULT_PENALTY_PERCENT);
    }

    /// Совместимость с базами, созданными в Задаче 2 до появления правил отмены.
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

    /// Копирует часы работы и проверяет правила отмены.
    public Base {
        openingHours = Map.copyOf(openingHours);
        if (freeCancellationHours < 0) {
            throw new IllegalArgumentException("Срок бесплатной отмены не может быть отрицательным");
        }
        if (penaltyPercent < 0 || penaltyPercent > 100) {
            throw new IllegalArgumentException("Неустойка должна быть от 0 до 100 процентов");
        }
    }

    /// Возвращает часы работы базы в указанный день недели.
    ///
    /// @param day день недели
    /// @return часы работы или пустой результат, если в этот день база закрыта
    public Optional<OpeningHours> hoursOn(@NotNull DayOfWeek day) {
        return Optional.ofNullable(openingHours.get(day));
    }

    /// Возвращает копию базы с новыми правилами отмены.
    ///
    /// @param freeCancellationHours новый срок бесплатной отмены в часах
    /// @param penaltyPercent        новый размер неустойки в процентах
    /// @return база с изменёнными правилами отмены
    public Base withCancellationRules(int freeCancellationHours, int penaltyPercent) {
        return new Base(id, ownerId, name, address, timezone, bookingLeadHours, equipment, openingHours,
                freeCancellationHours, penaltyPercent);
    }
}

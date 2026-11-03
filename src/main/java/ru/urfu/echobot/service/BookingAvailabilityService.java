package ru.urfu.echobot.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Вычисляет доступные для бронирования даты и часы, не формируя сообщения Telegram.
public final class BookingAvailabilityService {
    /// Репозиторий залов.
    private final RoomRepository roomRepository;
    /// Репозиторий бронирований.
    private final BookingRepository bookingRepository;
    /// Правила доступности и минимального срока до брони.
    private final BookingRules rules;

    /// Создаёт сервис проверки доступности времени.
    ///
    /// @param roomRepository хранилище залов
    /// @param bookingRepository хранилище броней
    /// @param rules правила бронирования
    public BookingAvailabilityService(RoomRepository roomRepository, BookingRepository bookingRepository,
                                      BookingRules rules) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.rules = rules;
    }

    /// Проверяет, есть ли свободное время в выбранном месяце.
    ///
    /// @param base база, в которой выполняется поиск
    /// @param roomId идентификатор зала
    /// @param month месяц для проверки
    /// @return true, если в месяце есть доступное время
    public boolean monthHasAvailability(Base base, long roomId, YearMonth month) {
        return rangeHasAvailability(base, roomId, month.atDay(1), month.atEndOfMonth());
    }

    /// Проверяет доступность бронирования хотя бы на один день заданного периода.
    ///
    /// @param base база, в которой выполняется поиск
    /// @param roomId идентификатор зала
    /// @param start первый день периода
    /// @param end последний день периода
    /// @return true, если в периоде есть доступное время
    public boolean rangeHasAvailability(Base base, long roomId, LocalDate start, LocalDate end) {
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (dateHasAvailability(base, roomId, date)) {
                return true;
            }
        }
        return false;
    }

    /// Проверяет, доступен ли хотя бы один час в указанном зале и дне.
    ///
    /// @param base база, в которой находится зал
    /// @param roomId идентификатор зала
    /// @param date проверяемая дата
    /// @return true, если в этот день можно начать бронирование
    public boolean dateHasAvailability(Base base, long roomId, LocalDate date) {
        Room room = roomRepository.findById(roomId).orElse(null);
        return room != null && !availableStarts(base, room, date).isEmpty();
    }

    /// Возвращает свободные часы начала бронирования в заданном зале и дне.
    ///
    /// @param base база, в которой находится зал
    /// @param room зал для проверки
    /// @param date дата бронирования
    /// @return список доступных часов начала
    public List<Integer> availableStarts(Base base, Room room, LocalDate date) {
        OpeningHours hours = base.hoursOn(date.getDayOfWeek()).orElse(null);
        if (hours == null) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>();
        ZonedDateTime earliest = rules.earliestStart(base);
        for (int hour = hours.openHour(); hour < hours.closeHour(); hour++) {
            LocalDateTime start = date.atTime(hour, 0);
            if (start.atZone(ZoneId.of(base.timezone())).isBefore(earliest)) {
                continue;
            }
            if (!bookingRepository.hasConflict(room.id(), start, start.plusHours(1))) {
                result.add(hour);
            }
        }
        return result;
    }
}

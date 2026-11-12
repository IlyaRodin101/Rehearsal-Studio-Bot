package ru.urfu.echobot.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.HotSlot;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Вычисляет свободные «горячие» слоты баз по правилам бронирования.
public final class HotSlotService {
    /// Длительность одного предлагаемого слота в часах.
    private static final int SLOT_HOURS = 1;

    /// Хранилище залов.
    private final RoomRepository roomRepository;
    /// Хранилище броней.
    private final BookingRepository bookingRepository;
    /// Правила бронирования.
    private final BookingRules rules;

    /// Создаёт сервис расчёта «горячих» слотов.
    ///
    /// @param roomRepository    хранилище залов
    /// @param bookingRepository хранилище броней
    /// @param rules             правила бронирования
    public HotSlotService(RoomRepository roomRepository, BookingRepository bookingRepository,
                          BookingRules rules) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.rules = rules;
    }

    /// Возвращает свободные «горячие» слоты всех залов базы.
    ///
    /// @param base база, для которой выполняется поиск
    /// @return слоты в порядке времени начала
    public List<HotSlot> findHotSlots(Base base) {
        LocalDate today = rules.today(base);
        LocalDate lastDate = rules.freeCancellationDeadline(base).toLocalDate();
        List<HotSlot> slots = new ArrayList<>();
        for (Room room : roomRepository.findByBaseId(base.id())) {
            collectRoomSlots(base, room, today, lastDate, slots);
        }
        slots.sort(Comparator.comparing(HotSlot::start).thenComparing(slot -> slot.room().name()));
        return slots;
    }

    /// Проверяет, что конкретное время зала остаётся свободным «горячим» слотом.
    ///
    /// @param base  база, в которой находится зал
    /// @param room  проверяемый зал
    /// @param start время начала
    /// @return true, если слот ещё можно предложить клиенту
    public boolean isAvailable(Base base, Room room, LocalDateTime start) {
        return room.baseId() == base.id() && room.active()
                && rules.canBook(base, start, SLOT_HOURS)
                && rules.isHotSlot(base, start)
                && !overlaps(bookingRepository.findByRoom(room.id()), start, start.plusHours(SLOT_HOURS));
    }

    /// Добавляет свободные слоты одного зала.
    ///
    /// @param base     база
    /// @param room     зал
    /// @param firstDay первый проверяемый день
    /// @param lastDay  последний проверяемый день
    /// @param slots    список, в который добавляются слоты
    private void collectRoomSlots(Base base, Room room, LocalDate firstDay, LocalDate lastDay,
                                  List<HotSlot> slots) {
        List<Booking> bookings = bookingRepository.findByRoom(room.id());
        for (LocalDate date = firstDay; !date.isAfter(lastDay); date = date.plusDays(1)) {
            OpeningHours hours = base.hoursOn(date.getDayOfWeek()).orElse(null);
            if (hours != null) {
                for (int hour = hours.openHour(); hour < hours.closeHour(); hour++) {
                    addSlot(base, room, date.atTime(hour, 0), bookings, slots);
                }
            }
        }
    }

    /// Добавляет слот, если время свободно и попадает в срок бесплатной отмены.
    ///
    /// @param base     база
    /// @param room     зал
    /// @param start    время начала
    /// @param bookings брони зала
    /// @param slots    список, в который добавляется слот
    private void addSlot(Base base, Room room, LocalDateTime start, List<Booking> bookings,
                         List<HotSlot> slots) {
        LocalDateTime end = start.plusHours(SLOT_HOURS);
        if (rules.canBook(base, start, SLOT_HOURS) && rules.isHotSlot(base, start)
                && !overlaps(bookings, start, end)) {
            slots.add(new HotSlot(room, start, SLOT_HOURS));
        }
    }

    /// Проверяет пересечение промежутка с бронями.
    ///
    /// @param bookings брони зала
    /// @param start    начало промежутка
    /// @param end      конец промежутка
    /// @return true, если хотя бы одна бронь пересекается с промежутком
    private boolean overlaps(List<Booking> bookings, LocalDateTime start, LocalDateTime end) {
        for (Booking booking : bookings) {
            if (booking.start().isBefore(end) && booking.end().isAfter(start)) {
                return true;
            }
        }
        return false;
    }
}

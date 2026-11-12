package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.HotSlot;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.SqliteBaseRepository;
import ru.urfu.echobot.repository.SqliteBookingRepository;
import ru.urfu.echobot.repository.SqliteDatabase;
import ru.urfu.echobot.repository.SqliteRoomRepository;

/// Тесты расчёта «горячих» слотов. Сейчас вторник, 20 октября 2026, 10:00 по Москве.
final class HotSlotServiceTest {
    /// Фиксированные часы: 10:00 по Москве.
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"), ZoneOffset.UTC);

    @TempDir
    private Path temporaryDirectory;
    private SqliteBaseRepository bases;
    private SqliteBookingRepository bookings;
    private SqliteRoomRepository rooms;
    private HotSlotService service;

    /// Создаёт репозитории и сервис расчёта слотов.
    @BeforeEach
    void setUp() {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("bookings.db").toString());
        bases = new SqliteBaseRepository(database);
        bookings = new SqliteBookingRepository(database);
        rooms = new SqliteRoomRepository(database);
        service = new HotSlotService(rooms, bookings, new BookingRules(clock));
    }

    /// Given: свободное время раньше срока бесплатной отмены. When: ищем слоты. Then: слот попадает в список.
    @Test
    void freeSlotBeforeCancellationDeadlineIsHot() {
        Base base = createBase(48);
        rooms.save(new Room(0, base.id(), "Зал 1", false));

        List<HotSlot> slots = service.findHotSlots(base);

        assertTrue(slots.stream().anyMatch(slot -> slot.start().equals(LocalDateTime.of(2026, 10, 21, 12, 0))));
    }

    /// Given: свободное время позже срока бесплатной отмены. When: ищем слоты. Then: его нет в списке.
    @Test
    void freeSlotAfterCancellationDeadlineIsNotHot() {
        Base base = createBase(24);
        rooms.save(new Room(0, base.id(), "Зал 1", false));

        List<HotSlot> slots = service.findHotSlots(base);

        LocalDateTime deadline = LocalDateTime.of(2026, 10, 21, 10, 0);
        assertTrue(slots.stream().allMatch(slot -> slot.start().isBefore(deadline)));
        assertFalse(slots.stream().anyMatch(slot -> slot.start().equals(deadline)));
    }

    /// Given: занятое время и время вне графика. When: ищем слоты. Then: такие часы не предлагаются.
    @Test
    void occupiedAndOutOfScheduleSlotsAreNotHot() {
        Base base = createBase(48);
        Room room = rooms.save(new Room(0, base.id(), "Зал 1", false));
        bookings.addIfFree(new Booking(room.id(), 0, LocalDateTime.of(2026, 10, 21, 18, 0), 2,
                "Служебная запись", "+70000000000", ""));

        List<HotSlot> slots = service.findHotSlots(base);

        assertFalse(slots.stream().anyMatch(slot -> slot.start().equals(LocalDateTime.of(2026, 10, 21, 18, 0))));
        assertFalse(slots.stream().anyMatch(slot -> slot.start().equals(LocalDateTime.of(2026, 10, 21, 19, 0))));
        assertTrue(slots.stream().anyMatch(slot -> slot.start().equals(LocalDateTime.of(2026, 10, 21, 20, 0))));
        assertFalse(slots.stream().anyMatch(slot -> slot.start().getHour() == 8));
        assertFalse(slots.stream().anyMatch(slot -> slot.start().getHour() == 24));
    }

    /// Создаёт базу с графиком каждый день с 9:00 до 24:00.
    ///
    /// @param freeCancellationHours срок бесплатной отмены
    /// @return сохранённая база
    private Base createBase(int freeCancellationHours) {
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            schedule.put(day, new OpeningHours(9, 24));
        }
        return bases.save(new Base(0, 0, "Центр", "Ленина, 1", "Europe/Moscow", 2, "",
                schedule, freeCancellationHours, 100));
    }
}

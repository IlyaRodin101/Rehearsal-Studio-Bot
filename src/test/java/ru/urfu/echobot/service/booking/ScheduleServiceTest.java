package ru.urfu.echobot.service.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.SqliteBaseRepository;
import ru.urfu.echobot.repository.SqliteBookingRepository;
import ru.urfu.echobot.repository.SqliteDatabase;
import ru.urfu.echobot.repository.SqliteRoomRepository;

/// Тесты сборки данных расписания.
final class ScheduleServiceTest {
    /// Владелец тестовой базы.
    private static final long OWNER = 50L;
    /// Клиент тестовых записей.
    private static final long CLIENT = 42L;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"), ZoneOffset.UTC);
    @TempDir
    private Path temporaryDirectory;
    private SqliteBookingRepository bookings;
    private ScheduleService service;
    private Base base;
    private Room firstRoom;
    private Room secondRoom;

    /// Создаёт базу, залы и сервис расписания.
    @BeforeEach
    void setUp() {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("bookings.db").toString());
        SqliteBaseRepository bases = new SqliteBaseRepository(database);
        SqliteRoomRepository rooms = new SqliteRoomRepository(database);
        bookings = new SqliteBookingRepository(database);
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            schedule.put(day, new OpeningHours(9, 24));
        }
        base = bases.save(new Base(0, OWNER, "Центр", "Ленина, 1", "Europe/Moscow", 2, "",
                schedule, 48, 100));
        firstRoom = rooms.save(new Room(0, base.id(), "Зал 1", false));
        secondRoom = rooms.save(new Room(0, base.id(), "Зал 2", false));
        service = new ScheduleService(bases, rooms, bookings, new BookingRules(clock));
    }

    /// Месяцы начинаются с текущего месяца базы и содержат шесть значений.
    @Test
    void monthsStartFromCurrentBaseMonthAndContainSixMonths() {
        List<YearMonth> months = service.months(base);

        assertEquals(6, months.size());
        assertEquals(YearMonth.of(2026, 10), months.getFirst());
        assertEquals(YearMonth.of(2027, 3), months.getLast());
    }

    /// Недели возвращаются понедельниками выбранного месяца.
    @Test
    void weekStartsReturnMondaysInsideMonth() {
        List<LocalDate> starts = service.weekStarts(base, YearMonth.of(2026, 10));

        assertEquals(List.of(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26)), starts);
        assertTrue(starts.stream().allMatch(date -> date.getDayOfWeek() == DayOfWeek.MONDAY));
        assertTrue(starts.stream().allMatch(date -> YearMonth.from(date).equals(YearMonth.of(2026, 10))));
    }

    /// Дни недели идут с понедельника по воскресенье.
    @Test
    void daysReturnSevenDatesFromMondayToSunday() {
        List<LocalDate> days = service.days(LocalDate.of(2026, 10, 5));

        assertEquals(7, days.size());
        assertEquals(LocalDate.of(2026, 10, 5), days.getFirst());
        assertEquals(LocalDate.of(2026, 10, 11), days.getLast());
        assertEquals(DayOfWeek.MONDAY, days.getFirst().getDayOfWeek());
        assertEquals(DayOfWeek.SUNDAY, days.getLast().getDayOfWeek());
    }

    /// День группирует записи по залам, сортирует их и пропускает пустые залы.
    @Test
    void dayGroupsBookingsByRoomsSortedAndSkipsEmptyRooms() {
        bookings.addIfFree(booking(firstRoom.id(), LocalDateTime.of(2026, 10, 21, 18, 0)));
        bookings.addIfFree(booking(firstRoom.id(), LocalDateTime.of(2026, 10, 21, 12, 0)));
        bookings.addIfFree(booking(secondRoom.id(), LocalDateTime.of(2026, 10, 22, 10, 0)));

        ScheduleDay day = service.day(base, LocalDate.of(2026, 10, 21));

        assertFalse(day.isEmpty());
        assertEquals(1, day.rooms().size());
        RoomSchedule roomSchedule = day.rooms().getFirst();
        assertEquals(firstRoom, roomSchedule.room());
        assertEquals(List.of(LocalDateTime.of(2026, 10, 21, 12, 0),
                LocalDateTime.of(2026, 10, 21, 18, 0)),
                roomSchedule.bookings().stream().map(Booking::start).toList());
    }

    /// День без записей возвращается пустым.
    @Test
    void emptyDayHasNoRooms() {
        ScheduleDay day = service.day(base, LocalDate.of(2026, 10, 23));

        assertTrue(day.isEmpty());
        assertTrue(day.rooms().isEmpty());
    }

    /// Создаёт запись на два часа.
    ///
    /// @param roomId идентификатор зала
    /// @param start  время начала
    /// @return новая запись
    private Booking booking(long roomId, LocalDateTime start) {
        return new Booking(roomId, CLIENT, start, 2, "Анна", "+79991234567", "");
    }
}

package ru.urfu.echobot.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;

/// Тесты поиска и удаления брони по залу и времени.
final class SqliteBookingRepositoryTest {
    /// Владелец тестовой базы.
    private static final long OWNER = 101L;
    /// Клиент тестовых записей.
    private static final long CLIENT = 201L;

    private final LocalDateTime start = LocalDateTime.of(2026, 10, 24, 18, 0);
    private final LocalDateTime otherStart = LocalDateTime.of(2026, 10, 24, 20, 0);
    @TempDir
    private Path temporaryDirectory;
    private SqliteBookingRepository bookings;
    private Room firstRoom;
    private Room secondRoom;

    /// Создаёт базу, залы и хранилище броней.
    @BeforeEach
    void setUp() {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("bookings.db").toString());
        SqliteBaseRepository bases = new SqliteBaseRepository(database);
        SqliteRoomRepository rooms = new SqliteRoomRepository(database);
        bookings = new SqliteBookingRepository(database);
        Base base = bases.save(new Base(0, OWNER, "Центр", "Ленина, 1", "Europe/Moscow", 2, "",
                Map.of(DayOfWeek.SATURDAY, new OpeningHours(9, 24))));
        firstRoom = rooms.save(new Room(0, base.id(), "Зал 1", false));
        secondRoom = rooms.save(new Room(0, base.id(), "Зал 2", false));
    }

    /// Поиск находит бронь нужного зала и времени и не находит чужую.
    @Test
    void findByRoomAndStartFindsExactBooking() {
        Booking first = booking(firstRoom.id(), start);
        Booking second = booking(secondRoom.id(), start);
        Booking third = booking(firstRoom.id(), otherStart);
        bookings.addIfFree(first);
        bookings.addIfFree(second);
        bookings.addIfFree(third);

        assertEquals(first, bookings.findByRoomAndStart(firstRoom.id(), start).orElseThrow());
        assertEquals(third, bookings.findByRoomAndStart(firstRoom.id(), otherStart).orElseThrow());
        assertTrue(bookings.findByRoomAndStart(firstRoom.id(), start.plusHours(1)).isEmpty());
    }

    /// Удаление убирает ровно одну бронь и не трогает другие залы и время.
    @Test
    void deleteByRoomAndStartRemovesExactlyOneBooking() {
        bookings.addIfFree(booking(firstRoom.id(), start));
        bookings.addIfFree(booking(secondRoom.id(), start));
        bookings.addIfFree(booking(firstRoom.id(), otherStart));

        assertTrue(bookings.deleteByRoomAndStart(firstRoom.id(), start));

        assertTrue(bookings.findByRoomAndStart(firstRoom.id(), start).isEmpty());
        assertEquals(1, bookings.findByRoom(secondRoom.id()).size());
        assertEquals(1, bookings.findByRoom(firstRoom.id()).size());
        assertEquals(otherStart, bookings.findByRoom(firstRoom.id()).getFirst().start());
    }

    /// Повторное удаление той же брони возвращает false.
    @Test
    void deleteByRoomAndStartReturnsFalseWhenNothingRemoved() {
        bookings.addIfFree(booking(firstRoom.id(), start));

        assertTrue(bookings.deleteByRoomAndStart(firstRoom.id(), start));
        assertFalse(bookings.deleteByRoomAndStart(firstRoom.id(), start));
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

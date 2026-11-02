package ru.urfu.echobot.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;

/// Tests SQLite persistence and atomic booking creation.
final class SqliteRepositoriesTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 24, 18, 0);

    @TempDir
    private Path temporaryDirectory;
    private SqliteDatabase database;
    private SqliteBaseRepository bases;
    private SqliteRoomRepository rooms;
    private SqliteBookingRepository bookings;

    @BeforeEach
    void setUp() {
        database = new SqliteDatabase(temporaryDirectory.resolve("bookings.db").toString());
        bases = new SqliteBaseRepository(database);
        rooms = new SqliteRoomRepository(database);
        bookings = new SqliteBookingRepository(database);
    }

    @Test
    void basesRoomsScheduleAndBookingsSurviveRepositoryRecreation() {
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        schedule.put(DayOfWeek.MONDAY, new OpeningHours(9, 24));
        Base base = bases.save(new Base(0, 101, "Центр", "Ленина, 1", "Europe/Moscow",
                2, "Барабаны", schedule));
        Room room = rooms.save(new Room(0, base.id(), "Зал 1", true));
        assertTrue(bookings.addIfFree(booking(room.id(), 201, START, 2)));

        SqliteDatabase reopened = new SqliteDatabase(temporaryDirectory.resolve("bookings.db").toString());

        assertEquals(base, new SqliteBaseRepository(reopened).findById(base.id()).orElseThrow());
        assertEquals(room, new SqliteRoomRepository(reopened).findById(room.id()).orElseThrow());
        assertEquals(1, new SqliteBookingRepository(reopened).findByUser(201).size());
    }

    @Test
    void deletedRoomPreservesExistingBookingsButIsNotAvailableForNewOnes() {
        Base base = bases.save(new Base(0, 101, "Центр", "Ленина, 1", "Europe/Moscow",
                2, "", Map.of()));
        Room room = rooms.save(new Room(0, base.id(), "Зал 1", false));
        bookings.addIfFree(booking(room.id(), 201, START, 2));
        rooms.deactivate(room.id());

        assertFalse(rooms.findById(room.id()).orElseThrow().active());
        assertTrue(rooms.findByBaseId(base.id()).isEmpty());
        assertEquals(1, bookings.findByUser(201).size());
    }

    @Test
    void simultaneousConflictingBookingsAllowExactlyOneWinner() throws Exception {
        Base base = bases.save(new Base(0, 101, "Центр", "Ленина, 1", "Europe/Moscow",
                2, "", Map.of()));
        Room room = rooms.save(new Room(0, base.id(), "Зал 1", false));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> tryBook(room.id(), 201, ready, start));
            Future<Boolean> second = executor.submit(() -> tryBook(room.id(), 202, ready, start));
            ready.await();
            start.countDown();
            assertNotEquals(first.get(), second.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private Boolean tryBook(long roomId, long userId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return bookings.addIfFree(booking(roomId, userId, START, 2));
    }

    private Booking booking(long roomId, long userId, LocalDateTime start, int hours) {
        return new Booking(roomId, userId, start, hours, "Клиент", "+79990000000", "");
    }
}

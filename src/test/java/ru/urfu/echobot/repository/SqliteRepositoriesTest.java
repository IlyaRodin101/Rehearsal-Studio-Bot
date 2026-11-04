package ru.urfu.echobot.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    /// Booking start time used by persistence tests.
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 24, 18, 0);

    /// Temporary directory that isolates the SQLite database for each test.
    @TempDir
    private Path temporaryDirectory;
    /// SQLite connection factory used in the test.
    private SqliteDatabase database;
    /// Base repository under test.
    private SqliteBaseRepository bases;
    /// Room repository under test.
    private SqliteRoomRepository rooms;
    /// Booking repository under test.
    private SqliteBookingRepository bookings;

    /// Opens an isolated database and initializes all repositories.
    @BeforeEach
    void setUp() {
        database = new SqliteDatabase(temporaryDirectory.resolve("database.db").toString());
        bases = new SqliteBaseRepository(database);
        rooms = new SqliteRoomRepository(database);
        bookings = new SqliteBookingRepository(database);
    }

    /// Verifies persisted data is available after repository recreation.
    @Test
    void basesRoomsScheduleAndBookingsSurviveRepositoryRecreation() {
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        schedule.put(DayOfWeek.MONDAY, new OpeningHours(9, 24));
        Base base = bases.save(new Base(0, 101, "Центр", "Ленина, 1", "Europe/Moscow",
                2, "Барабаны", schedule));
        Room room = rooms.save(new Room(0, base.id(), "Зал 1", true));
        assertTrue(bookings.addIfFree(booking(room.id(), 201, START, 2)));

        SqliteDatabase reopened = new SqliteDatabase(temporaryDirectory.resolve("database.db").toString());

        assertEquals(base, new SqliteBaseRepository(reopened).findById(base.id()).orElseThrow());
        assertEquals(room, new SqliteRoomRepository(reopened).findById(room.id()).orElseThrow());
        assertEquals(1, new SqliteBookingRepository(reopened).findByUser(201).size());
    }

    /// Verifies failed initial-room insertion rolls back the base and schedule.
    @Test
    void failedInitialRoomSaveRollsBackBaseAndSchedule() {
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        schedule.put(DayOfWeek.MONDAY, new OpeningHours(9, 24));
        Base base = new Base(0, 101, "Центр", "Ленина, 1", "Europe/Moscow", 2, "", schedule);

        assertThrows(IllegalStateException.class,
                () -> bases.save(base, List.of(new Room(0, 0, null, false))));

        assertTrue(bases.findAll().isEmpty());
    }

    /// Verifies deactivating a room keeps existing bookings but prevents new ones.
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

    /// Verifies two concurrent conflicting booking attempts have exactly one winner.
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

    /// Attempts a booking after synchronizing with the concurrent test's start signal.
    ///
    /// @param roomId room to book
    /// @param userId user requesting the booking
    /// @param ready latch indicating the worker is ready
    /// @param start latch releasing all workers
    /// @return whether the booking was accepted
    private Boolean tryBook(long roomId, long userId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return bookings.addIfFree(booking(roomId, userId, START, 2));
    }

    /// Creates a booking value used by repository tests.
    ///
    /// @param roomId room to book
    /// @param userId user requesting the booking
    /// @param start booking start time
    /// @param hours booking duration in hours
    /// @return booking request
    private Booking booking(long roomId, long userId, LocalDateTime start, int hours) {
        return new Booking(roomId, userId, start, hours, "Клиент", "+79990000000", "");
    }
}

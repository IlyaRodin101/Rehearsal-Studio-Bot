package ru.urfu.echobot;

import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.SqliteBaseRepository;
import ru.urfu.echobot.repository.SqliteBookingRepository;
import ru.urfu.echobot.repository.SqliteDatabase;
import ru.urfu.echobot.repository.SqliteRoomRepository;

/// Creates persistent SQLite fixtures for tests of booking flows.
public final class TestStudioData {
    /// Internal user ID used to create fixture records.
    private static final long SERVICE_USER_ID = 0;
    /// Time zone shared by fixture bases.
    private static final String TIMEZONE = "Europe/Moscow";
    /// SQLite database backing the fixture repositories.
    private final SqliteDatabase database;
    /// Repository containing fixture bases.
    private final SqliteBaseRepository baseRepository;
    /// Repository containing fixture rooms.
    private final SqliteRoomRepository roomRepository;
    /// Repository containing fixture bookings.
    private final SqliteBookingRepository bookingRepository;
    /// Base with the Center label.
    private final Base center;
    /// Base with the North label.
    private final Base north;
    /// First room of the Center base.
    private final Room centerRoomOne;
    /// Second room of the Center base.
    private final Room centerRoomTwo;
    /// First room of the North base.
    private final Room northRoomOne;
    /// Comment-required room of the North base.
    private final Room northStudio;

    /// Creates two bases and their rooms, including the fixture bookings used in booking tests.
    ///
    /// @param directory isolated temporary directory for the SQLite file
    /// @param clock clock used to determine dates for the fixture bookings
    public TestStudioData(Path directory, Clock clock) {
        database = new SqliteDatabase(directory.resolve("test-database.db").toString());
        baseRepository = new SqliteBaseRepository(database);
        roomRepository = new SqliteRoomRepository(database);
        bookingRepository = new SqliteBookingRepository(database);
        Map<DayOfWeek, OpeningHours> schedule = schedule();
        center = baseRepository.save(new Base(0, SERVICE_USER_ID, "Центр", "ул. Ленина, 1", TIMEZONE,
                2, "Барабаны: акустическая установка", schedule));
        north = baseRepository.save(new Base(0, SERVICE_USER_ID, "Север", "ул. Ленина, 15", TIMEZONE,
                2, "Барабаны, комбоусилители, микрофоны, микшерный пульт", schedule));
        centerRoomOne = roomRepository.save(new Room(0, center.id(), "Зал 1", false));
        centerRoomTwo = roomRepository.save(new Room(0, center.id(), "Зал 2", false));
        northRoomOne = roomRepository.save(new Room(0, north.id(), "Зал 1", false));
        northStudio = roomRepository.save(new Room(0, north.id(), "Студия", true));
        seedBookings(clock);
    }

    /// Returns the SQLite database handle.
    ///
    /// @return database connection factory
    public SqliteDatabase database() {
        return database;
    }

    /// Returns the base repository.
    ///
    /// @return SQLite base repository
    public SqliteBaseRepository baseRepository() {
        return baseRepository;
    }

    /// Returns the room repository.
    ///
    /// @return SQLite room repository
    public SqliteRoomRepository roomRepository() {
        return roomRepository;
    }

    /// Returns the booking repository.
    ///
    /// @return SQLite booking repository
    public SqliteBookingRepository bookingRepository() {
        return bookingRepository;
    }

    /// Returns the Center base.
    ///
    /// @return Center base
    public Base center() {
        return center;
    }

    /// Returns the North base.
    ///
    /// @return North base
    public Base north() {
        return north;
    }

    /// Returns Center room 1.
    ///
    /// @return first Center room
    public Room centerRoomOne() {
        return centerRoomOne;
    }

    /// Returns Center room 2.
    ///
    /// @return second Center room
    public Room centerRoomTwo() {
        return centerRoomTwo;
    }

    /// Returns North room 1.
    ///
    /// @return first North room
    public Room northRoomOne() {
        return northRoomOne;
    }

    /// Returns the North studio.
    ///
    /// @return North studio room
    public Room northStudio() {
        return northStudio;
    }

    /// Creates the common weekly schedule for fixture bases.
    ///
    /// @return Monday-through-Saturday opening hours
    private Map<DayOfWeek, OpeningHours> schedule() {
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day != DayOfWeek.SUNDAY) {
                schedule.put(day, new OpeningHours(9, 24));
            }
        }
        return schedule;
    }

    /// Inserts non-conflicting bookings on upcoming working days.
    ///
    /// @param clock clock used to determine fixture dates
    private void seedBookings(Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(TIMEZONE)));
        LocalDate tomorrow = workDay(today.plusDays(1));
        LocalDate dayAfterTomorrow = workDay(today.plusDays(2));
        bookingRepository.addIfFree(new Booking(centerRoomOne.id(), SERVICE_USER_ID, tomorrow.atTime(18, 0), 2,
                "Служебная запись", "+70000000000", ""));
        bookingRepository.addIfFree(new Booking(northStudio.id(), SERVICE_USER_ID, dayAfterTomorrow.atTime(13, 0), 3,
                "Служебная запись", "+70000000000", "Служебная запись"));
    }

    /// Moves a Sunday date to the following Monday.
    ///
    /// @param date candidate date
    /// @return date that is not Sunday
    private LocalDate workDay(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SUNDAY ? date.plusDays(1) : date;
    }
}

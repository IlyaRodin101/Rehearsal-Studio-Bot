package ru.urfu.echobot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.model.Booking;

/// SQLite repository for bookings. Conflicts are checked and inserted under one writer transaction.
public final class SqliteBookingRepository implements BookingRepository {
    private static final int ROOM_PARAMETER = 1;
    private static final int USER_PARAMETER = 2;
    private static final int START_PARAMETER = 3;
    private static final int END_PARAMETER = 4;
    private static final int HOURS_PARAMETER = 5;
    private static final int CUSTOMER_PARAMETER = 6;
    private static final int PHONE_PARAMETER = 7;
    private static final int COMMENT_PARAMETER = 8;
    private static final int USER_QUERY_PARAMETER = 1;
    private static final int CONFLICT_END_PARAMETER = 2;
    private final SqliteDatabase database;

    public SqliteBookingRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public boolean addIfFree(Booking booking) {
        try (Connection connection = database.open(); StatementControl transaction = new StatementControl(connection)) {
            transaction.beginImmediate();
            if (hasConflict(connection, booking.roomId(), booking.start(), booking.end())) {
                transaction.rollback();
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO bookings(room_id,user_id,start_time,end_time,hours,customer_name,phone,comment) "
                            + "VALUES(?,?,?,?,?,?,?,?)")) {
                statement.setLong(ROOM_PARAMETER, booking.roomId());
                statement.setLong(USER_PARAMETER, booking.userId());
                statement.setString(START_PARAMETER, booking.start().toString());
                statement.setString(END_PARAMETER, booking.end().toString());
                statement.setInt(HOURS_PARAMETER, booking.hours());
                statement.setString(CUSTOMER_PARAMETER, booking.customerName());
                statement.setString(PHONE_PARAMETER, booking.phone());
                statement.setString(COMMENT_PARAMETER, booking.comment());
                statement.executeUpdate();
            }
            transaction.commit();
            return true;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not save booking", exception);
        }
    }

    @Override
    public boolean hasConflict(long roomId, LocalDateTime start, LocalDateTime end) {
        try (Connection connection = database.open()) {
            return hasConflict(connection, roomId, start, end);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not check booking availability", exception);
        }
    }

    @Override
    public List<Booking> findByUser(long userId) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM bookings WHERE user_id=? ORDER BY start_time,id")) {
            statement.setLong(USER_QUERY_PARAMETER, userId);
            try (ResultSet rows = statement.executeQuery()) {
                List<Booking> bookings = new ArrayList<>();
                while (rows.next()) {
                    bookings.add(new Booking(rows.getLong("room_id"), rows.getLong("user_id"),
                            LocalDateTime.parse(rows.getString("start_time")), rows.getInt("hours"),
                            rows.getString("customer_name"), rows.getString("phone"), rows.getString("comment")));
                }
                return bookings;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read bookings", exception);
        }
    }

    private boolean hasConflict(Connection connection, long roomId, LocalDateTime start, LocalDateTime end)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM bookings WHERE room_id=? AND start_time<? AND end_time>? LIMIT 1")) {
            statement.setLong(ROOM_PARAMETER, roomId);
            statement.setString(CONFLICT_END_PARAMETER, end.toString());
            statement.setString(START_PARAMETER, start.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private final class StatementControl implements AutoCloseable {
        private final Connection connection;
        private boolean active;

        StatementControl(Connection connection) {
            this.connection = connection;
        }

        void beginImmediate() throws SQLException {
            try (java.sql.Statement statement = connection.createStatement()) {
                statement.execute("BEGIN IMMEDIATE");
                active = true;
            }
        }

        void commit() throws SQLException {
            try (java.sql.Statement statement = connection.createStatement()) {
                statement.execute("COMMIT");
                active = false;
            }
        }

        void rollback() throws SQLException {
            if (active) {
                try (java.sql.Statement statement = connection.createStatement()) {
                    statement.execute("ROLLBACK");
                    active = false;
                }
            }
        }

        @Override
        public void close() throws SQLException {
            rollback();
        }
    }
}

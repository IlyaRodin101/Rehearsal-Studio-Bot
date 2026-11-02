package ru.urfu.echobot.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.jetbrains.annotations.NotNull;

/// SQLite connection factory and schema initialization for persistent repositories.
public final class SqliteDatabase {
    private final String url;

    public SqliteDatabase(@NotNull String path) {
        this.url = "jdbc:sqlite:" + path;
        initialize();
    }

    Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 10000");
        }
        return connection;
    }

    private void initialize() {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bases (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner_id INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        address TEXT NOT NULL,
                        timezone TEXT NOT NULL,
                        booking_lead_hours INTEGER NOT NULL,
                        equipment TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS opening_hours (
                        base_id INTEGER NOT NULL REFERENCES bases(id),
                        day_of_week INTEGER NOT NULL,
                        open_hour INTEGER NOT NULL,
                        close_hour INTEGER NOT NULL,
                        PRIMARY KEY (base_id, day_of_week)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS rooms (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        base_id INTEGER NOT NULL REFERENCES bases(id),
                        name TEXT NOT NULL,
                        comment_required INTEGER NOT NULL,
                        active INTEGER NOT NULL DEFAULT 1
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bookings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        room_id INTEGER NOT NULL REFERENCES rooms(id),
                        user_id INTEGER NOT NULL,
                        start_time TEXT NOT NULL,
                        end_time TEXT NOT NULL,
                        hours INTEGER NOT NULL,
                        customer_name TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        comment TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS bookings_room_time "
                    + "ON bookings(room_id, start_time, end_time)");
            statement.execute("CREATE INDEX IF NOT EXISTS bookings_user_time ON bookings(user_id, start_time)");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not initialize SQLite database", exception);
        }
    }
}

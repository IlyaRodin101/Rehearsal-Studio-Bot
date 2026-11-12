package ru.urfu.echobot.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.jetbrains.annotations.NotNull;

/// Открывает соединения SQLite и создаёт схему базы данных при первом запуске.
public final class SqliteDatabase {
    /// Имя таблицы баз.
    private static final String BASES_TABLE = "bases";
    /// JDBC-адрес файла базы данных.
    private final String url;

    /// Создаёт подключение к файлу базы данных и инициализирует схему.
    ///
    /// @param path путь к файлу SQLite
    public SqliteDatabase(@NotNull String path) {
        this.url = "jdbc:sqlite:" + path;
        initialize();
    }

    /// Открывает новое соединение и включает необходимые настройки SQLite.
    ///
    /// @return открытое соединение
    /// @throws SQLException если соединение не удалось открыть
    Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 10000");
        }
        return connection;
    }

    /// Создаёт таблицы и индексы, если их ещё нет.
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
                        equipment TEXT NOT NULL,
                        free_cancellation_hours INTEGER NOT NULL DEFAULT 48,
                        penalty_percent INTEGER NOT NULL DEFAULT 100
                    )
                    """);
            addColumnIfMissing(statement, BASES_TABLE, "free_cancellation_hours",
                    "INTEGER NOT NULL DEFAULT 48");
            addColumnIfMissing(statement, BASES_TABLE, "penalty_percent", "INTEGER NOT NULL DEFAULT 100");
            createOpeningHoursTable(statement);
            createRoomsTable(statement);
            createBookingsTable(statement);
            createSubscriptionsTable(statement);
            createIndexes(statement);
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось инициализировать базу данных SQLite", exception);
        }
    }

    /// Создаёт таблицу графика работы.
    ///
    /// @param statement команда выполнения SQL
    /// @throws SQLException если таблица не создана
    private void createOpeningHoursTable(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS opening_hours (
                    base_id INTEGER NOT NULL REFERENCES bases(id),
                    day_of_week INTEGER NOT NULL,
                    open_hour INTEGER NOT NULL,
                    close_hour INTEGER NOT NULL,
                    PRIMARY KEY (base_id, day_of_week)
                )
                """);
    }

    /// Создаёт таблицу залов.
    ///
    /// @param statement команда выполнения SQL
    /// @throws SQLException если таблица не создана
    private void createRoomsTable(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS rooms (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    base_id INTEGER NOT NULL REFERENCES bases(id),
                    name TEXT NOT NULL,
                    comment_required INTEGER NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1
                )
                """);
    }

    /// Создаёт таблицу броней.
    ///
    /// @param statement команда выполнения SQL
    /// @throws SQLException если таблица не создана
    private void createBookingsTable(Statement statement) throws SQLException {
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
    }

    /// Создаёт таблицу подписок на «горячие» слоты баз.
    ///
    /// @param statement команда выполнения SQL
    /// @throws SQLException если таблица не создана
    private void createSubscriptionsTable(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS subscriptions (
                    user_id INTEGER NOT NULL,
                    base_id INTEGER NOT NULL REFERENCES bases(id),
                    PRIMARY KEY (user_id, base_id)
                )
                """);
    }

    /// Добавляет колонку в существующую таблицу, если её ещё нет.
    ///
    /// @param statement  команда выполнения SQL
    /// @param table      имя таблицы
    /// @param column     имя колонки
    /// @param definition тип и значение по умолчанию
    /// @throws SQLException если схему не удалось изменить
    private void addColumnIfMissing(Statement statement, String table, String column, String definition)
            throws SQLException {
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (columns.next()) {
                if (column.equals(columns.getString("name"))) {
                    return;
                }
            }
        }
        statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    /// Создаёт индексы для поиска броней.
    ///
    /// @param statement команда выполнения SQL
    /// @throws SQLException если индекс не создан
    private void createIndexes(Statement statement) throws SQLException {
        statement.execute("CREATE INDEX IF NOT EXISTS bookings_room_time "
                + "ON bookings(room_id, start_time, end_time)");
        statement.execute("CREATE INDEX IF NOT EXISTS bookings_user_time ON bookings(user_id, start_time)");
    }
}

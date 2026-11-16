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
    /// Колонка срока бесплатной отмены.
    private static final String FREE_CANCELLATION_COLUMN = "free_cancellation_hours";
    /// Колонка размера неустойки.
    private static final String PENALTY_COLUMN = "penalty_percent";
    /// Определение колонки срока бесплатной отмены.
    private static final String FREE_CANCELLATION_DEFINITION = "INTEGER NOT NULL DEFAULT 48";
    /// Определение колонки размера неустойки.
    private static final String PENALTY_DEFINITION = "INTEGER NOT NULL DEFAULT 100";
    /// Имя колонки в результате PRAGMA table_info.
    private static final String COLUMN_NAME = "name";

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
            migrateBaseCancellationRules(statement);
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

    /// Добавляет правила отмены в базы, созданные до Задачи 3.
    ///
    /// @param statement команда выполнения SQL
    /// @throws SQLException если миграция не удалась
    private void migrateBaseCancellationRules(Statement statement) throws SQLException {
        ensureColumn(statement, BASES_TABLE, FREE_CANCELLATION_COLUMN, FREE_CANCELLATION_DEFINITION);
        ensureColumn(statement, BASES_TABLE, PENALTY_COLUMN, PENALTY_DEFINITION);
    }

    /// Добавляет колонку, если её ещё нет в таблице.
    ///
    /// @param statement  команда выполнения SQL
    /// @param table      имя таблицы
    /// @param column     имя колонки
    /// @param definition SQL-определение колонки
    /// @throws SQLException если колонку не удалось добавить
    private void ensureColumn(Statement statement, String table, String column, String definition)
            throws SQLException {
        if (!hasColumn(statement, table, column)) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    /// Проверяет наличие колонки в таблице.
    ///
    /// @param statement команда выполнения SQL
    /// @param table     имя таблицы
    /// @param column    имя колонки
    /// @return true, если колонка существует
    /// @throws SQLException если схема не прочитана
    private boolean hasColumn(Statement statement, String table, String column) throws SQLException {
        try (ResultSet rows = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rows.next()) {
                if (column.equals(rows.getString(COLUMN_NAME))) {
                    return true;
                }
            }
        }
        return false;
    }

    /// Создаёт индексы для поиска броней и подписок.
    ///
    /// @param statement команда выполнения SQL
    /// @throws SQLException если индекс не создан
    private void createIndexes(Statement statement) throws SQLException {
        statement.execute("CREATE INDEX IF NOT EXISTS bookings_room_time "
                + "ON bookings(room_id, start_time, end_time)");
        statement.execute("CREATE INDEX IF NOT EXISTS bookings_user_time ON bookings(user_id, start_time)");
        statement.execute("CREATE INDEX IF NOT EXISTS subscriptions_base ON subscriptions(base_id)");
    }
}

package ru.urfu.echobot.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/// Управляет одной транзакцией SQLite и откатывает её при закрытии, если она не была зафиксирована.
final class SqliteTransaction implements AutoCloseable {
    /// Соединение, в рамках которого выполняется транзакция.
    private final Connection connection;
    /// Признак того, что транзакция начата и ещё не завершена.
    private boolean active;

    /// Создаёт управление транзакцией для указанного соединения.
    ///
    /// @param connection открытое соединение с базой данных
    SqliteTransaction(Connection connection) {
        this.connection = connection;
    }

    /// Начинает транзакцию в режиме немедленной блокировки записи.
    ///
    /// @throws SQLException если не удалось выполнить команду начала транзакции
    void beginImmediate() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN IMMEDIATE");
            active = true;
        }
    }

    /// Фиксирует изменения транзакции.
    ///
    /// @throws SQLException если не удалось выполнить команду фиксации
    void commit() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("COMMIT");
            active = false;
        }
    }

    /// Откатывает незавершённую транзакцию.
    ///
    /// @throws SQLException если не удалось выполнить команду отката
    void rollback() throws SQLException {
        if (active) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ROLLBACK");
                active = false;
            }
        }
    }

    /// Откатывает транзакцию, если её ещё не зафиксировали.
    ///
    /// @throws SQLException если не удалось выполнить команду отката
    @Override
    public void close() throws SQLException {
        rollback();
    }
}

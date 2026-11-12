package ru.urfu.echobot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/// Удаляет базу владельца вместе с залами, графиком и бронями.
final class SqliteBaseDeletion {
    /// Запрос удаления броней залов базы.
    private static final String DELETE_BOOKINGS_SQL = "DELETE FROM bookings WHERE room_id IN "
            + "(SELECT id FROM rooms WHERE base_id=?)";
    /// Запрос удаления залов базы.
    private static final String DELETE_ROOMS_SQL = "DELETE FROM rooms WHERE base_id=?";
    /// Запрос удаления подписок на «горячие» слоты базы.
    private static final String DELETE_SUBSCRIPTIONS_SQL = "DELETE FROM subscriptions WHERE base_id=?";
    /// Позиция базы в простых запросах.
    private static final int SINGLE_PARAMETER = 1;
    /// Позиция базы в запросах с условием по владельцу.
    private static final int FIRST_PARAMETER = 1;
    /// Позиция владельца в запросах с условием по владельцу.
    private static final int SECOND_PARAMETER = 2;

    /// База данных, через которую выполняются запросы.
    private final SqliteDatabase database;
    /// Хранилище графика баз.
    private final SqliteBaseSchedule schedule;

    /// Создаёт удаление баз.
    ///
    /// @param database база данных SQLite
    /// @param schedule хранилище графика баз
    SqliteBaseDeletion(SqliteDatabase database, SqliteBaseSchedule schedule) {
        this.database = database;
        this.schedule = schedule;
    }

    /// Удаляет базу вместе с залами, графиком и бронями.
    ///
    /// @param id      идентификатор базы
    /// @param ownerId идентификатор владельца
    /// @return true, если база удалена
    boolean deleteByIdAndOwnerId(long id, long ownerId) {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                if (!baseExists(connection, id, ownerId)) {
                    connection.rollback();
                    return false;
                }
                for (String sql : List.of(DELETE_SUBSCRIPTIONS_SQL, DELETE_BOOKINGS_SQL, DELETE_ROOMS_SQL)) {
                    deleteRelatedRows(connection, sql, id);
                }
                schedule.delete(connection, id);
                deleteBaseRow(connection, id, ownerId);
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось удалить базу", exception);
        }
    }

    /// Проверяет, что база существует и принадлежит владельцу.
    ///
    /// @param connection соединение с базой данных
    /// @param id         идентификатор базы
    /// @param ownerId    идентификатор владельца
    /// @return true, если база найдена
    /// @throws SQLException если запрос не удался
    private boolean baseExists(Connection connection, long id, long ownerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM bases WHERE id=? AND owner_id=?")) {
            statement.setLong(FIRST_PARAMETER, id);
            statement.setLong(SECOND_PARAMETER, ownerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    /// Удаляет строки, связанные с базой, по указанному запросу.
    ///
    /// @param connection соединение с базой данных
    /// @param sql        запрос удаления с одним параметром
    /// @param baseId     идентификатор базы
    /// @throws SQLException если удаление не удалось
    private void deleteRelatedRows(Connection connection, String sql, long baseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(SINGLE_PARAMETER, baseId);
            statement.executeUpdate();
        }
    }

    /// Удаляет строку базы, проверяя владельца.
    ///
    /// @param connection соединение с базой данных
    /// @param id         идентификатор базы
    /// @param ownerId    идентификатор владельца
    /// @throws SQLException если база изменилась во время удаления
    private void deleteBaseRow(Connection connection, long id, long ownerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM bases WHERE id=? AND owner_id=?")) {
            statement.setLong(FIRST_PARAMETER, id);
            statement.setLong(SECOND_PARAMETER, ownerId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("База изменилась во время удаления: " + id);
            }
        }
    }
}

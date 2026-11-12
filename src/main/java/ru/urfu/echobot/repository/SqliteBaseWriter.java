package ru.urfu.echobot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import ru.urfu.echobot.model.Base;

/// Сохраняет и обновляет базы вместе с их графиком в SQLite.
final class SqliteBaseWriter {
    /// Позиция владельца в запросах баз.
    private static final int OWNER_ID_PARAMETER = 1;
    /// Позиция названия в запросах баз.
    private static final int BASE_NAME_PARAMETER = 2;
    /// Позиция адреса в запросах баз.
    private static final int ADDRESS_PARAMETER = 3;
    /// Позиция часового пояса в запросах баз.
    private static final int TIMEZONE_PARAMETER = 4;
    /// Позиция срока бронирования в запросах баз.
    private static final int LEAD_HOURS_PARAMETER = 5;
    /// Позиция оборудования в запросах баз.
    private static final int EQUIPMENT_PARAMETER = 6;
    /// Позиция идентификатора базы в запросах баз.
    private static final int BASE_ID_PARAMETER = 7;

    /// База данных, через которую выполняются запросы.
    private final SqliteDatabase database;
    /// Хранилище графика баз.
    private final SqliteBaseSchedule schedule;

    /// Создаёт хранилище записи баз.
    ///
    /// @param database база данных SQLite
    /// @param schedule хранилище графика баз
    SqliteBaseWriter(SqliteDatabase database, SqliteBaseSchedule schedule) {
        this.database = database;
        this.schedule = schedule;
    }

    /// Добавляет новую базу вместе с графиком.
    ///
    /// @param base сохраняемая база
    /// @return сохранённая база с присвоенным идентификатором
    Base save(Base base) {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                long id = insertBase(connection, base);
                schedule.save(connection, id, base.openingHours());
                connection.commit();
                return new Base(id, base.ownerId(), base.name(), base.address(), base.timezone(),
                        base.bookingLeadHours(), base.equipment(), base.openingHours());
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось сохранить базу", exception);
        }
    }

    /// Обновляет базу с тем же идентификатором и заменяет её график.
    ///
    /// @param base обновлённая база
    void update(Base base) {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                updateBaseRow(connection, base);
                schedule.delete(connection, base.id());
                schedule.save(connection, base.id(), base.openingHours());
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось обновить базу", exception);
        }
    }

    /// Обновляет строку базы.
    ///
    /// @param connection соединение с базой данных
    /// @param base       обновлённая база
    /// @throws SQLException если база не найдена
    private void updateBaseRow(Connection connection, Base base) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE bases SET owner_id=?,name=?,address=?,timezone=?,booking_lead_hours=?,equipment=? "
                        + "WHERE id=?")) {
            bindBase(statement, base);
            statement.setLong(BASE_ID_PARAMETER, base.id());
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("База не существует: " + base.id());
            }
        }
    }

    /// Подставляет поля базы в запрос.
    ///
    /// @param statement запрос с параметрами базы
    /// @param base      база с новыми значениями
    /// @throws SQLException если подстановка не удалась
    private void bindBase(PreparedStatement statement, Base base) throws SQLException {
        statement.setLong(OWNER_ID_PARAMETER, base.ownerId());
        statement.setString(BASE_NAME_PARAMETER, base.name());
        statement.setString(ADDRESS_PARAMETER, base.address());
        statement.setString(TIMEZONE_PARAMETER, base.timezone());
        statement.setInt(LEAD_HOURS_PARAMETER, base.bookingLeadHours());
        statement.setString(EQUIPMENT_PARAMETER, base.equipment());
    }

    /// Вставляет базу и возвращает её идентификатор.
    ///
    /// @param connection соединение с базой данных
    /// @param base       сохраняемая база
    /// @return идентификатор новой базы
    /// @throws SQLException если вставка не удалась
    private long insertBase(Connection connection, Base base) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bases(owner_id,name,address,timezone,booking_lead_hours,equipment) "
                        + "VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            bindBase(statement, base);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite не вернул идентификатор новой базы");
                }
                return keys.getLong(OWNER_ID_PARAMETER);
            }
        }
    }
}

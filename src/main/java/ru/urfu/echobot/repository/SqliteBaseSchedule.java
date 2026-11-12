package ru.urfu.echobot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.Map;
import ru.urfu.echobot.model.OpeningHours;

/// Читает, сохраняет и удаляет недельный график базы в SQLite.
final class SqliteBaseSchedule {
    /// Запрос удаления графика базы.
    private static final String DELETE_SQL = "DELETE FROM opening_hours WHERE base_id=?";
    /// Позиция базы в запросах графика.
    private static final int BASE_ID_PARAMETER = 1;
    /// Позиция дня недели в запросах графика.
    private static final int DAY_PARAMETER = 2;
    /// Позиция часа открытия в запросах графика.
    private static final int OPEN_HOUR_PARAMETER = 3;
    /// Позиция часа закрытия в запросах графика.
    private static final int CLOSE_HOUR_PARAMETER = 4;

    /// Удаляет график базы.
    ///
    /// @param connection соединение с базой данных
    /// @param baseId     идентификатор базы
    /// @throws SQLException если удаление не удалось
    void delete(Connection connection, long baseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setLong(BASE_ID_PARAMETER, baseId);
            statement.executeUpdate();
        }
    }

    /// Загружает график базы.
    ///
    /// @param connection соединение с базой данных
    /// @param baseId     идентификатор базы
    /// @return график по дням недели
    /// @throws SQLException если запрос не удался
    Map<DayOfWeek, OpeningHours> load(Connection connection, long baseId) throws SQLException {
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT day_of_week,open_hour,close_hour FROM opening_hours WHERE base_id=?")) {
            statement.setLong(BASE_ID_PARAMETER, baseId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    schedule.put(DayOfWeek.of(rows.getInt("day_of_week")),
                            new OpeningHours(rows.getInt("open_hour"), rows.getInt("close_hour")));
                }
            }
        }
        return schedule;
    }

    /// Сохраняет график базы.
    ///
    /// @param connection соединение с базой данных
    /// @param baseId     идентификатор базы
    /// @param schedule   график по дням недели
    /// @throws SQLException если вставка не удалась
    void save(Connection connection, long baseId, Map<DayOfWeek, OpeningHours> schedule) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO opening_hours(base_id,day_of_week,open_hour,close_hour) VALUES(?,?,?,?)")) {
            for (Map.Entry<DayOfWeek, OpeningHours> entry : schedule.entrySet()) {
                statement.setLong(BASE_ID_PARAMETER, baseId);
                statement.setInt(DAY_PARAMETER, entry.getKey().getValue());
                statement.setInt(OPEN_HOUR_PARAMETER, entry.getValue().openHour());
                statement.setInt(CLOSE_HOUR_PARAMETER, entry.getValue().closeHour());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
